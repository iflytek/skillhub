import { expect, spyOn, test } from 'bun:test'
import * as fs from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { InventoryStore } from '../../../src/stores/inventory-store'
import { openInstalledProjectionSnapshot } from '../../../src/integrations/installed-projection'

const registry = 'https://registry.example'
async function fixture(count: number, run: (inventory: InventoryStore, targets: { scope: 'user' | 'project'; root: string }[]) => Promise<void>) {
  const root = await fs.realpath(await fs.mkdtemp(join(tmpdir(), 'skillhub-update-scan-')))
  try {
    const inventory = new InventoryStore(root)
    const targets = [{ scope: 'user' as const, root: join(root, 'user') }, { scope: 'project' as const, root: join(root, 'project') }]
    const items = []
    for (let index = 0; index < count / 2; index++) {
      const slug = `skill-${index}`
      const entries = targets.map(target => ({ rootDir: target.root, installDir: join(target.root, slug), agent: 'dsh', version: '1.0.0', installedAt: '2026-10-28', fingerprint: 'original' }))
      for (const entry of entries) {
        await fs.mkdir(join(entry.installDir, '.skillhub'), { recursive: true })
        await fs.writeFile(join(entry.installDir, 'SKILL.md'), 'local content')
        await fs.writeFile(join(entry.installDir, '.skillhub/metadata.json'), JSON.stringify({ registry, namespace: 'demo', slug, ...entry }))
      }
      items.push({ registry, namespace: 'demo', slug, targets: entries })
    }
    await inventory.write({ items })
    await run(inventory, targets)
  } finally { await fs.rm(root, { recursive: true, force: true }) }
}

for (const size of [10, 100, 500]) test(`snapshot ${size} real filesystem targets has linear reads`, async () => {
  await fixture(size, async (inventory, targets) => {
    const read = spyOn(inventory, 'read')
    const files = spyOn(fs, 'readFile')
    const stats = spyOn(fs, 'lstat')
    const started = performance.now()
    try {
      const snapshot = await openInstalledProjectionSnapshot(inventory, registry, targets)
      expect(snapshot.projection.items).toHaveLength(size)
      expect(snapshot.projection.complete).toBe(true)
      for (const item of snapshot.projection.items) { await snapshot.read(item); await snapshot.read(item) }
      const metadataReads = files.mock.calls.filter(([path]) => String(path).endsWith('/metadata.json')).length
      const inventoryStats = stats.mock.calls.filter(([path]) => path === inventory.path).length
      expect(read).toHaveBeenCalledTimes(1)
      expect(metadataReads).toBe(size * 3)
      expect(inventoryStats).toBe(size * 4 + 3)
      console.info(JSON.stringify({ targets: size, inventoryReads: read.mock.calls.length, metadataReads, inventoryStats, elapsedMs: Math.round(performance.now() - started) }))
    } finally { read.mockRestore(); files.mockRestore(); stats.mockRestore() }
  })
})

test('snapshot rejects inventory ABA, atomic replacement and target metadata changes', async () => {
  await fixture(2, async (inventory, targets) => {
    let snapshot = await openInstalledProjectionSnapshot(inventory, registry, targets)
    const original = await fs.readFile(inventory.path, 'utf8')
    const before = await fs.stat(inventory.path)
    await fs.writeFile(inventory.path, original.replace('demo', 'evil'))
    await fs.writeFile(inventory.path, original)
    await fs.utimes(inventory.path, before.atime, before.mtime)
    await expect(snapshot.read(snapshot.projection.items[0]!)).rejects.toThrow('Inventory changed')
    snapshot = await openInstalledProjectionSnapshot(inventory, registry, targets)
    await fs.writeFile(inventory.path + '.replacement', original)
    await fs.rename(inventory.path + '.replacement', inventory.path)
    await expect(snapshot.read(snapshot.projection.items[0]!)).rejects.toThrow('Inventory changed')
    snapshot = await openInstalledProjectionSnapshot(inventory, registry, targets)
    await fs.writeFile(join(snapshot.projection.items[0]!.targetPath, '.skillhub/metadata.json'), '{}')
    await expect(snapshot.read(snapshot.projection.items[0]!)).rejects.toThrow('Installed target changed')
  })
})

test('absent inventory is an empty snapshot; creation invalidates it', async () => {
  await fixture(2, async (inventory, targets) => {
    const existing = await openInstalledProjectionSnapshot(inventory, registry, targets)
    await fs.unlink(inventory.path)
    const empty = await openInstalledProjectionSnapshot(inventory, registry, targets)
    expect(empty.projection).toEqual({ items: [], complete: true, issues: [] })
    await inventory.write({ items: [] })
    await expect(empty.read(existing.projection.items[0]!)).rejects.toThrow('Inventory changed')
  })
})
