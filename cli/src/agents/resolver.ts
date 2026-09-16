import { homedir } from 'node:os'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { canonicalizeExistingPath, directoryExists } from '../platform/paths'
import type { AgentCandidate } from './types'
import { allProfiles, profileMap } from './detector'

export interface ResolveInstallTargetOptions {
  cwd: string
  home?: string | undefined
  dir?: string | undefined
  agents?: string[] | undefined
  scope?: 'user' | 'project' | undefined
  json: boolean
  interactive: boolean
  detected?: AgentCandidate[] | undefined
}

export async function resolveInstallTargets(options: ResolveInstallTargetOptions): Promise<AgentCandidate[]> {
  const agentList = options.agents ?? []

  if (options.dir && agentList.length > 0) {
    throw new CliError('--dir cannot be used with --agent', EXIT.usage)
  }
  if (options.dir && options.scope !== undefined) {
    throw new CliError('--dir cannot be used with --scope', EXIT.usage)
  }
  if (options.dir) {
    return [{ agent: 'custom', rootDir: options.dir, scope: 'user', source: 'explicit' }]
  }

  if (options.scope !== undefined) {
    return resolveScopedTargets(options, agentList)
  }

  if (agentList.length > 0) {
    const resolved = await resolveExplicitAgents(agentList, options.cwd, options.home ?? homedir())
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

async function resolveScopedTargets(
  options: ResolveInstallTargetOptions,
  agentList: string[]
): Promise<AgentCandidate[]> {
  const scope = options.scope!
  const scopedHome = options.home ?? homedir()

  let candidates: AgentCandidate[]
  if (agentList.length > 0) {
    candidates = await resolveExplicitAgents(agentList, options.cwd, scopedHome, scope)
  } else if (options.detected !== undefined) {
    candidates = options.detected.filter(c => c.scope === scope)
  } else {
    candidates = await generateScopedCandidates(scope, options.cwd, scopedHome)
  }
  candidates = await dedupeByRoot(candidates)

  if (scope === 'user' && agentList.length === 0 && options.interactive && !options.json) {
    candidates = await dedupeByRoot([
      ...candidates,
      {
        agent: 'generic',
        rootDir: `${scopedHome}/.agents/skills`,
        scope: 'user',
        source: 'fallback'
      }
    ])
  }

  if (candidates.length === 0) {
    const fallbackRoot = scope === 'user'
      ? `${scopedHome}/.agents/skills`
      : `${options.cwd}/.agents/skills`
    return [{ agent: 'generic', rootDir: fallbackRoot, scope, source: 'fallback' }]
  }
  if (candidates.length === 1) return candidates
  if (options.interactive && !options.json) {
    return selectTargetsInteractively(candidates)
  }
  throw new CliError('multiple install targets detected', EXIT.usage, {
    next: 'pass --agent or --dir',
    candidates
  })
}

async function generateScopedCandidates(
  scope: 'user' | 'project',
  cwd: string,
  home: string
): Promise<AgentCandidate[]> {
  const results: AgentCandidate[] = []
  for (const profile of allProfiles) {
    const roots = scope === 'user' ? profile.userRoots(home) : profile.projectRoots(cwd)
    for (const root of roots) {
      if (await directoryExists(root)) {
        results.push({ agent: profile.id, rootDir: root, scope, source: 'detected' })
      }
    }
  }
  return results
}

async function detectAll(cwd: string, home: string): Promise<AgentCandidate[]> {
  const results: AgentCandidate[] = []
  for (const profile of allProfiles) {
    const candidates = await profile.detectInstalled(cwd, home)
    results.push(...candidates)
  }
  return dedupeByRoot(results)
}

async function resolveExplicitAgents(
  agents: string[],
  cwd: string,
  home: string,
  scope?: 'user' | 'project'
): Promise<AgentCandidate[]> {
  const results: AgentCandidate[] = []
  for (const agentId of agents) {
    const profile = profileMap.get(agentId)
    if (!profile) {
      throw new CliError(`unknown agent: ${agentId}`, EXIT.usage, {
        next: 'use a supported agent profile or pass --dir'
      })
    }
    let roots: string[]
    if (scope === 'user') {
      roots = profile.userRoots(home)
    } else if (scope === 'project') {
      roots = profile.projectRoots(cwd)
    } else {
      const userRoots = home ? profile.userRoots(home) : []
      roots = userRoots.length > 0 ? userRoots : profile.projectRoots(cwd)
    }
    if (roots.length === 0) {
      throw new CliError(`agent ${agentId} does not support ${scope} scope`, EXIT.usage, {
        next: 'choose a supported scope or omit --scope'
      })
    }
    const userRootSet = new Set(home ? profile.userRoots(home) : [])
    for (const root of roots) {
      const candidateScope: AgentCandidate['scope'] = scope !== undefined
        ? scope
        : (userRootSet.has(root) ? 'user' : 'project')
      results.push({
        agent: agentId,
        rootDir: root,
        scope: candidateScope,
        source: 'explicit'
      })
    }
  }
  return results
}

async function dedupeByRoot(candidates: AgentCandidate[]): Promise<AgentCandidate[]> {
  const seen = new Set<string>()
  const deduped: AgentCandidate[] = []

  for (const candidate of candidates) {
    const canonicalRootDir = await canonicalizeExistingPath(candidate.rootDir)
    if (seen.has(canonicalRootDir)) continue
    seen.add(canonicalRootDir)
    deduped.push(candidate)
  }

  return deduped
}

async function selectTargetsInteractively(candidates: AgentCandidate[]): Promise<AgentCandidate[]> {
  const prompts = await import('prompts')
  let highlightedIndex = 0
  const { selected } = await prompts.default({
    type: 'multiselect',
    name: 'selected',
    message: 'Select install targets',
    choices: candidates.map(c => ({
      title: `${profileMap.get(c.agent)?.displayName ?? c.agent} (${c.rootDir})`,
      value: c
    })),
    onRender: function (this: { cursor?: number }) {
      highlightedIndex = this.cursor ?? highlightedIndex
    },
    format: (selectedTargets: AgentCandidate[]) => (
      selectedTargets.length > 0 ? selectedTargets : [candidates[highlightedIndex] ?? candidates[0]!]
    )
  })
  if (!selected || selected.length === 0) {
    throw new CliError('installation cancelled', EXIT.usage)
  }
  return selected
}
