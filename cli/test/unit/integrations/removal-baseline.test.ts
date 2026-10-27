import { expect, test } from 'bun:test'
import { realpath, mkdtemp, mkdir, readFile, rm, symlink, unlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { inspectRemovalBaseline } from '../../../src/integrations/removal-baseline'
import { snapshotSkillDirectory } from '../../../src/services/skill-fingerprint'
import type { Inventory, InventoryItem } from '../../../src/stores/inventory-store'

test('removal inspects one exact target without binding unrelated targets in its baseline', async () => {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-removal-baseline-')))
  try {
    const target = { scope: 'project' as const, root: join(root, 'project/skills') }
    const path = join(target.root, 'writer')
    await mkdir(join(path, '.skillhub'), { recursive: true })
    await writeFile(join(path, 'SKILL.md'), 'original')
    const snapshot = await snapshotSkillDirectory(path)
    const selected = { registry: 'https://registry.example', namespace: 'demo', slug: 'writer', version: '1.0.0' }
    const entry = { version: selected.version, fingerprint: snapshot.fingerprint, agent: 'dsh', rootDir: target.root, installDir: path, installedAt: '2026-10-14T00:00:00Z' }
    const item: InventoryItem = { registry: selected.registry, namespace: selected.namespace, slug: selected.slug,
      targets: [entry, { ...entry, agent: 'other-agent', rootDir: join(root, 'other'), installDir: join(root, 'other/writer') }] }
    await writeFile(join(path, '.skillhub/metadata.json'), JSON.stringify({ ...selected, ...snapshot, agent: 'dsh', installedAt: entry.installedAt }))
    let inventory: Inventory = { items: [structuredClone(item)] }
    const store = { read: async () => structuredClone(inventory) }
    const check = () => inspectRemovalBaseline(store, selected, target)
    const ready = await check()
    expect(ready.status).toBe('ready')
    if (ready.status !== 'ready') throw new Error('Expected valid removal')
    expect(ready.kind).toBe('uninstall')
    expect(ready.inventoryItem.targets).toHaveLength(1)
    expect(ready.target.path).toBe(path)
    expect(await readFile(join(path, 'SKILL.md'), 'utf8')).toBe('original')
    inventory.items[0]!.targets[0]!.installedBy = ['direct', 'suite:demo']
    expect(await check()).toEqual({ status: 'blocked', reason: 'shared-target' })
    inventory = { items: [structuredClone(item)] }
    await writeFile(join(path, 'SKILL.md'), 'local changes')
    expect(await check()).toEqual({ status: 'blocked', reason: 'local-changes' })
    await rm(path, { recursive: true })
    expect(await check()).toMatchObject({ status: 'ready', kind: 'remove-stale-record', target: { path } })
    inventory.items.push({ ...structuredClone(item), registry: 'https://foreign.example' })
    expect(await check()).toEqual({ status: 'blocked', reason: 'shared-target' })
    inventory = { items: [structuredClone(item)] }
    let reads = 0
    expect(await inspectRemovalBaseline({ read: async () => {
      reads++; return { items: [reads === 1 ? structuredClone(item) : { ...structuredClone(item), targets: item.targets.map(t => ({ ...t, version: '2.0.0' })) }] }
    } }, selected, target)).toEqual({ status: 'blocked', reason: 'record-changed' })
    reads = 0
    expect(await inspectRemovalBaseline({ read: async () => {
      reads++
      if (reads === 2) await mkdir(path)
      return structuredClone(inventory)
    } }, selected, target)).toEqual({ status: 'blocked', reason: 'record-changed' })
    await rm(path, { recursive: true })
    await mkdir(join(root, 'outside'))
    await symlink(join(root, 'outside'), path)
    expect(await check()).toMatchObject({ status: 'blocked', reason: 'target-alias' })
    await unlink(path)
    await rm(target.root, { recursive: true })
    await writeFile(target.root, 'not a directory')
    expect(await check()).toEqual({ status: 'blocked', reason: 'target-unavailable' })
  } finally { await rm(root, { recursive: true, force: true }) }
})
