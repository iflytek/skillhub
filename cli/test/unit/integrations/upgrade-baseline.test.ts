import { expect, test } from 'bun:test'
import { realpath, mkdtemp, mkdir, rm, writeFile, symlink } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { inspectUpgradeBaseline } from '../../../src/integrations/upgrade-baseline'
import { snapshotSkillDirectory } from '../../../src/services/skill-fingerprint'
import type { Inventory, InventoryItem } from '../../../src/stores/inventory-store'

test('upgrade baseline includes both approved targets and blocks selected drift, sharing and aliases while preserving foreign targets', async () => {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-upgrade-baseline-')))
  try {
    const roots = [{ scope: 'user' as const, root: join(root, 'user') }, { scope: 'project' as const, root: join(root, 'project') }]
    const item: InventoryItem = { registry: 'https://registry.example', namespace: 'demo', slug: 'writer',
      targets: roots.map(entry => ({ version: '1.0.0', agent: 'dsh', rootDir: entry.root, installDir: join(entry.root, 'writer'), installedAt: '2026-10-14T00:00:00Z' })) }
    for (const target of item.targets) {
      await mkdir(join(target.installDir, '.skillhub'), { recursive: true })
      await writeFile(join(target.installDir, 'SKILL.md'), 'original')
      await writeFile(join(target.installDir, '.skillhub/examples.json'), '{invalid but fingerprinted}')
      const snapshot = await snapshotSkillDirectory(target.installDir)
      target.fingerprint = snapshot.fingerprint
      await writeFile(join(target.installDir, '.skillhub/metadata.json'), JSON.stringify({ ...item, version: target.version, ...snapshot,
        agent: 'dsh', installedAt: target.installedAt }))
    }
    let inventory: Inventory = { items: [structuredClone(item)] }
    const store = { read: async () => structuredClone(inventory) }
    const check = () => inspectUpgradeBaseline(store, { ...item, version: item.targets[0]!.version }, roots)
    const ready = await check()
    expect(ready.status).toBe('ready')
    if (ready.status !== 'ready') throw new Error('expected ready')
    expect(ready.targets.map(target => target.scope)).toEqual(['user', 'project'])
    expect(ready.inventoryItem.targets).toHaveLength(2)

    await writeFile(join(item.targets[0]!.installDir, '.skillhub/examples.json'), '{edited}')
    expect(await check()).toEqual({ status: 'blocked', reason: 'local-changes' })
    await rm(join(item.targets[0]!.installDir, '.skillhub/examples.json'))
    expect(await check()).toEqual({ status: 'blocked', reason: 'local-changes' })
    await writeFile(join(item.targets[0]!.installDir, '.skillhub/examples.json'), '{invalid but fingerprinted}')
    expect((await check()).status).toBe('ready')

    inventory.items[0]!.targets[1]!.agent = 'other-agent'
    expect(await check()).toEqual({ status: 'blocked', reason: 'target-outside-context' })
    inventory = { items: [structuredClone(item)] }
    inventory.items[0]!.targets.push({ ...item.targets[0]!, rootDir: join(root, 'foreign'), installDir: join(root, 'foreign/writer') })
    expect((await check()).status).toBe('ready')
    inventory = { items: [structuredClone(item)] }
    inventory.items[0]!.targets[0]!.installedBy = ['direct', 'suite:demo']
    expect(await check()).toEqual({ status: 'blocked', reason: 'shared-target' })
    inventory = { items: [structuredClone(item)] }
    inventory.items[0]!.targets.push({ ...item.targets[0]! })
    expect(await check()).toEqual({ status: 'blocked', reason: 'target-alias' })
    inventory = { items: [structuredClone(item), { ...structuredClone(item), registry: 'https://other.example' }] }
    expect(await check()).toEqual({ status: 'blocked', reason: 'shared-target' })
    inventory = { items: [structuredClone(item)] }
    await writeFile(join(item.targets[0]!.installDir, 'SKILL.md'), 'edited')
    expect(await check()).toEqual({ status: 'blocked', reason: 'local-changes' })
    await writeFile(join(item.targets[0]!.installDir, 'SKILL.md'), 'original')
    const metadataPath = join(item.targets[0]!.installDir, '.skillhub/metadata.json')
    const snapshot = await snapshotSkillDirectory(item.targets[0]!.installDir)
    const metadata = { ...item, version: item.targets[0]!.version, ...snapshot, agent: 'dsh', installedAt: item.targets[0]!.installedAt }
    await writeFile(metadataPath, JSON.stringify({ ...metadata, files: undefined }))
    // Authored metadata files cannot be considered owned without a recorded file baseline.
    expect(await check()).toEqual({ status: 'blocked', reason: 'target-unavailable' })
    await writeFile(metadataPath, JSON.stringify(metadata))
    let reads = 0
    expect(await inspectUpgradeBaseline({ read: async () => {
      reads++
      return { items: [reads === 1 ? structuredClone(item) : { ...structuredClone(item), targets: item.targets.map(t => ({ ...t, version: '2.0.0' })) }] }
    } }, { ...item, version: item.targets[0]!.version }, roots)).toEqual({ status: 'blocked', reason: 'record-changed' })
    await rm(item.targets[1]!.installDir, { recursive: true })
    await symlink(item.targets[0]!.installDir, item.targets[1]!.installDir)
    expect(await check()).toEqual({ status: 'blocked', reason: 'target-alias' })
  } finally { await rm(root, { recursive: true, force: true }) }
})
