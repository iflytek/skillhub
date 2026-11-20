import type { RepositoryInstallPlan } from '../clients/skillhub-client'
import { InventoryStore } from '../stores/inventory-store'
import { EXIT } from '../shared/constants'
import { CliError } from '../shared/errors'
import { readInstalledSkillMetadata, sameInstalledSkillSource } from './installed-skill-metadata'
import { MAX_UPGRADE_SELECTION, matchesTargetFilters, planSkillUpgrades, executeSkillUpgradePlan,
  type UpgradeSelectionOptions, type UpgradePlan, type UpgradeExecutionResult } from './upgrade-service'

type RepositoryUpgradeOptions = Omit<UpgradeSelectionOptions, 'coordinates' | 'namespace' | 'force' | 'resolveVersion'> & { registry: string }

export async function planRepositoryUpgrades(remote: RepositoryInstallPlan, options: RepositoryUpgradeOptions): Promise<UpgradePlan> {
  const inventory = await new InventoryStore(options.home).read()
  const members = new Map(remote.members.map(member => [member.slug, member]))
  const items: UpgradePlan['items'] = []
  const coordinates: string[] = []
  for (const item of inventory.items) {
    if (item.namespace !== 'github' || item.registry.replace(/\/+$/, '') !== options.registry.replace(/\/+$/, '')) continue
    const targets = item.targets.filter(target => matchesTargetFilters(target, options.agents, options.dir))
    if (!targets.length) continue
    if (members.has(item.slug)) {
      coordinates.push(`@github/${item.slug}`)
      continue
    }
    // Local provenance is only a discovery hint for unavailable members, never upgrade authority.
    for (const target of targets) {
      const metadata = await readInstalledSkillMetadata(target.installDir)
      if (metadata.status !== 'valid' || !sameInstalledSkillSource(metadata.metadata, item)
        || metadata.metadata.sourceRepositoryId !== remote.repositoryId) continue
      items.push({ coordinate: `@github/${item.slug}`, registry: item.registry, currentVersion: target.version,
        action: 'blocked', reason: 'no longer in the accessible published repository plan; local files are preserved',
        changedFiles: [], targets: [{ agent: target.agent, dir: target.installDir }],
        inventoryItem: { ...item, targets: [target] }, selectedTargets: [target], expectedTargetFiles: {}, allowTargetDrift: false })
    }
  }
  if (!coordinates.length && !items.length) throw new CliError('no installed skills match this repository and target selection; no files changed', EXIT.usage)
  for (let offset = 0; offset < coordinates.length; offset += MAX_UPGRADE_SELECTION) {
    const plan = await planSkillUpgrades({ ...options, coordinates: coordinates.slice(offset, offset + MAX_UPGRADE_SELECTION),
      force: false, resolveVersion: async item => {
        const member = members.get(item.slug)
        if (!member) throw new CliError('repository member is not in the pinned plan', EXIT.validation)
        return member
      } })
    items.push(...plan.items)
  }
  return { items, blocked: items.filter(item => item.action === 'blocked').length,
    upgrades: items.filter(item => item.action === 'upgrade').length, unchanged: items.filter(item => item.action === 'unchanged').length }
}

export async function executeRepositoryUpgrades(plan: UpgradePlan, options: Pick<RepositoryUpgradeOptions, 'home' | 'tokenForRegistry'>): Promise<UpgradeExecutionResult> {
  const safeItems = plan.items.filter(item => item.action !== 'blocked')
  const executed = await executeSkillUpgradePlan({ ...plan, items: safeItems, blocked: 0 }, options)
  let position = 0
  return { ...executed, notAttempted: executed.notAttempted + plan.blocked,
    items: plan.items.map(item => item.action === 'blocked'
      ? { coordinate: item.coordinate, action: 'not-attempted' as const, reason: item.reason ?? 'protected local installation' }
      : executed.items[position++]!) }
}
