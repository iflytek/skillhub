import { expect, test } from 'bun:test'
import { createHash, randomUUID } from 'node:crypto'
import { mkdtemp, readFile, readdir, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { zipSync } from 'fflate'
import { OperationStore, operationRequestDigest } from '../../../src/integrations/operation-store'
import { createInstallTransactionObserver } from '../../../src/integrations/transaction-journal'
import { installSkill } from '../../../src/services/install-service'
import { SkillHubClient } from '../../../src/clients/skillhub-client'
import { InventoryStore } from '../../../src/stores/inventory-store'

class LocalClient extends SkillHubClient {
  async download(): Promise<Response> {
    const zip = zipSync({ 'SKILL.md': new TextEncoder().encode('# Journal fixture') })
    return new Response(new Uint8Array(zip).buffer)
  }
}
const fingerprint = 'sha256:' + createHash('sha256').update('SKILL.md:' + createHash('sha256').update('# Journal fixture').digest('hex') + '\n').digest('hex')
const identity = { registry: 'https://registry.example', namespace: 'team', slug: 'writer' }

async function setup(targetRoots = ['skills']) {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-transaction-journal-'))
  const store = await OperationStore.open(join(root, 'operations'))
  const ownerId = 'session'
  const operation = (await store.accept({ ownerId, hostInstanceId: store.hostInstanceId, mode: 'new', requestKey: randomUUID(), requestDigest: operationRequestDigest({ scope: 'project' }) }, async () => ({
    kind: 'install', namespace: 'team', slug: 'writer', version: '1.0.0', targetLabel: '项目', targetPaths: targetRoots.map(dir => join(root, dir, 'writer')),
    context: { sourceIdentity: identity.registry, sourceLabel: 'SkillHub', sourceRevision: randomUUID(), hostLabel: 'Host', initiatedFrom: 'conversation' }
  })))!
  await store.update(operation.operationId, ownerId, 0, { ...operation.operation.status, phase: 'staging' })
  return { root, store, operation, ownerId }
}

test('real installation persists move intent before rename and both inventory snapshots before commit', async () => {
  const { root, store, operation, ownerId } = await setup()
  try {
    const observer = await createInstallTransactionObserver(store, operation.operationId, ownerId)
    const rootDir = join(root, 'skills')
    const home = join(root, 'home')
    await installSkill({ ...identity, force: false, home, client: new LocalClient(identity.registry),
      targets: [{ agent: 'dsh', rootDir, scope: 'project', source: 'explicit' }],
      resolved: { namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1, fingerprint, downloadUrl: '' },
      transactionObserver: {
        ...observer,
        beforeMove: async move => {
          await observer.beforeMove!(move)
          const saved = (await store.get(operation.operationId, ownerId))!
          expect(saved.transaction?.preparation?.before).toEqual({ items: [] })
          expect(saved.transaction?.preparation?.release.fingerprint).toBe(fingerprint)
          expect(saved.transaction?.preparation?.targets[0]?.stagingDir).toBe(move.stagingDir)
          expect(saved.transaction?.moves[0]).toEqual({ move, completed: false })
          expect(await readFile(join(move.stagingDir, 'SKILL.md'), 'utf8')).toBe('# Journal fixture')
        },
        beforeInventoryCommit: async commit => {
          await observer.beforeInventoryCommit!(commit)
          const saved = (await store.get(operation.operationId, ownerId))!
          expect(saved.transaction?.inventoryCommit).toEqual({ ...commit, committed: false })
          expect((await new InventoryStore(home).read()).items).toEqual([])
        }
      }
    })
    const reopened = await OperationStore.open(store.root)
    const saved = (await reopened.get(operation.operationId, ownerId))!
    expect(saved.transaction?.moves[0]?.completed).toBe(true)
    expect(saved.transaction?.inventoryCommit?.committed).toBe(true)
    expect(saved.transaction?.inventoryCommit?.after.items[0]?.fingerprint).toBe(fingerprint)
    expect(await readFile(join(store.root, `${operation.operationId}.json`), 'utf8')).not.toContain('# Journal fixture')
    expect(saved.operation.status.phase).toBe('committing') // The runner must still verify/finalize the operation.
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('staging intent rejects unrelated roots and duplicate target intents', async () => {
  const { root, store, operation, ownerId } = await setup()
  try {
    const staging = { canonicalSkillDir: join(root, 'skills/writer'), rootDir: join(root, 'skills'),
      stagingDir: join(root, 'skills', `.writer.install-${randomUUID()}`) }
    await expect(store.recordTransaction(operation.operationId, ownerId, 1, {
      type: 'beforeStaging', staging: { ...staging, stagingDir: join(root, 'elsewhere', `.writer.install-${randomUUID()}`) }
    })).rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
    const saved = await store.recordTransaction(operation.operationId, ownerId, 1, { type: 'beforeStaging', staging })
    expect(saved.transaction?.staging).toEqual([staging])
    await expect(store.recordTransaction(operation.operationId, ownerId, saved.revision, { type: 'beforeStaging', staging }))
      .rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('all staged targets are durably described before the first rename', async () => {
  const { root, store, operation, ownerId } = await setup(['user', 'project'])
  try {
    const observer = await createInstallTransactionObserver(store, operation.operationId, ownerId)
    let checked = false
    await installSkill({ ...identity, force: false, home: join(root, 'home'), client: new LocalClient(identity.registry),
      targets: ['user', 'project'].map(dir => ({ agent: 'dsh', rootDir: join(root, dir), scope: 'project', source: 'explicit' })),
      resolved: { namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1, fingerprint, downloadUrl: '' },
      transactionObserver: { ...observer, beforeMove: async move => {
        if (!checked) {
          checked = true
          const reopened = await OperationStore.open(store.root)
          const saved = (await reopened.get(operation.operationId, ownerId))!
          expect(saved.transaction!.moves).toEqual([])
          expect(saved.transaction!.preparation!.targets).toHaveLength(2)
          for (const target of saved.transaction!.preparation!.targets) {
            expect(await readFile(join(target.stagingDir, 'SKILL.md'), 'utf8')).toBe('# Journal fixture')
            await expect(readFile(join(target.skillDir, 'SKILL.md'), 'utf8')).rejects.toMatchObject({ code: 'ENOENT' })
          }
          const target = saved.transaction!.preparation!.targets[0]!
          await expect(store.recordTransaction(operation.operationId, ownerId, saved.revision,
            { type: 'beforeMove', move: { ...move, stagingDir: target.stagingDir + '-other' } }))
            .rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
        }
        await observer.beforeMove!(move)
      } }
    })
    expect(checked).toBe(true)
  } finally { await rm(root, { recursive: true, force: true }) }
})

test.each([false, true])('preparation acknowledgment failure leaves target unchanged, existing=%s', async existing => {
  const { root, store, operation, ownerId } = await setup()
  try {
    const options = { ...identity, home: join(root, 'home'), client: new LocalClient(identity.registry),
      targets: [{ agent: 'dsh', rootDir: join(root, 'skills'), scope: 'project' as const, source: 'explicit' as const }],
      resolved: { namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1, fingerprint, downloadUrl: '' } }
    if (existing) await installSkill({ ...options, force: false })
    const inventory = new InventoryStore(options.home)
    const before = await inventory.read()
    const observer = await createInstallTransactionObserver(store, operation.operationId, ownerId)
    await expect(installSkill({ ...options, force: existing, transactionObserver: { ...observer,
      beforePreparation: async preparation => {
        await observer.beforePreparation!(preparation)
        throw new Error('Preparation acknowledgment lost')
      }
    } })).rejects.toThrow('Preparation acknowledgment lost')
    expect(await inventory.read()).toEqual(before)
    expect(await readdir(join(root, 'skills'))).toEqual(existing ? ['writer'] : [])
    const saved = (await store.get(operation.operationId, ownerId))!
    expect(saved.transaction!.moves).toEqual([])
    expect(saved.transaction!.preparation!.before).toEqual(before)
    if (existing) expect(await readFile(join(root, 'skills/writer/SKILL.md'), 'utf8')).toBe('# Journal fixture')
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('journal cannot confirm an unrecorded move or accept a stale writer revision', async () => {
  const { root, store, operation, ownerId } = await setup()
  try {
    const move = { phase: 'install' as const, index: 0, agent: 'dsh', canonicalSkillDir: join(root, 'skills/writer'),
      stagingDir: join(root, '.stage'), backupDir: null, from: join(root, '.stage'), to: join(root, 'skills/writer') }
    await expect(store.recordTransaction(operation.operationId, ownerId, 1, { type: 'afterMove', move })).rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
    await store.recordTransaction(operation.operationId, ownerId, 1, { type: 'beforeMove', move })
    await expect(store.recordTransaction(operation.operationId, ownerId, 1, { type: 'afterMove', move })).rejects.toMatchObject({ code: 'REVISION_CONFLICT' })
    await expect(store.recordTransaction(operation.operationId, ownerId, 2, { type: 'beforeInventoryCommit', commit: { before: { items: [] }, after: { items: [] } } }))
      .rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('native journal requires preparation and all prepared targets before inventory commit', async () => {
  const { root, store, operation, ownerId } = await setup(['user', 'project'])
  try {
    const targets = ['user', 'project'].map(dir => ({ agent: 'dsh', canonicalSkillDir: join(root, dir, 'writer'),
      skillDir: join(root, dir, 'writer'), stagingDir: join(root, dir, '.writer.install-fixture'), installedAt: new Date().toISOString() }))
    const preparation = { before: { items: [] }, release: { ...identity, version: '1.0.0', fingerprint }, targets }
    const target = targets[0]!
    const move = { phase: 'install' as const, index: 0, agent: 'dsh', canonicalSkillDir: target.canonicalSkillDir,
      stagingDir: target.stagingDir, backupDir: null, from: target.stagingDir, to: target.skillDir }
    const observer = await createInstallTransactionObserver(store, operation.operationId, ownerId)
    expect(() => observer.beforeMove!(move)).toThrow('preparation must be persisted')
    await expect(store.recordTransaction(operation.operationId, ownerId, 1,
      { type: 'beforePreparation', preparation: { ...preparation, targets: [target, target] } })).rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
    await observer.beforePreparation!(preparation)
    await expect(observer.beforePreparation!(preparation)).rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
    await observer.beforeMove!(move)
    await observer.afterMove!(move)
    await expect(observer.beforeInventoryCommit!({ before: { items: [] }, after: { items: [] } }))
      .rejects.toMatchObject({ code: 'INVALID_TRANSITION' })
    expect((await store.get(operation.operationId, ownerId))!.transaction!.inventoryCommit).toBeUndefined()
  } finally { await rm(root, { recursive: true, force: true }) }
})

test.each(['missing', 'failed', 'confirmed'] as const)('completed replacement with %s cleanup acknowledgment is classified on reopen', async acknowledgment => {
  const { root, store, operation, ownerId } = await setup()
  try {
    const options = { ...identity, home: join(root, 'home'), client: new LocalClient(identity.registry),
      targets: [{ agent: 'dsh', rootDir: join(root, 'skills'), scope: 'project' as const, source: 'explicit' as const }],
      resolved: { namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1, fingerprint, downloadUrl: '' } }
    await installSkill({ ...options, force: false })
    const observer = await createInstallTransactionObserver(store, operation.operationId, ownerId)
    const result = await installSkill({ ...options, force: true, transactionObserver: { ...observer,
      cleanupResult: async cleanup => {
        if (acknowledgment === 'missing') throw new Error('Cleanup acknowledgment persistence failed')
        await observer.cleanupResult!({ ...cleanup, removed: acknowledgment === 'confirmed' })
      }
    } })
    if (acknowledgment === 'missing') expect(result.transactionWarnings).toContainEqual(expect.objectContaining({ stage: 'cleanupResult' }))
    const committed = (await store.get(operation.operationId, ownerId))!
    expect(committed.transaction?.moves.some(entry => entry.move.phase === 'backup')).toBe(true)
    await store.update(operation.operationId, ownerId, committed.revision,
      { ...committed.operation.status, phase: 'completed', files: 'installed' })
    const reopened = await OperationStore.open(store.root)
    const pending = await reopened.listForRecovery()
    expect(pending.map(entry => entry.operationId)).toEqual(acknowledgment === 'confirmed' ? [] : [operation.operationId])
    // Discovery only: reopening must neither change installed files nor replay the old operation.
    expect(await readFile(join(root, 'skills/writer/SKILL.md'), 'utf8')).toBe('# Journal fixture')
    expect((await reopened.get(operation.operationId, ownerId))!.operation.status.phase).toBe('completed')
  } finally { await rm(root, { recursive: true, force: true }) }
})


test.each(['before-download', 'after-download'] as const)('unresolved journal introduced %s blocks the real installer before target mutation', async boundary => {
  const { root, store, operation, ownerId } = await setup()
  try {
    const target = join(root, 'skills/writer')
    const pending = (await store.accept({ ownerId: 'other-session', hostInstanceId: store.hostInstanceId,
      requestKey: randomUUID(), requestDigest: operationRequestDigest({ scope: 'project' }), mode: 'new' }, async () => ({
      kind: 'install', namespace: 'team', slug: 'writer', version: '1.0.0', targetLabel: '项目', targetPaths: [target],
      context: { sourceIdentity: identity.registry, sourceLabel: 'SkillHub', sourceRevision: randomUUID(), hostLabel: 'Host', initiatedFrom: 'conversation' }
    })))!
    await store.update(pending.operationId, pending.ownerId, 0, { ...pending.operation.status, phase: 'staging' })
    const recordIntent = () => store.recordTransaction(pending.operationId, pending.ownerId, 1, { type: 'beforeMove', move: {
      phase: 'install', index: 0, agent: 'dsh', canonicalSkillDir: target, stagingDir: join(root, '.pending'),
      backupDir: null, from: join(root, '.pending'), to: target
    } })
    let downloads = 0
    class RacingClient extends LocalClient {
      override async download(): Promise<Response> {
        downloads++
        if (boundary === 'after-download') await recordIntent()
        return super.download()
      }
    }
    if (boundary === 'before-download') await recordIntent()
    await expect(installSkill({ ...identity, force: false, home: join(root, 'home'), client: new RacingClient(identity.registry),
      targets: [{ agent: 'dsh', rootDir: join(root, 'skills'), scope: 'project', source: 'explicit' }],
      resolved: { namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1, fingerprint, downloadUrl: '' },
      transactionObserver: await createInstallTransactionObserver(store, operation.operationId, ownerId)
    })).rejects.toMatchObject({ code: 'TARGET_RECOVERY_REQUIRED' })
    expect(downloads).toBe(boundary === 'before-download' ? 0 : 1)
    const entries = await readdir(join(root, 'skills')).catch((error: NodeJS.ErrnoException) => {
      if (error.code === 'ENOENT') return []
      throw error
    })
    expect(entries).toEqual([])
  } finally { await rm(root, { recursive: true, force: true }) }
})
