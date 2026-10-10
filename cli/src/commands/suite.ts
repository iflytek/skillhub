import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import { resolveSkillName } from '../shared/skill-name-parser'
import { resolveInstallTargets } from '../agents/resolver'
import { resolveEffectiveScope } from './install'
import { computeStrictIsTTY } from '../shared/tty'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { checkSuite, installSuite, planSuiteUpgrade, removeSuite, upgradeSuite } from '../services/suite-service'

export interface SuiteCommandOptions {
  version?: string | undefined
  scope?: string | undefined
  agent?: string[] | undefined
  dir?: string | undefined
  force?: boolean | undefined
  check?: boolean | undefined
  registry?: string | undefined
  token?: string | undefined
  json?: boolean | undefined
}

export async function suiteCommand(
  action: string,
  coordinate: string,
  options: SuiteCommandOptions
): Promise<string> {
  if (!['install', 'check', 'upgrade', 'remove'].includes(action)) {
    throw new CliError(`unknown suite action: ${action}`, EXIT.usage, {
      next: 'use install, check, upgrade, or remove'
    })
  }
  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  const token = resolveToken(options, process.env, await credentialsStore.getToken(registry))
  const { namespace, slug } = resolveSkillName(coordinate)
  const common = { registry, token, namespace, slug }

  if (action === 'install') {
    const isTTY = computeStrictIsTTY({
      stdinIsTTY: process.stdin.isTTY === true,
      stdoutIsTTY: process.stdout.isTTY === true,
      json: Boolean(options.json)
    })
    const scope = await resolveEffectiveScope(options, {
      isTTY,
      promptScope: async () => {
        const prompts = await import('prompts')
        const { selected } = await prompts.default({
          type: 'select',
          name: 'selected',
          message: 'Install Suite for user or project?',
          choices: [
            { title: 'User', value: 'user' },
            { title: 'Project', value: 'project' }
          ]
        })
        if (!selected) throw new CliError('installation cancelled', EXIT.usage)
        return selected as 'user' | 'project'
      }
    })
    const targets = await resolveInstallTargets({
      cwd: process.cwd(),
      scope,
      dir: options.dir,
      agents: options.agent ?? [],
      json: Boolean(options.json),
      interactive: isTTY
    })
    const result = await installSuite({
      ...common,
      version: options.version,
      targets,
      force: Boolean(options.force)
    })
    if (options.json) return JSON.stringify({ ok: true, suite: result.plan, installed: result.installed, reused: result.reused })
    return [
      `Installed Suite @${namespace}/${slug}@${result.plan.version}`,
      ...result.installed.map(item => `Installed @${item.namespace}/${item.slug} -> ${item.dir} (${item.agent})`),
      ...result.reused.map(item => `Reused @${item.namespace}/${item.slug} -> ${item.dir} (${item.agent})`)
    ].join('\n')
  }

  if (hasInstallOnlyOptions(options) || (options.force === true && action !== 'upgrade')) {
    throw new CliError(
      '--scope, --agent, --dir, and --version are only valid with suite install; --force is valid with install or upgrade',
      EXIT.usage)
  }

  if (action === 'check') {
    const result = await checkSuite(common)
    if (options.json) return JSON.stringify({ ok: result.current, ...result })
    return [
      `Suite @${namespace}/${slug}@${result.suite.version}: ${result.current ? 'current' : 'changes detected'}`,
      ...(result.remoteVersion && result.remoteVersion !== result.suite.version
        ? [`Remote version: ${result.remoteVersion}`]
        : []),
      ...(!result.installedVersionAvailable
        ? [`Installed version unavailable: ${result.blockingReasons.join(', ') || 'unknown reason'}`]
        : []),
      ...result.members.map(member => `${member.status.padEnd(8)} @${member.namespace}/${member.slug}@${member.version} ${member.dir}`)
    ].join('\n')
  }

  if (action === 'remove') {
    const result = await removeSuite(common)
    if (options.json) return JSON.stringify({ ok: true, ...result })
    return [
      `Removed Suite @${namespace}/${slug}`,
      ...result.removed.map(dir => `Removed member: ${dir}`),
      ...result.preserved.map(item => `Preserved member (${item.reason}): ${item.dir}`)
    ].join('\n')
  }

  if (options.check) {
    const plan = await planSuiteUpgrade(common)
    return renderUpgradePlan(plan, Boolean(options.json))
  }
  const { upgrade, result } = await upgradeSuite({ ...common, force: Boolean(options.force) })
  if (options.json) return JSON.stringify({ ok: true, upgrade, result })
  if (!result) return `Suite @${namespace}/${slug}@${upgrade.current.version} is current`
  return [
    `Upgraded Suite @${namespace}/${slug}: ${upgrade.current.version} -> ${upgrade.remote.version}`,
    ...upgrade.changes.map(change => renderChange(change))
  ].join('\n')
}

function hasInstallOnlyOptions(options: SuiteCommandOptions): boolean {
  return options.scope !== undefined || options.agent !== undefined || options.dir !== undefined ||
    options.version !== undefined
}

function renderUpgradePlan(plan: Awaited<ReturnType<typeof planSuiteUpgrade>>, json: boolean): string {
  if (json) return JSON.stringify({
    ok: true,
    currentVersion: plan.current.version,
    remoteVersion: plan.remote.version,
    changes: plan.changes
  })
  return [
    `Suite upgrade plan: ${plan.current.version} -> ${plan.remote.version}`,
    ...(plan.changes.length === 0 ? ['No member changes'] : plan.changes.map(renderChange))
  ].join('\n')
}

function renderChange(change: Awaited<ReturnType<typeof planSuiteUpgrade>>['changes'][number]): string {
  const versions = change.action === 'add'
    ? ` -> ${change.toVersion}`
    : change.action === 'remove'
      ? ` ${change.fromVersion} -> removed`
      : ` ${change.fromVersion} -> ${change.toVersion}`
  return `${change.action.padEnd(7)} ${change.coordinate}${versions}`
}
