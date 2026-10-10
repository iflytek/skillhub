import { CredentialsStore } from '../stores/credentials-store'
import { resolveToken } from '../services/registry-service'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import {
  executeSkillUpgradePlan,
  planSkillUpgrades,
  type UpgradeExecutionResult,
  type UpgradePlan
} from '../services/upgrade-service'

export interface UpgradeCommandOptions {
  namespace?: string | undefined
  agent?: string[] | undefined
  dir?: string | undefined
  registry?: string | undefined
  token?: string | undefined
  check?: boolean | undefined
  force?: boolean | undefined
  json?: boolean | undefined
}

export async function upgradeCommand(coordinates: string[], options: UpgradeCommandOptions): Promise<string> {
  const credentials = new CredentialsStore()
  const tokenForRegistry = async (registry: string): Promise<string | undefined> =>
    resolveToken(options, process.env, await credentials.getToken(registry))

  const plan = await planSkillUpgrades({
    coordinates,
    namespace: options.namespace,
    registry: options.registry,
    agents: options.agent,
    dir: options.dir,
    force: Boolean(options.force),
    tokenForRegistry
  })
  if (plan.blocked > 0) {
    const output = renderUpgradePlan(plan, {
      check: Boolean(options.check),
      executed: false
    }, Boolean(options.json))
    process.stdout.write(`${output}\n`)
    throw new CliError('upgrade plan contains blocked skills', EXIT.validation, {
      blocked: plan.items.filter(item => item.action === 'blocked').map(item => ({
        coordinate: item.coordinate,
        reason: item.reason
      }))
    })
  }
  if (options.check) return renderUpgradePlan(plan, { check: true, executed: false }, Boolean(options.json))

  const result = await executeSkillUpgradePlan(plan, { tokenForRegistry })
  const output = renderUpgradeResult(plan, result, Boolean(options.json))
  if (result.failed > 0) {
    process.stdout.write(`${output}\n`)
    const firstFailure = result.items.find(item => item.action === 'failed')
    throw new CliError('one or more skills failed to upgrade', firstFailure?.exitCode ?? EXIT.generic, {
      failed: result.items.filter(item => item.action === 'failed')
    })
  }
  return output
}

function renderUpgradePlan(
  plan: UpgradePlan,
  state: { check: boolean; executed: boolean },
  json: boolean
): string {
  if (json) {
    return JSON.stringify({
      ok: plan.blocked === 0,
      check: state.check,
      summary: { upgrades: plan.upgrades, unchanged: plan.unchanged, blocked: plan.blocked },
      items: plan.items.map(item => ({
        coordinate: item.coordinate,
        registry: item.registry,
        currentVersion: item.currentVersion,
        remoteVersion: item.remoteVersion,
        action: item.action === 'upgrade' && state.executed ? 'upgraded' : item.action,
        reason: item.reason,
        changedFiles: item.changedFiles,
        targets: item.targets
      }))
    })
  }

  const heading = state.executed ? 'Upgrade result' : 'Upgrade plan'
  return [
    `${heading}: ${plan.upgrades} upgrade, ${plan.unchanged} unchanged, ${plan.blocked} blocked`,
    ...plan.items.map(item => {
      const action = item.action === 'upgrade' && state.executed ? 'upgraded' : item.action
      const versions = item.remoteVersion ? ` ${item.currentVersion} -> ${item.remoteVersion}` : ''
      const reason = item.reason ? ` (${item.reason})` : ''
      return `${action.padEnd(10)} ${item.coordinate}${versions}${reason}`
    })
  ].join('\n')
}

export function renderUpgradeResult(plan: UpgradePlan, result: UpgradeExecutionResult, json: boolean): string {
  const executionByCoordinate = new Map(result.items.map(item => [item.coordinate, item]))
  const items = plan.items.map(item => ({
    coordinate: item.coordinate,
    registry: item.registry,
    currentVersion: item.currentVersion,
    remoteVersion: item.remoteVersion,
    action: executionByCoordinate.get(item.coordinate)?.action ?? item.action,
    reason: executionByCoordinate.get(item.coordinate)?.reason ?? item.reason,
    warnings: executionByCoordinate.get(item.coordinate)?.warnings,
    changedFiles: item.changedFiles,
    targets: item.targets
  }))

  if (json) {
    return JSON.stringify({
      ok: result.failed === 0,
      check: false,
      summary: {
        upgraded: result.upgraded,
        unchanged: result.unchanged,
        failed: result.failed,
        notAttempted: result.notAttempted
      },
      items
    })
  }

  return [
    `Upgrade result: ${result.upgraded} upgraded, ${result.unchanged} unchanged, ${result.failed} failed, ${result.notAttempted} not attempted`,
    ...items.map(item => {
      const versions = item.remoteVersion ? ` ${item.currentVersion} -> ${item.remoteVersion}` : ''
      const reason = item.reason ? ` (${item.reason})` : ''
      const warnings = item.warnings?.length ? ` [warning: ${item.warnings.join('; ')}]` : ''
      return `${item.action.padEnd(13)} ${item.coordinate}${versions}${reason}${warnings}`
    })
  ].join('\n')
}
