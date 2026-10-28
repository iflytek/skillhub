import { resolve, relative } from 'node:path'
import { canonicalizeExistingPath, pathsOverlap } from '../platform/paths'
import { targetInstalledBy } from '../stores/inventory-store'
import type { Inventory, InventoryItem } from '../stores/inventory-store'

type Identity = Pick<InventoryItem, 'registry' | 'namespace' | 'slug'>

/** Compare affected records, not unrelated skills. Ordering and legacy direct ownership are semantic. */
export async function captureInstallInventory(
  inventory: Inventory, identity: Identity, canonicalDirs: ReadonlySet<string>
): Promise<string> {
  void identity
  const records: string[] = []
  for (const item of inventory.items) {
    const targets = await Promise.all(item.targets.map(async target => {
      const root = resolve(target.rootDir)
      const canonicalRoot = await canonicalizeExistingPath(root)
      return { agent: target.agent, rootDir: canonicalRoot,
        installDir: resolve(canonicalRoot, relative(root, resolve(target.installDir))),
        installationState: target.installationState ?? 'enabled', version: target.version, fingerprint: target.fingerprint ?? null, installedAt: target.installedAt, installedBy: [...new Set(targetInstalledBy(item, target))].sort() }
    }))
    const selected = targets.filter(target => [...canonicalDirs].some(path => pathsOverlap(path, target.installDir)))
    if (!selected.length) continue
    records.push(JSON.stringify({ registry: item.registry, namespace: item.namespace, slug: item.slug,
      targets: selected.map(target => JSON.stringify(target)).sort() }))
  }
  const suites: string[] = []
  for (const suite of inventory.suites ?? []) {
    const members: string[] = []
    for (const member of suite.members) {
      const selected = []
      for (const path of member.installDirs) {
        const canonical = await canonicalizeExistingPath(path)
        if ([...canonicalDirs].some(path => pathsOverlap(path, canonical))) selected.push(canonical)
      }
      if (selected.length) members.push(JSON.stringify({ ...member, installDirs: selected.sort() }))
    }
    if (members.length) suites.push(JSON.stringify({ registry: suite.registry, namespace: suite.namespace,
      slug: suite.slug, version: suite.version, fingerprint: suite.fingerprint, members: members.sort() }))
  }
  return JSON.stringify({ records: records.sort(), suites: suites.sort() })
}
