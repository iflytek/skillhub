import { resolve, relative } from 'node:path'
import { canonicalizeExistingPath } from '../platform/paths'
import { installedBy, targetInstalledBy } from '../stores/inventory-store'
import type { Inventory, InventoryItem } from '../stores/inventory-store'
import { sameInstalledSkillSource } from './installed-skill-metadata'

type Identity = Pick<InventoryItem, 'registry' | 'namespace' | 'slug'>

/** Compare affected records, not unrelated skills. Ordering and legacy direct ownership are semantic. */
export async function captureInstallInventory(
  inventory: Inventory, identity: Identity, canonicalDirs: ReadonlySet<string>
): Promise<string> {
  const records: string[] = []
  for (const item of inventory.items) {
    const targets = await Promise.all(item.targets.map(async target => {
      const root = resolve(target.rootDir)
      const canonicalRoot = await canonicalizeExistingPath(root)
      return { agent: target.agent, rootDir: canonicalRoot,
        installDir: resolve(canonicalRoot, relative(root, resolve(target.installDir))),
        installedAt: target.installedAt, installedBy: [...new Set(targetInstalledBy(item, target))].sort() }
    }))
    if (!sameInstalledSkillSource(item, identity) && !targets.some(target => canonicalDirs.has(target.installDir))) continue
    records.push(JSON.stringify({ registry: item.registry, namespace: item.namespace, slug: item.slug,
      version: item.version, fingerprint: item.fingerprint ?? null,
      installedBy: [...new Set(installedBy(item))].sort(),
      targets: targets.map(target => JSON.stringify(target)).sort() }))
  }
  return JSON.stringify(records.sort())
}
