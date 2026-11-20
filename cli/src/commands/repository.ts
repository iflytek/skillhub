import { resolveInstallTargets } from '../agents/resolver'
import { SkillHubClient } from '../clients/skillhub-client'
import { installRepositoryPlan } from '../services/repository-install-service'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { EXIT } from '../shared/constants'
import { CliError } from '../shared/errors'
import { computeStrictIsTTY } from '../shared/tty'
import { resolveEffectiveScope, defaultPromptScope, type InstallCommandOptions } from './install'
import { planRepositoryUpgrades, executeRepositoryUpgrades } from '../services/repository-upgrade-service'
import { renderUpgradePlan, renderUpgradeResult } from './upgrade'

export type RepositoryCommandOptions = Omit<InstallCommandOptions, 'force' | 'version' | 'namespace'> & { check?: boolean }

export async function repositoryCommand(action: string, repositoryId: string, options: RepositoryCommandOptions): Promise<string> {
  if (!['install', 'check', 'upgrade'].includes(action)) throw new CliError('repository action must be "install", "check" or "upgrade"', EXIT.usage)
  repositoryId = String(repositoryId)
  if (!/^[1-9]\d*$/.test(repositoryId) || BigInt(repositoryId) > 9223372036854775807n) {
    throw new CliError('repository ID must be a positive integer from the repository page', EXIT.usage)
  }
  if (action !== 'install' && options.scope !== undefined) throw new CliError('use --agent or --dir to filter installed targets; --scope is for installation', EXIT.usage)
  if (action === 'install' && options.check) throw new CliError('use repository check to inspect installed skills', EXIT.usage)
  const isTTY = computeStrictIsTTY({ stdinIsTTY: process.stdin.isTTY === true,
    stdoutIsTTY: process.stdout.isTTY === true, json: Boolean(options.json) })
  const scope = action === 'install' ? await resolveEffectiveScope(options, { isTTY, promptScope: defaultPromptScope }) : undefined
  const registry = resolveRegistry(options, process.env, await new ConfigStore().read())
  const token = resolveToken(options, process.env, await new CredentialsStore().getToken(registry))
  const plan = await new SkillHubClient(registry, token).repositoryInstallPlan(repositoryId)
  if (action !== 'install') {
    const tokenForRegistry = async () => token
    const upgrades = await planRepositoryUpgrades(plan, { registry, agents: options.agent, dir: options.dir, tokenForRegistry })
    const check = action === 'check' || Boolean(options.check)
    const result = check ? undefined : await executeRepositoryUpgrades(upgrades, { tokenForRegistry })
    const output = check ? renderUpgradePlan(upgrades, { check: true, executed: false }, Boolean(options.json))
      : renderUpgradeResult(upgrades, result!, Boolean(options.json), upgrades.blocked)
    if (upgrades.blocked > 0 || result && result.failed > 0) {
      process.stdout.write(`${output}\n`)
      throw new CliError('repository update contains protected or failed skills; inspect per-target results',
        result?.items.find(item => item.action === 'failed')?.exitCode ?? EXIT.validation)
    }
    return output
  }
  const targets = await resolveInstallTargets({ cwd: process.cwd(), scope, dir: options.dir,
    agents: options.agent ?? [], json: Boolean(options.json), interactive: isTTY })
  const result = await installRepositoryPlan(plan, { registry, token, targets })
  const note = 'Only accessible published skills are included. Pending or unavailable members are excluded; versions may come from different commits.'
  const output = options.json
    ? JSON.stringify({ ok: result.summary.failed === 0, ...result, note })
    : [`Repository ${plan.owner}/${plan.name}: ${result.summary.installed} installed, ${result.summary.skipped} skipped, ${result.summary.failed} failed`,
      ...result.items.flatMap(item => [
        `${item.action.padEnd(10)} ${item.coordinate}@${item.version}${item.reason ? ` (${item.reason})` : ''}${item.warnings?.length ? ` [${item.warnings.join('; ')}]` : ''}`,
        ...(item.recovery ? [`  Recovery: ${item.recovery.status}`, ...item.recovery.targets.map(path => `  Target: ${path}`),
          ...item.recovery.stagingPaths.map(path => `  Staging: ${path}`), ...item.recovery.retainedBackups.map(path => `  Retained backup: ${path}`), `  Next: ${item.recovery.next}`] : [])
      ]), note].join('\n')
  if (result.summary.failed > 0 || result.summary.installed === 0) {
    process.stdout.write(`${output}\n`)
    throw new CliError('repository installation did not complete successfully',
      result.items.find(item => item.action === 'failed')?.exitCode ?? EXIT.validation)
  }
  return output
}
