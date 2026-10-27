import { expect, test } from 'bun:test'
import { realpath, mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { recoverInstallation } from '../../../src/integrations/install-recovery'
import { OperationStore } from '../../../src/integrations/operation-store'
import { InventoryStore, type Inventory } from '../../../src/stores/inventory-store'

interface Fixture { operationId: string; ownerId: string; trustedTargetPaths: string[]; before: Inventory; oldContent: string; newContent: string }

test.skipIf(process.platform === 'win32')('real killed installers recover all pre-commit boundaries without rolling back a committed release', async () => {
  const cases: Array<{ boundary: string; first: boolean; conflict: boolean; recoveryBoundary?: string }> = [
    ...['prepared', 'before-backup', 'after-backup', 'before-install', 'after-install', 'before-commit', 'after-commit'].map(boundary => ({ boundary, first: false, conflict: false })),
    ...['prepared', 'before-install', 'after-install', 'before-commit', 'after-commit'].map(boundary => ({ boundary, first: true, conflict: false })),
    { boundary: 'after-install', first: false, conflict: true },
    ...['return-staging', 'restore-backup', 'remove-staging'].map(recoveryBoundary => ({ boundary: 'after-install', first: false, conflict: false, recoveryBoundary }))
  ]
  const roots: string[] = []
  try {
    const fixtures = await Promise.all(cases.map(async scenario => {
      const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-killed-install-')))
      roots.push(root)
      const child = Bun.spawn([process.execPath, fileURLToPath(new URL('../../fixtures/install-crash-child.ts', import.meta.url)),
        root, scenario.boundary, scenario.first ? 'first' : 'replacement'], { stdout: 'pipe', stderr: 'pipe' })
      const exit = await child.exited
      expect(exit).not.toBe(0)
      expect(await new Response(child.stderr).text()).toBe('')
      const fixture = JSON.parse(await readFile(join(root, 'fixture.json'), 'utf8')) as Fixture
      return { ...scenario, root, fixture }
    }))
    // Real target locks expire after 10 s; inventory locks held at the kill boundary expire after 30 s.
    await Bun.sleep(31_500)
    await Promise.all(fixtures.filter(fixture => fixture.recoveryBoundary).map(async fixture => {
      const child = Bun.spawn([process.execPath, fileURLToPath(new URL('../../fixtures/recovery-crash-child.ts', import.meta.url)),
        fixture.root, fixture.recoveryBoundary!], { stdout: 'pipe', stderr: 'pipe' })
      expect(await child.exited).not.toBe(0)
      expect(await new Response(child.stderr).text()).toBe('')
    }))
    await Bun.sleep(31_500)
    for (const { root, boundary, first, conflict, fixture } of fixtures) {
      const operations = await OperationStore.open(join(root, 'operations'))
      const inventory = new InventoryStore(join(root, 'home'))
      if (conflict) await writeFile(join(fixture.trustedTargetPaths[1]!, 'SKILL.md'), '# User edit after interruption')
      if (boundary === 'after-install' && !first && !conflict) {
        const directories = await Promise.all(['user', 'project'].map(scope => readdir(join(root, scope))))
        const denied = await recoverInstallation({ ...fixture, operations, inventory, authorizeMutation: async (paths, phase) => {
          expect(phase).toBe('rollback')
          expect(paths.length).toBeGreaterThan(0)
          throw new Error('Host policy changed to read-only')
        } })
        expect(denied.outcome).toBe('unknown')
        expect((await operations.get(fixture.operationId, fixture.ownerId))!.operation.status)
          .toMatchObject({ phase: 'recovery-required', recovery: 'incomplete' })
        expect(await inventory.read()).toEqual(fixture.before)
        expect(await Promise.all(['user', 'project'].map(scope => readdir(join(root, scope))))).toEqual(directories)
      }
      const result = await recoverInstallation({ ...fixture, operations, inventory })
      expect(result.outcome, `${boundary}/${first}/${conflict}: ${result.reason}`).toBe(conflict ? 'unknown' : boundary === 'after-commit' ? 'committed' : 'restored')
      if (conflict) {
        expect(await readFile(join(fixture.trustedTargetPaths[0]!, 'SKILL.md'), 'utf8')).toBe(fixture.newContent)
        expect(await readFile(join(fixture.trustedTargetPaths[1]!, 'SKILL.md'), 'utf8')).toBe('# User edit after interruption')
        expect((await operations.listForRecovery()).map(entry => entry.operationId)).toEqual([fixture.operationId])
        continue
      }
      const saved = (await operations.get(fixture.operationId, fixture.ownerId))!
      expect(saved.operation.status.recovery).toBe(boundary === 'after-commit' ? 'not-needed' : 'restored')
      for (const scope of ['user', 'project']) {
        expect(await readdir(join(root, scope))).toEqual(first && boundary !== 'after-commit' ? [] : ['writer'])
        if (!first || boundary === 'after-commit') expect(await readFile(join(root, scope, 'writer/SKILL.md'), 'utf8')).toBe(boundary === 'after-commit' ? fixture.newContent : fixture.oldContent)
      }
      if (boundary !== 'after-commit') expect(await inventory.read()).toEqual(fixture.before)
      else {
        const committed = await inventory.read()
        expect(committed).toEqual(saved.transaction!.inventoryCommit!.after)
        expect(committed.items[0]!.targets[0]!.version).toBe('2.0.0')
        expect(committed.items[0]!.targets).toHaveLength(2)
      }
      expect(await operations.listForRecovery()).toEqual([])
      expect((await recoverInstallation({ ...fixture, operations, inventory })).outcome).toBe(boundary === 'after-commit' ? 'committed' : 'restored')
    }
  } finally { await Promise.all(roots.map(root => rm(root, { recursive: true, force: true }))) }
}, 180_000)
