import { homedir } from 'node:os'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import type { AgentCandidate } from './types'
import { allProfiles, profileMap } from './detector'

export interface ResolveInstallTargetOptions {
  cwd: string
  home?: string | undefined
  dir?: string | undefined
  agents?: string[] | undefined
  json: boolean
  interactive: boolean
  detected?: AgentCandidate[] | undefined
}

export async function resolveInstallTargets(options: ResolveInstallTargetOptions): Promise<AgentCandidate[]> {
  if (options.dir && options.agents?.length) {
    throw new CliError('--dir cannot be used with --agent', EXIT.usage)
  }
  if (options.dir) {
    return [{ agent: 'custom', rootDir: options.dir, scope: 'user', source: 'explicit' }]
  }
  if (options.agents?.length) {
    const resolved = await resolveExplicitAgents(options.agents, options.cwd, options.home ?? homedir())
    return dedupeByRoot(resolved)
  }
  const detected = options.detected ?? await detectAll(options.cwd, options.home ?? '')
  if (detected.length === 1) return detected
  if (detected.length > 1 && options.interactive && !options.json) {
    return selectTargetsInteractively(detected)
  }
  if (detected.length > 1 && (!options.interactive || options.json)) {
    throw new CliError('multiple install targets detected', EXIT.usage, {
      next: 'pass --agent or --dir',
      candidates: detected
    })
  }
  return [{ agent: 'generic', rootDir: `${options.cwd}/.agents/skills`, scope: 'project', source: 'fallback' }]
}

async function detectAll(cwd: string, home: string): Promise<AgentCandidate[]> {
  const results: AgentCandidate[] = []
  for (const profile of allProfiles) {
    const candidates = await profile.detectInstalled(cwd, home)
    results.push(...candidates)
  }
  return dedupeByRoot(results)
}

async function resolveExplicitAgents(agents: string[], cwd: string, home?: string): Promise<AgentCandidate[]> {
  const results: AgentCandidate[] = []
  for (const agentId of agents) {
    const profile = profileMap.get(agentId)
    if (!profile) {
      throw new CliError(`unknown agent: ${agentId}`, EXIT.usage, {
        next: 'use a supported agent profile or pass --dir'
      })
    }
    const userRoots = home ? profile.userRoots(home) : []
    const roots = userRoots.length > 0 ? userRoots : profile.projectRoots(cwd)
    if (roots.length > 0) {
      results.push(...roots.map(root => {
        const scope: AgentCandidate['scope'] = root.startsWith(cwd) ? 'project' : 'user'
        return {
        agent: agentId,
        rootDir: root,
        scope,
        source: 'explicit' as const
      }
      }))
    }
  }
  return results
}

function dedupeByRoot(candidates: AgentCandidate[]): AgentCandidate[] {
  const seen = new Set<string>()
  return candidates.filter(c => {
    if (seen.has(c.rootDir)) return false
    seen.add(c.rootDir)
    return true
  })
}

async function selectTargetsInteractively(candidates: AgentCandidate[]): Promise<AgentCandidate[]> {
  const prompts = await import('prompts')
  const { selected } = await prompts.default({
    type: 'multiselect',
    name: 'selected',
    message: 'Select install targets',
    choices: candidates.map(c => ({
      title: `${c.agent} (${c.rootDir})`,
      value: c
    }))
  })
  if (!selected || selected.length === 0) {
    throw new CliError('installation cancelled', EXIT.usage)
  }
  return selected
}
