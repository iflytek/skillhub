import { expect, test } from 'bun:test'
import { mkdtemp, readFile, rm, stat, symlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { OperationStore } from '../../../src/integrations/operation-store'
import { recoverInstallation } from '../../../src/integrations/install-recovery'
import { InventoryStore } from '../../../src/stores/inventory-store'

test.skipIf(process.platform === 'win32').each(['before-staging', 'staged-content'])('SIGKILL at %s preserves the staging intent before full preparation', async boundary => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-staging-crash-'))
  try {
    const child = Bun.spawn([process.execPath, fileURLToPath(new URL('../../fixtures/install-crash-child.ts', import.meta.url)),
      root, boundary, 'first'], { stdout: 'pipe', stderr: 'pipe' })
    expect(await child.exited).not.toBe(0)
    expect(await new Response(child.stderr).text()).toBe('')
    const fixture = JSON.parse(await readFile(join(root, 'fixture.json'), 'utf8'))
    const store = await OperationStore.open(join(root, 'operations'))
    const record = (await store.get(fixture.operationId, fixture.ownerId))!
    expect(record.transaction?.preparation).toBeUndefined()
    expect(record.transaction?.staging).toHaveLength(1)
    const intent = record.transaction!.staging![0]!
    expect(intent.canonicalSkillDir).toBe(join(root, 'user/writer'))
    if (boundary === 'before-staging') await expect(stat(intent.stagingDir)).rejects.toMatchObject({ code: 'ENOENT' })
    else expect(await readFile(join(intent.stagingDir, 'SKILL.md'), 'utf8')).toBe(fixture.newContent)
    expect((await store.listForRecovery()).map(item => item.operationId)).toEqual([record.operationId])
    const options = { operations: store, operationId: record.operationId, ownerId: fixture.ownerId,
      inventory: new InventoryStore(join(root, 'home')), trustedTargetPaths: fixture.trustedTargetPaths }
    if (boundary === 'before-staging') {
      await writeFile(join(root, 'user'), 'unrelated file blocking the root')
      expect(await recoverInstallation(options)).toMatchObject({ outcome: 'unknown' })
      expect(await readFile(join(root, 'user'), 'utf8')).toBe('unrelated file blocking the root')
      await rm(join(root, 'user'))
      await symlink(join(root, 'operations'), join(root, 'user'), 'dir')
      expect(await recoverInstallation(options)).toMatchObject({ outcome: 'unknown', reason: 'target-root-changed' })
      await rm(join(root, 'user'))
      expect(await recoverInstallation(options)).toMatchObject({ outcome: 'restored', reason: 'staging-absent-no-target-moves' })
      expect(await store.listForRecovery()).toEqual([])
      expect(await recoverInstallation(options)).toMatchObject({ outcome: 'restored', reason: 'previously-restored' })
      await expect(stat(join(root, 'user'))).rejects.toMatchObject({ code: 'ENOENT' })
    } else {
      expect(await recoverInstallation(options)).toMatchObject({ outcome: 'unknown', reason: 'unprepared-staging-remains' })
      expect(await readFile(join(intent.stagingDir, 'SKILL.md'), 'utf8')).toBe(fixture.newContent)
      expect((await store.listForRecovery()).map(item => item.operationId)).toEqual([record.operationId])
      // Simulate external cleanup of the isolated fixture; recovery itself must never delete it.
      await rm(intent.stagingDir, { recursive: true })
      expect(await recoverInstallation(options)).toMatchObject({ outcome: 'restored', reason: 'staging-absent-no-target-moves' })
      expect(await store.listForRecovery()).toEqual([])
    }
    expect(await options.inventory.read()).toEqual(fixture.before)
  } finally { await rm(root, { recursive: true, force: true }) }
})
