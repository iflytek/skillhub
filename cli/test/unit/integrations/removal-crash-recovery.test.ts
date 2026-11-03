import { expect, test } from 'bun:test'
import { realpath, mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { openNativeRuntime } from '../../../src/integrations/native-runtime'
import { inventoryWithoutState, InventoryStore, type Inventory } from '../../../src/stores/inventory-store'

interface Fixture { operationId: string; ownerId: string; path: string; retainedPath: string;
  quarantine: string | null; before: Inventory; content: string }

test.skipIf(process.platform === 'win32')('killed removal and recovery processes preserve the exact commit boundary and other targets', async () => {
  const cases: Array<{ boundary: string; mode: 'uninstall' | 'stale'; recoveryBoundary?: string }> = [
    ...['before-intent', 'intent', 'after-rename', 'isolated', 'before-commit', 'after-commit', 'committed', 'after-cleanup']
      .map(boundary => ({ boundary, mode: 'uninstall' as const })),
    ...['before-intent', 'intent', 'before-commit', 'after-commit', 'committed'].map(boundary => ({ boundary, mode: 'stale' as const })),
    ...['before-restore', 'after-restore'].map(recoveryBoundary => ({ boundary: 'after-rename', mode: 'uninstall' as const, recoveryBoundary })),
    { boundary: 'after-commit', mode: 'uninstall', recoveryBoundary: 'after-recovery-cleanup' },
  ]
  const roots: string[] = []
  const run = async (root: string, boundary: string, mode: string) => {
    const child = Bun.spawn([process.execPath, fileURLToPath(new URL('../../fixtures/removal-crash-child.ts', import.meta.url)), root, boundary, mode],
      { stdout: 'pipe', stderr: 'pipe' })
    expect(await child.exited).not.toBe(0)
    expect(await new Response(child.stderr).text()).toBe('')
  }
  try {
    const fixtures = await Promise.all(cases.map(async scenario => {
      const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-killed-removal-')))
      roots.push(root)
      await run(root, scenario.boundary, scenario.mode)
      return { ...scenario, root, fixture: JSON.parse(await readFile(join(root, 'fixture.json'), 'utf8')) as Fixture }
    }))
    // Use actual stale-lock expiry, not mock lock removal. Inventory commit locks expire after 30 seconds.
    await Bun.sleep(31_500)
    await Promise.all(fixtures.filter(value => value.recoveryBoundary).map(value => run(value.root, value.recoveryBoundary!, 'recover')))
    await Bun.sleep(31_500)
    for (const { root, fixture, boundary, mode } of fixtures) {
      const runtime = await openNativeRuntime({ stateDirectory: join(root, 'operations'), inventoryHome: join(root, 'home') })
      const committed = ['after-commit', 'committed', 'after-cleanup'].includes(boundary)
      const result = await runtime.recover(fixture.operationId, fixture.ownerId, [fixture.path], async () => {})
      expect(result.outcome, `${boundary}/${mode}: ${result.reason}`).toBe(committed ? 'committed' : 'restored')
      const beforeItem = fixture.before.items[0]!
      expect(inventoryWithoutState(await new InventoryStore(join(root, 'home')).read())).toEqual(inventoryWithoutState(committed
        ? { items: [{ ...beforeItem, targets: beforeItem.targets.filter(target => target.installDir !== fixture.path) }] }
        : fixture.before))
      expect(await readFile(join(fixture.retainedPath, 'SKILL.md'), 'utf8')).toBe(fixture.content)
      if (committed || mode === 'stale') await expect(readFile(join(fixture.path, 'SKILL.md'))).rejects.toMatchObject({ code: 'ENOENT' })
      else expect(await readFile(join(fixture.path, 'SKILL.md'), 'utf8')).toBe(fixture.content)
      if (fixture.quarantine) await expect(readFile(join(fixture.quarantine, 'SKILL.md'))).rejects.toMatchObject({ code: 'ENOENT' })
      expect(await runtime.listPending()).toEqual([])
      expect((await runtime.recover(fixture.operationId, fixture.ownerId, [fixture.path], async () => {})).outcome).toBe(result.outcome)
    }
  } finally { await Promise.all(roots.map(root => rm(root, { recursive: true, force: true }))) }
}, 180_000)
