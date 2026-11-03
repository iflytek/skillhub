import { isDeepStrictEqual } from 'node:util'
import { captureInstallInventory } from '../services/inventory-cas'
import { sameInstalledSkillSource } from '../services/installed-skill-metadata'
import { inventoryWithoutState, targetInstalledBy } from '../stores/inventory-store'
import type { Inventory, InventoryStore } from '../stores/inventory-store'
import type { RemovalBaseline } from './native-contract'

/** Caller holds the target lock and has verified files. Persist the commit intent before writing. */
export async function commitRemovalInventory(store: InventoryStore,
  baseline: Extract<RemovalBaseline, { status: 'ready' }>,
  beforeCommit: (before: Inventory, after: Inventory) => Promise<void>): Promise<void> {
  const expected = structuredClone(baseline.inventoryItem)
  const path = baseline.target.path
  const target = expected.targets.filter(entry => entry.installDir === path)
  if (target.length !== 1 || target[0]!.agent !== 'dsh'
    || target[0]!.rootDir !== baseline.target.root
    || !isDeepStrictEqual(targetInstalledBy(expected, target[0]!), ['direct'])) {
    throw new Error('Removal target is not exclusively owned')
  }
  const paths = new Set([path])
  const expectedState = await captureInstallInventory({ items: [expected] }, expected, paths)
  await store.mutateAtomic(inventory => {
    const matches = inventory.items.filter(item => sameInstalledSkillSource(item, expected))
    if (!isDeepStrictEqual(inventoryWithoutState({ items: matches.map(item => ({ ...item, targets: item.targets.filter(target => target.installDir === path) })) }), inventoryWithoutState({ items: [expected] }))) throw new Error('Removal inventory changed')
    const item = matches[0]!
    item.targets = item.targets.filter(entry => entry.installDir !== path)
    if (!item.targets.length) inventory.items = inventory.items.filter(entry => entry !== item)
    // Retained target records and legacy ownership representation remain unchanged.
  }, async (before, after) => {
    if (await captureInstallInventory(before, expected, paths) !== expectedState) {
      throw new Error('Removal target ownership changed')
    }
    await beforeCommit(before, after)
  })
}
