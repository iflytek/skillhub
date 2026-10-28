import { expect, test } from 'bun:test'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { commitRemovalInventory } from '../../../src/integrations/removal-inventory'
import type { RemovalBaseline } from '../../../src/integrations/native-contract'
import { InventoryStore } from '../../../src/stores/inventory-store'

test('removal commits exactly one target, journals before writing and preserves unrelated records', async () => {
  const home = await mkdtemp(join(tmpdir(), 'skillhub-removal-inventory-'))
  try {
    const store = new InventoryStore(home)
    const root = join(home, 'skills')
    const target = { installationState: 'enabled' as const, version: '1.0.0', fingerprint: 'fingerprint', agent: 'dsh', rootDir: root, installDir: join(root, 'writer'), installedAt: '2026-10-14' }
    const retained = { ...target, agent: 'other', rootDir: join(home, 'other'), installDir: join(home, 'other/writer') }
    const item = { registry: 'https://registry.example', namespace: 'demo', slug: 'writer', targets: [target, retained] }
    const other = { ...item, slug: 'reader', targets: [{ ...target, installDir: join(root, 'reader') }] }
    const initial = { items: [item, other] }
    const baseline: Extract<RemovalBaseline, { status: 'ready' }> = { status: 'ready', kind: 'remove-stale-record',
      inventoryItem: { ...item, targets: [target] }, target: { scope: 'user', root, path: target.installDir } }
    await store.writeAtomic(initial)
    let calls = 0
    await commitRemovalInventory(store, baseline, async (before, after) => {
      calls++
      expect(await store.read()).toEqual(before)
      expect(before).toEqual(initial)
      expect(after.items).toEqual([{ ...item, targets: [retained] }, other])
    })
    expect(calls).toBe(1)
    expect((await store.read()).items).toEqual([{ ...item, targets: [retained] }, other])
    await expect(commitRemovalInventory(store, baseline, async () => {})).rejects.toThrow('inventory changed')

    await store.writeAtomic(initial)
    await expect(commitRemovalInventory(store, baseline, async () => { throw new Error('journal failed') })).rejects.toThrow('journal failed')
    expect(await store.read()).toEqual(initial)

    const occupied = { items: [...initial.items, { ...item, registry: 'https://other.example', targets: [target] }] }
    await expect(store.writeAtomic(occupied)).rejects.toThrow('Ambiguous inventory target ownership')
    expect(await store.read()).toEqual(initial)

    const changed = { items: [{ ...item, targets: item.targets.map(t => ({ ...t, version: '2.0.0' })) }, other] }
    await store.writeAtomic(changed)
    await expect(commitRemovalInventory(store, baseline, async () => {})).rejects.toThrow('inventory changed')
    expect(await store.read()).toEqual(changed)

    const advancedSibling = { ...retained, version: '2.0.0', fingerprint: 'new-sibling' }
    await store.writeAtomic({ items: [{ ...item, targets: [target, advancedSibling] }, other] })
    await commitRemovalInventory(store, baseline, async () => {})
    expect((await store.read()).items).toEqual([{ ...item, targets: [advancedSibling] }, other])

    const single = { ...item, targets: [target] }
    await store.writeAtomic({ items: [single, other] })
    await commitRemovalInventory(store, { ...baseline, inventoryItem: single }, async () => {})
    expect((await store.read()).items).toEqual([other])
  } finally { await rm(home, { recursive: true, force: true }) }
})
