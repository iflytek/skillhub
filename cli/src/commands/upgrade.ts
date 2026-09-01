import { CredentialsStore } from '../stores/credentials-store'
import { resolveToken } from '../services/registry-service'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { executeSkillUpgradePlan, planSkillUpgrades, type UpgradePlan } from '../services/upgrade-service'

export interface UpgradeCommandOptions {
  namespace?: string
  agent?: string[]
  dir?: string
  registry?: string
  token?: string
  check?: boolean
  force?: boolean
  json?: boolean
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
  const output = renderUpgradePlan(plan, Boolean(options.check), Boolean(options.json))

  if (plan.blocked > 0) {
    process.stdout.write(`${output}\n`)
    throw new CliError('upgrade plan contains blocked skills', EXIT.validation, {
      blocked: plan.items.filter(item => item.action === 'blocked').map(item => ({
        coordinate: item.coordinate,
        reason: item.reason
      }))
    })
  }
  if (!options.check) {
    await executeSkillUpgradePlan(plan, { force: Boolean(options.force), tokenForRegistry })
  }
  return output
}

function renderUpgradePlan(plan: UpgradePlan, check: boolean, json: boolean): string {
  if (json) {
    return JSON.stringify({
      ok: plan.blocked === 0,
      check,
      summary: { upgrades: plan.upgrades, unchanged: plan.unchanged, blocked: plan.blocked },
      items: plan.items.map(item => ({
        coordinate: item.coordinate,
        registry: item.registry,
        currentVersion: item.currentVersion,
        remoteVersion: item.remoteVersion,
        action: item.action === 'upgrade' && !check ? 'upgraded' : item.action,
        reason: item.reason,
        changedFiles: item.changedFiles,
        targets: item.targets
      }))
    })
  }

  const heading = check ? 'Upgrade plan' : 'Upgrade result'
  return [
    `${heading}: ${plan.upgrades} upgrade, ${plan.unchanged} unchanged, ${plan.blocked} blocked`,
    ...plan.items.map(item => {
      const action = item.action === 'upgrade' && !check ? 'upgraded' : item.action
      const versions = item.remoteVersion ? ` ${item.currentVersion} -> ${item.remoteVersion}` : ''
      const reason = item.reason ? ` (${item.reason})` : ''
      return `${action.padEnd(10)} ${item.coordinate}${versions}${reason}`
    })
  ].join('\n')
}
