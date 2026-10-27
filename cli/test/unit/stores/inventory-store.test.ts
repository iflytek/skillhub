import { describe, expect, test } from 'bun:test'
import { mkdir, rm, utimes, writeFile } from 'node:fs/promises'
import { mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, dirname } from 'node:path'
import type { Inventory } from '../../../src/stores/inventory-store'
import { InventoryStore } from '../../../src/stores/inventory-store'

function makeTempHome() {
  return mkdtemp(join(tmpdir(), 'skillhub-store-test-'))
}

function makeTarget(slug: string) {
  return {
    agent: 'claude',
    rootDir: `/projects/${slug}`,
    installDir: `/projects/${slug}/.skillhub/skills/${slug}`,
    installedAt: new Date().toISOString(),
  }
}

describe('InventoryStore', () => {
  test.each(['before', 'after', 'other', 'unreadable'] as const)('rechecks %s write failure under the inventory lock', async mode => {
    class FailingStore extends InventoryStore {
      protected override async writeUnderLock(inventory: Inventory): Promise<void> {
        if (mode === 'before') throw new Error('write failed before rename')
        await super.writeUnderLock(inventory)
        if (mode === 'other') await super.writeUnderLock({ items: [{ ...inventory.items[0]!, targets: inventory.items[0]!.targets.map(target => ({ ...target, version: 'unverifiable' })) }] })
        if (mode === 'unreadable') await writeFile(this.path, '{invalid-json')
        throw new Error('write response lost')
      }
    }
    const home = await makeTempHome()
    const store = new FailingStore(home)
    try {
      const operation = store.upsertTarget('https://registry.example', 'team', 'writer', '1.0.0', makeTarget('writer'))
      if (mode === 'after') {
        await operation
        expect((await store.read()).items[0]?.targets[0]!.version).toBe('1.0.0')
      } else if (mode === 'before') {
        await expect(operation).rejects.toThrow('write failed before rename')
        expect((await store.read()).items).toEqual([])
      } else {
        await expect(operation).rejects.toMatchObject({ name: 'InventoryCommitUnknownError' })
      }
    } finally { await rm(home, { recursive: true, force: true }) }
  })

  test('5 sequential upsertTarget calls all persist', async () => {
    const home = await makeTempHome()
    const store = new InventoryStore(home)

    for (let i = 0; i < 5; i++) {
      await store.upsertTarget(
        'https://skill.xfyun.cn',
        'global',
        `skill-${i}`,
        '1.0.0',
        makeTarget(`skill-${i}`),
      )
    }

    const inventory = await store.read()
    expect(inventory.items).toHaveLength(5)
  })

  test('recovers from a stale lock directory', async () => {
    const home = await makeTempHome()
    const store = new InventoryStore(home)

    // proper-lockfile uses atomic mkdir and an mtime lease.
    await mkdir(dirname(store.path), { recursive: true })
    const lockPath = `${store.path}.lock`
    await mkdir(lockPath)
    const staleTime = new Date(Date.now() - 60_000)
    await utimes(lockPath, staleTime, staleTime)

    // upsertTarget should recover from the stale lock and succeed
    await store.upsertTarget(
      'https://skill.xfyun.cn',
      'global',
      'test-skill',
      '1.0.0',
      makeTarget('test-skill'),
    )

    const inventory = await store.read()
    expect(inventory.items).toHaveLength(1)
    expect(inventory.items[0]?.slug).toBe('test-skill')
  })

  test('locked inspection does not persist callback changes and releases its lock after failure', async () => {
    const store = new InventoryStore(await makeTempHome())
    await expect(store.withLockedSnapshot(async snapshot => {
      snapshot.items.push({ registry: 'https://registry.example', namespace: 'team', slug: 'local', targets: [] })
      throw new Error('Inspection interrupted')
    })).rejects.toThrow('Inspection interrupted')
    expect(await store.read()).toEqual({ items: [] })
    await store.mutateAtomic(snapshot => { snapshot.items.push({ registry: 'https://registry.example', namespace: 'team', slug: 'saved', targets: [] }) })
    expect(await store.withLockedSnapshot(async snapshot => snapshot.items[0]?.slug)).toBe('saved')
  })

  test('removeTarget returns false when item not found', async () => {
    const home = await makeTempHome()
    const store = new InventoryStore(home)

    const result = await store.removeTarget(
      'https://skill.xfyun.cn',
      'global',
      'nonexistent',
      '/some/path',
    )
    expect(result).toBe(false)
  })

  test('removeTargetsByInstallDir returns 0 when no matches', async () => {
    const home = await makeTempHome()
    const store = new InventoryStore(home)

    // Seed one item so the inventory file exists
    await store.upsertTarget(
      'https://skill.xfyun.cn',
      'global',
      'existing-skill',
      '1.0.0',
      makeTarget('existing-skill'),
    )

    const removed = await store.removeTargetsByInstallDir('/nonexistent/path')
    expect(removed).toBe(0)

    // Original item should still be there
    const inventory = await store.read()
    expect(inventory.items).toHaveLength(1)
  })

  test('allows a different version while preserving an unselected target', async () => {
    const home = await makeTempHome()
    const store = new InventoryStore(home)
    const retained = makeTarget('shared-a')
    await store.upsertTarget(
      'https://skill.xfyun.cn',
      'global',
      'shared',
      '1.0.0',
      retained,
      'fp-v1',
    )

    const replacement = makeTarget('shared-b')
    await store.replaceTargetsAtInstallDirs(
      'https://skill.xfyun.cn',
      'global',
      'shared',
      '1.1.0',
      [replacement],
      'fp-v2',
    )

    const inventory = await store.read()
    expect(inventory.items).toHaveLength(1)
    expect(inventory.items[0]?.targets).toEqual([
      { ...retained, version: '1.0.0', fingerprint: 'fp-v1', installedBy: ['direct'] },
      { ...replacement, version: '1.1.0', fingerprint: 'fp-v2', installedBy: ['direct'] }
    ])
  })
})
