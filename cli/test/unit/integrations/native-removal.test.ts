import { expect, test } from 'bun:test'
import { randomUUID } from 'node:crypto'
import { realpath, mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { executeNativeRemoval } from '../../../src/integrations/native-removal'
import { recoverRemoval } from '../../../src/integrations/removal-recovery'
import { inspectRemovalBaseline } from '../../../src/integrations/removal-baseline'
import { OperationStore, operationRequestDigest } from '../../../src/integrations/operation-store'
import { InventoryStore } from '../../../src/stores/inventory-store'
import { snapshotSkillDirectory } from '../../../src/services/skill-fingerprint'

test.each(['uninstall', 'stale', 'stale-root-file', 'local-change', 'commit-denied', 'cleanup-denied', 'commit-ack-lost', 'commit-log-failed', 'inventory-response-lost'] as const)(
  'native removal isolates one exact target: %s', async scenario => {
    const home = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-native-removal-')))
    try {
      const target = { scope: 'project' as const, root: join(home, '.dsh/skills') }
      const path = join(target.root, 'writer')
      await mkdir(join(path, '.skillhub'), { recursive: true })
      await writeFile(join(path, 'SKILL.md'), '# Original')
      const snapshot = await snapshotSkillDirectory(path)
      const selected = { registry: 'https://registry.example', namespace: 'demo', slug: 'writer', version: '1.0.0' }
      const entry = { agent: 'dsh', rootDir: target.root, installDir: path, installedAt: '2026-10-14' }
      const retained = { ...entry, agent: 'other', rootDir: join(home, 'other'), installDir: join(home, 'other/writer') }
      const item = { ...selected, fingerprint: snapshot.fingerprint, targets: [entry, retained] }
      const inventory = new InventoryStore(home)
      await inventory.writeAtomic({ items: [item] })
      await writeFile(join(path, '.skillhub/metadata.json'), JSON.stringify({ ...selected, ...snapshot,
        agent: 'dsh', installedAt: entry.installedAt }))
      if (scenario === 'stale' || scenario === 'stale-root-file') await rm(path, { recursive: true })
      const baseline = await inspectRemovalBaseline(inventory, selected, target)
      if (baseline.status !== 'ready') throw new Error(`Unexpected baseline ${baseline.reason}`)
      const operations = await OperationStore.open(join(home, 'operations'))
      const record = (await operations.accept({ hostInstanceId: operations.hostInstanceId, ownerId: 'session', requestKey: randomUUID(),
        requestDigest: operationRequestDigest({ scenario }), mode: 'new' }, async () => ({ kind: baseline.kind,
        namespace: selected.namespace, slug: selected.slug, version: selected.version, targetLabel: 'Project', targetPaths: [path],
        context: { sourceIdentity: selected.registry, sourceLabel: 'SkillHub', sourceRevision: 'revision', hostLabel: 'Host', initiatedFrom: 'plaza' } })))!
      if (scenario === 'local-change') await writeFile(join(path, 'SKILL.md'), '# User edit')
      const quarantine = join(home, '.dsh/.skillhub-trash', record.operationId)
      if (scenario === 'commit-ack-lost' || scenario === 'commit-log-failed') {
        const append = operations.recordRemoval.bind(operations)
        let failOnce = true
        operations.recordRemoval = async (...args) => {
          if (failOnce && args[3].type === 'committed') {
            failOnce = false
            if (scenario === 'commit-ack-lost') await append(...args)
            throw new Error('Commit acknowledgment lost')
          }
          const result = await append(...args)
          return result
        }
      }
      if (scenario === 'inventory-response-lost') {
        const mutate = inventory.mutateAtomic.bind(inventory)
        let loseResponse = true
        inventory.mutateAtomic = async (...args) => {
          const result = await mutate(...args)
          if (loseResponse) { loseResponse = false; throw new Error('Commit acknowledgment lost') }
          return result
        }
      }
      const execute = () => executeNativeRemoval(operations, inventory, { operationId: record.operationId, ownerId: 'session', baseline,
        authorize: async (paths, phase) => {
          if (scenario === 'stale-root-file' && phase === 'replace') {
            await rm(target.root, { recursive: true })
            await writeFile(target.root, 'Concurrent root replacement')
          }
          if (scenario === 'commit-denied' && phase === 'replace' && paths.includes(target.root)) throw new Error('Commit denied')
          if (scenario === 'cleanup-denied' && phase === 'cleanup') throw new Error('Cleanup denied')
          if (phase === 'replace' && !paths.includes(target.root)) {
            const saved = await operations.get(record.operationId, 'session')
            expect(saved?.removal?.intent.quarantine).toBe(quarantine)
            expect(await readFile(join(path, 'SKILL.md'), 'utf8')).toBe('# Original')
          }
        } })
      if (scenario === 'stale-root-file') {
        await expect(execute()).rejects.toThrow('Removal root changed')
        expect(await inventory.read()).toEqual({ items: [item] })
        const recover = () => recoverRemoval({ operations, inventory, operationId: record.operationId,
          ownerId: 'session', trustedTargetPaths: [path], authorizeMutation: async () => {} })
        expect((await recover()).outcome).toBe('unknown')
        expect(await readFile(target.root, 'utf8')).toBe('Concurrent root replacement')
        expect(await inventory.read()).toEqual({ items: [item] })
        await rm(target.root)
        await mkdir(target.root)
        expect((await recover()).outcome).toBe('restored')
        expect(await inventory.read()).toEqual({ items: [item] })
        return
      }
      if (scenario === 'commit-ack-lost' || scenario === 'commit-log-failed' || scenario === 'inventory-response-lost') {
        await expect(execute()).rejects.toThrow('Commit acknowledgment lost')
        expect(await inventory.read()).toEqual({ items: [{ ...item, targets: [retained] }] })
        await expect(readFile(join(path, 'SKILL.md'))).rejects.toMatchObject({ code: 'ENOENT' })
        expect(await readFile(join(quarantine, 'SKILL.md'), 'utf8')).toBe('# Original')
        const saved = (await operations.get(record.operationId, 'session'))!
        expect(saved.operation.status.phase).toBe('recovery-required')
        expect(saved.removal?.inventoryCommit?.committed).toBe(scenario === 'commit-ack-lost')
      } else if (scenario === 'local-change' || scenario === 'commit-denied') {
        await expect(execute()).rejects.toThrow(scenario === 'local-change' ? 'baseline changed' : 'Commit denied')
        expect(await inventory.read()).toEqual({ items: [item] })
        if (scenario === 'local-change') {
          expect(await readFile(join(path, 'SKILL.md'), 'utf8')).toBe('# User edit')
          expect((await operations.get(record.operationId, 'session'))?.operation.status.phase).toBe('failed')
        } else {
          expect(await readFile(join(quarantine, 'SKILL.md'), 'utf8')).toBe('# Original')
          expect((await operations.get(record.operationId, 'session'))?.operation.status.phase).toBe('recovery-required')
          expect((await operations.listForRecovery()).map(value => value.operationId)).toEqual([record.operationId])
        }
      } else {
        expect((await execute()).outcome).toBe('committed')
        expect(await inventory.read()).toEqual({ items: [{ ...item, targets: [retained] }] })
        await expect(readFile(join(path, 'SKILL.md'))).rejects.toMatchObject({ code: 'ENOENT' })
        const saved = (await operations.get(record.operationId, 'session'))!
        expect(saved.operation.status.phase).toBe('completed')
        expect(saved.operation.status.files).toBe(scenario === 'stale' ? 'stale-record-removed' : 'removed')
        if (scenario === 'cleanup-denied') {
          expect(await readFile(join(quarantine, 'SKILL.md'), 'utf8')).toBe('# Original')
          expect(saved.operation.status.warnings).toHaveLength(1)
          expect((await operations.listForRecovery()).map(value => value.operationId)).toEqual([record.operationId])
        } else {
          await expect(readFile(join(quarantine, 'SKILL.md'))).rejects.toMatchObject({ code: 'ENOENT' })
          expect(await operations.listForRecovery()).toEqual([])
        }
      }
      const recover = (authorizeMutation: (paths: readonly string[], phase: 'cleanup' | 'rollback') => Promise<void> = async () => {}) =>
        recoverRemoval({ operations, inventory, operationId: record.operationId, ownerId: 'session',
          trustedTargetPaths: [path], authorizeMutation })
      if (scenario === 'inventory-response-lost') {
        const committedInventory = { items: [{ ...item, targets: [retained] }] }
        for (const conflict of ['version', 'ownership', 'foreign-source', 'occupied-path'] as const) {
          const conflictedInventory = conflict === 'version'
            ? { items: [{ ...item, version: '9.0.0', targets: [retained] }] }
            : conflict === 'ownership'
              ? { items: [{ ...item, targets: [{ ...entry, installedBy: ['suite:other'] }, retained] }] }
              : conflict === 'foreign-source'
                ? { items: [...committedInventory.items, { ...item, registry: 'https://other.example', targets: [entry] }] }
                : committedInventory
          await inventory.writeAtomic(conflictedInventory)
          if (conflict === 'occupied-path') {
            await mkdir(path)
            await writeFile(join(path, 'SKILL.md'), '# New occupant after commit')
          }
          for (let attempt = 0; attempt < 2; attempt++) {
            const outcome = attempt === 0 ? await recover() : await recoverRemoval({
              operations: await OperationStore.open(operations.root), inventory: new InventoryStore(home),
              operationId: record.operationId, ownerId: 'session', trustedTargetPaths: [path], authorizeMutation: async () => {},
            })
            expect(outcome.outcome).toBe('unknown')
            expect(await inventory.read()).toEqual(conflictedInventory)
            expect(await readFile(join(quarantine, 'SKILL.md'), 'utf8')).toBe('# Original')
            if (conflict === 'occupied-path') {
              expect(await readFile(join(path, 'SKILL.md'), 'utf8')).toBe('# New occupant after commit')
            } else await expect(readFile(join(path, 'SKILL.md'))).rejects.toMatchObject({ code: 'ENOENT' })
            expect((await operations.listForRecovery()).map(value => value.operationId)).toEqual([record.operationId])
          }
          // Only the isolated fixture is repaired; recovery must never remove these conflicts itself.
          if (conflict === 'occupied-path') await rm(path, { recursive: true })
          await inventory.writeAtomic(committedInventory)
        }
      }
      if (scenario === 'commit-denied') {
        // Recovery must not overwrite a newly occupied destination or ignore changed policy/content.
        await mkdir(path)
        await writeFile(join(path, 'SKILL.md'), '# New occupant')
        expect((await recover()).outcome).toBe('unknown')
        expect(await readFile(join(path, 'SKILL.md'), 'utf8')).toBe('# New occupant')
        await rm(path, { recursive: true })
        expect((await recover(async () => { throw new Error('Read-only policy') })).outcome).toBe('unknown')
        expect(await readFile(join(quarantine, 'SKILL.md'), 'utf8')).toBe('# Original')
        await writeFile(join(quarantine, 'SKILL.md'), '# Changed isolation')
        expect((await recover()).outcome).toBe('unknown')
        expect(await readFile(join(quarantine, 'SKILL.md'), 'utf8')).toBe('# Changed isolation')
        await writeFile(join(quarantine, 'SKILL.md'), '# Original')
      }
      const recovered = await recover()
      const restored = scenario === 'commit-denied' || scenario === 'local-change'
      expect(recovered.outcome).toBe(restored ? 'restored' : 'committed')
      expect((await recover()).outcome).toBe(recovered.outcome)
      if (scenario === 'commit-denied') {
        expect(await readFile(join(path, 'SKILL.md'), 'utf8')).toBe('# Original')
        expect(await inventory.read()).toEqual({ items: [item] })
      }
      expect(await operations.listForRecovery()).toEqual([])
    } finally { await rm(home, { recursive: true, force: true }) }
  })
