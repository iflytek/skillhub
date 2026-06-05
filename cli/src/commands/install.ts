import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import { installSkill } from '../services/install-service'
import { resolveInstallTargets } from '../agents/resolver'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { parseSkillName } from '../shared/skill-name-parser'

export interface InstallCommandOptions {
  namespace?: string | undefined
  version?: string | undefined
  agent?: string[] | undefined
  dir?: string | undefined
  scope?: string | undefined
  force?: boolean | undefined
  registry?: string | undefined
  token?: string | undefined
  json?: boolean | undefined
}

export interface InstallCommandDeps {
  promptScope?: () => Promise<'user' | 'project'>
  resolveInstallTargets?: typeof resolveInstallTargets
  installSkill?: typeof installSkill
  isTTY?: () => boolean
}

export function computeStrictIsTTY(env: {
  stdinIsTTY: boolean
  stdoutIsTTY: boolean
  json: boolean
}): boolean {
  return env.stdinIsTTY && env.stdoutIsTTY && !env.json
}

export async function resolveEffectiveScope(
  options: InstallCommandOptions,
  env: { isTTY: boolean; promptScope: () => Promise<'user' | 'project'> }
): Promise<'user' | 'project' | undefined> {
  if (options.scope !== undefined && options.scope !== 'user' && options.scope !== 'project') {
    throw new CliError('--scope must be "user" or "project"', EXIT.usage)
  }
  const scope = options.scope as 'user' | 'project' | undefined
  const agentList = options.agent ?? []

  if (options.dir && scope !== undefined) {
    throw new CliError('--dir cannot be used with --scope', EXIT.usage)
  }
  if (options.dir && agentList.length > 0) {
    throw new CliError('--dir cannot be used with --agent', EXIT.usage)
  }

  if (scope !== undefined) return scope
  if (options.dir || agentList.length > 0) return undefined
  if (env.isTTY) return await env.promptScope()
  return undefined
}

async function defaultPromptScope(): Promise<'user' | 'project'> {
  const prompts = await import('prompts')
  const { scope } = await prompts.default({
    type: 'select',
    name: 'scope',
    message: 'Install for user or project?',
    choices: [
      { title: 'User (install to user-level agent directory)', value: 'user' },
      { title: 'Project (install to project-level agent directory)', value: 'project' }
    ]
  })
  if (!scope) {
    throw new CliError('installation cancelled', EXIT.usage)
  }
  return scope
}

export async function installCommand(
  skillNameArg: string,
  options: InstallCommandOptions,
  deps: InstallCommandDeps = {}
): Promise<string> {
  const isTTYFn = deps.isTTY ?? (() => computeStrictIsTTY({
    stdinIsTTY: process.stdin.isTTY === true,
    stdoutIsTTY: process.stdout.isTTY === true,
    json: Boolean(options.json)
  }))
  const isTTY = isTTYFn()

  const promptScope = deps.promptScope ?? defaultPromptScope
  const effectiveScope = await resolveEffectiveScope(options, { isTTY, promptScope })

  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  const token = resolveToken(options, process.env, await credentialsStore.getToken(registry))

  const parsed = parseSkillName(skillNameArg)
  const namespace = options.namespace ?? parsed.namespace
  const slug = parsed.slug

  const resolveTargets = deps.resolveInstallTargets ?? resolveInstallTargets
  const targets = await resolveTargets({
    cwd: process.cwd(),
    scope: effectiveScope,
    dir: options.dir,
    agents: options.agent ?? [],
    json: Boolean(options.json),
    interactive: isTTY
  })

  const installFn = deps.installSkill ?? installSkill
  const result = await installFn({
    registry, token, namespace, slug,
    version: options.version,
    targets,
    force: Boolean(options.force)
  })

  if (options.json) {
    return JSON.stringify({ ok: true, namespace, slug, installed: result.installed })
  }
  return result.installed.map(i => `Installed ${namespace}/${slug} -> ${i.dir} (${i.agent})`).join('\n')
}
