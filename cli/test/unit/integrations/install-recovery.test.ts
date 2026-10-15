import { expect, test } from 'bun:test'
import { createHash, randomUUID } from 'node:crypto'
import { chmod, lstat, mkdir, mkdtemp, readFile, rm, symlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { zipSync } from 'fflate'
import { lock } from 'proper-lockfile'
import { SkillHubClient } from '../../../src/clients/skillhub-client'
import { cleanupCommittedInstallation, inspectInstallRecovery, reconcileCommittedInstallation } from '../../../src/integrations/install-recovery'
import { OperationStore, operationRequestDigest } from '../../../src/integrations/operation-store'
import { createInstallTransactionObserver } from '../../../src/integrations/transaction-journal'
import { installSkill, type InstallOptions } from '../../../src/services/install-service'
import { acquireSkillTargetLock } from '../../../src/services/skill-target-lock'
import { InventoryCommitUnknownError, InventoryStore, type Inventory } from '../../../src/stores/inventory-store'

const content = '# Recovery fixture'
const fingerprint = 'sha256:' + createHash('sha256').update('SKILL.md:' + createHash('sha256').update(content).digest('hex') + '\n').digest('hex')
const identity = { registry: 'https://registry.example', namespace: 'team', slug: 'writer' }
class LocalClient extends SkillHubClient {
  async download(): Promise<Response> {
    return new Response(new Uint8Array(zipSync({ 'SKILL.md': new TextEncoder().encode(content) })).buffer)
  }
}

async function fixture(lostCommitAcknowledgment = false, replacement = false, retainBackups = false) {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-recovery-inspection-'))
  try {
    const operations = await OperationStore.open(join(root, 'operations'))
    const inventory = new InventoryStore(join(root, 'home'))
    const trustedTargetPaths = ['user', 'project'].map(scope => join(root, scope, 'writer'))
    const operation = (await operations.accept({ ownerId: 'session', hostInstanceId: operations.hostInstanceId,
      requestKey: randomUUID(), requestDigest: operationRequestDigest({ scope: 'both' }), mode: 'new' }, async () => ({
      kind: 'install', namespace: 'team', slug: 'writer', version: '1.0.0', targetLabel: 'Both', targetPaths: trustedTargetPaths,
      context: { sourceIdentity: identity.registry, sourceLabel: 'SkillHub', sourceRevision: randomUUID(), hostLabel: 'Host', initiatedFrom: 'plaza' }
    })))!
    await operations.update(operation.operationId, operation.ownerId, operation.revision, { ...operation.operation.status, phase: 'staging' })
    const observer = await createInstallTransactionObserver(operations, operation.operationId, operation.ownerId)
    const installOptions: InstallOptions = { ...identity, force: false, home: join(root, 'home'), client: new LocalClient(identity.registry),
      targets: ['user', 'project'].map(scope => ({ agent: 'dsh', rootDir: join(root, scope), scope: scope as 'user' | 'project', source: 'explicit' })),
      resolved: { namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1, fingerprint, downloadUrl: '' } }
    if (replacement) await installSkill(installOptions)
    class LostResponseStore extends InventoryStore {
      override async mutateAtomic<T>(mutate: (inventory: Inventory) => T, beforeCommit?: (before: Inventory, after: Inventory) => Promise<void>): Promise<T> {
        await super.mutateAtomic(mutate, beforeCommit)
        throw new InventoryCommitUnknownError(new Error('Commit response lost'))
      }
    }
    const installation = installSkill({ ...installOptions, force: replacement,
      ...(retainBackups ? { inventoryStore: new LostResponseStore(join(root, 'home')) } : {}),
      transactionObserver: { ...observer, afterInventoryCommit: async () => {
        if (lostCommitAcknowledgment) throw new Error('Commit log unavailable')
        await observer.afterInventoryCommit!()
      } }
    })
    if (retainBackups) await expect(installation).rejects.toMatchObject({ details: { reason: 'INSTALL_COMMIT_UNKNOWN' } })
    else await installation
    return { root, options: { operations, inventory, trustedTargetPaths, operationId: operation.operationId, ownerId: operation.ownerId } }
  } catch (error) { await rm(root, { recursive: true, force: true }); throw error }
}

test.each([false, true])('recovery verifies both targets from disk with lost commit acknowledgment=%s', async lost => {
  const { root, options } = await fixture(lost)
  try {
    const before = await options.operations.get(options.operationId, options.ownerId)
    expect(before!.transaction!.inventoryCommit!.committed).toBe(!lost)
    expect(await inspectInstallRecovery(options)).toMatchObject({ outcome: 'committed', reason: 'inventory-and-all-targets-match' })
    expect(await options.operations.get(options.operationId, options.ownerId)).toEqual(before)
  } finally { await rm(root, { recursive: true, force: true }) }
})

test.each(['content', 'missing', 'symlink', 'metadata', 'inventory'] as const)('second target %s conflict never certifies the whole installation', async change => {
  const { root, options } = await fixture()
  try {
    const second = options.trustedTargetPaths[1]!
    if (change === 'content') await writeFile(join(second, 'SKILL.md'), '# Changed')
    if (change === 'missing' || change === 'symlink') await rm(second, { recursive: true })
    if (change === 'symlink') await symlink(options.trustedTargetPaths[0]!, second, 'junction')
    if (change === 'metadata') {
      const path = join(second, '.skillhub/metadata.json')
      const metadata = JSON.parse(await readFile(path, 'utf8'))
      await writeFile(path, JSON.stringify({ ...metadata, registry: 'https://other.example' }))
    }
    if (change === 'inventory') {
      const current = await options.inventory.read()
      current.items[0]!.targets[1]!.installedBy = ['suite:foreign']
      await options.inventory.writeAtomic(current)
    }
    expect((await inspectInstallRecovery(options)).outcome).toBe('unknown')
    expect(await readFile(join(options.trustedTargetPaths[0]!, 'SKILL.md'), 'utf8')).toBe(content)
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('recovery uses shared target locks and rejects an incomplete trusted target set', async () => {
  const { root, options } = await fixture()
  try {
    expect(await inspectInstallRecovery({ ...options, trustedTargetPaths: options.trustedTargetPaths.slice(0, 1) }))
      .toMatchObject({ outcome: 'unknown', reason: 'target-binding-mismatch' })
    const release = await acquireSkillTargetLock(join(root, 'project'), 'writer')
    try { await expect(inspectInstallRecovery(options)).rejects.toMatchObject({ details: { reason: 'TARGET_BUSY' } }) }
    finally { await release() }
    expect((await inspectInstallRecovery(options)).outcome).toBe('committed')
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('unrelated inventory additions do not obscure commit evidence; before snapshot does not restore files', async () => {
  const { root, options } = await fixture()
  try {
    const current = await options.inventory.read()
    current.items.push({ ...identity, slug: 'other', version: '1.0.0', targets: [] })
    await options.inventory.writeAtomic(current)
    expect((await inspectInstallRecovery(options)).outcome).toBe('committed')
    const record = (await options.operations.get(options.operationId, options.ownerId))!
    await options.inventory.writeAtomic(record.transaction!.inventoryCommit!.before)
    expect(await inspectInstallRecovery(options)).toMatchObject({ outcome: 'inventory-before', reason: 'inventory-matches-before' })
    for (const path of options.trustedTargetPaths) expect(await readFile(join(path, 'SKILL.md'), 'utf8')).toBe(content)
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('committed recovery persists under both locks, survives restart and is idempotent', async () => {
  const { root, options } = await fixture(true)
  try {
    const prior = (await options.operations.get(options.operationId, options.ownerId))!
    await options.operations.update(options.operationId, options.ownerId, prior.revision,
      { ...prior.operation.status, phase: 'unknown', files: 'unknown', recovery: 'unknown' })
    const confirm = options.operations.confirmCommittedInstallation.bind(options.operations)
    let calls = 0
    options.operations.confirmCommittedInstallation = async (...args) => {
      calls++
      await expect(acquireSkillTargetLock(join(root, 'user'), 'writer')).rejects.toMatchObject({ details: { reason: 'TARGET_BUSY' } })
      await expect(acquireSkillTargetLock(join(root, 'project'), 'writer')).rejects.toMatchObject({ details: { reason: 'TARGET_BUSY' } })
      await expect(lock(options.inventory.path, { lockfilePath: `${options.inventory.path}.lock`, realpath: false, retries: 0 }))
        .rejects.toMatchObject({ code: 'ELOCKED' })
      return confirm(...args)
    }
    const reconciled = await reconcileCommittedInstallation(options)
    expect(reconciled).toMatchObject({ outcome: 'committed', reason: 'committed-outcome-persisted' })
    expect(calls).toBe(1)
    const reopened = await OperationStore.open(options.operations.root)
    const saved = (await reopened.get(options.operationId, options.ownerId))!
    expect(saved.operation.status).toMatchObject({ phase: 'completed', files: 'installed', recovery: 'not-needed', discovery: 'unconfirmed' })
    expect(saved.transaction!.inventoryCommit!.committed).toBe(true)
    expect(saved.operation.context.createdAt).toBe(prior.operation.context.createdAt)
    expect(await reopened.listForRecovery()).toEqual([])
    expect((await reconcileCommittedInstallation({ ...options, operations: reopened })).revision).toBe(saved.revision)
    expect(await reopened.accept({ ownerId: options.ownerId, hostInstanceId: reopened.hostInstanceId, requestKey: saved.requestKey,
      requestDigest: saved.requestDigest, mode: 'retry' }, async () => { throw new Error('Must not revalidate/replay') })).toEqual(saved)
  } finally { await rm(root, { recursive: true, force: true }) }
})

test.each(['content', 'before', 'journal-failure', 'revision-race'] as const)('reconciliation preserves uncertain state on %s', async failure => {
  const { root, options } = await fixture(true)
  try {
    const prior = (await options.operations.get(options.operationId, options.ownerId))!
    if (failure === 'content') await writeFile(join(options.trustedTargetPaths[1]!, 'SKILL.md'), '# User changes')
    if (failure === 'before') await options.inventory.writeAtomic(prior.transaction!.inventoryCommit!.before)
    if (failure === 'journal-failure') options.operations.confirmCommittedInstallation = async () => { throw new Error('Disk full') }
    if (failure === 'revision-race') {
      const confirm = options.operations.confirmCommittedInstallation.bind(options.operations)
      options.operations.confirmCommittedInstallation = async (...args) => {
        await options.operations.update(options.operationId, options.ownerId, prior.revision,
          { ...prior.operation.status, phase: 'unknown', files: 'unknown', recovery: 'unknown' })
        return confirm(...args)
      }
    }
    expect((await reconcileCommittedInstallation(options)).outcome).toBe(failure === 'before' ? 'inventory-before' : 'unknown')
    const saved = (await options.operations.get(options.operationId, options.ownerId))!
    expect(saved.operation.status.phase).toBe(failure === 'revision-race' ? 'unknown' : prior.operation.status.phase)
    expect(saved.transaction!.inventoryCommit!.committed).toBe(false)
    expect(await readFile(join(options.trustedTargetPaths[0]!, 'SKILL.md'), 'utf8')).toBe(content)
    if (failure === 'content') expect(await readFile(join(options.trustedTargetPaths[1]!, 'SKILL.md'), 'utf8')).toBe('# User changes')
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('recovered completion preserves unresolved backup cleanup as a separate warning and scan item', async () => {
  const { root, options } = await fixture(true, true)
  try {
    expect((await reconcileCommittedInstallation(options)).outcome).toBe('committed')
    const saved = (await options.operations.get(options.operationId, options.ownerId))!
    expect(saved.transaction!.moves.filter(entry => entry.move.phase === 'backup')).toHaveLength(2)
    expect(saved.operation.status).toMatchObject({ phase: 'completed', files: 'installed', warnings: ['Backup cleanup requires verification.'] })
    expect((await options.operations.listForRecovery()).map(entry => entry.operationId)).toEqual([options.operationId])
    expect((await reconcileCommittedInstallation(options)).revision).toBe(saved.revision)
    for (const path of options.trustedTargetPaths) expect(await readFile(join(path, 'SKILL.md'), 'utf8')).toBe(content)
  } finally { await rm(root, { recursive: true, force: true }) }
})

test.each([false, true])('committed backup cleanup handles retained directories=%s and durably clears pending cleanup', async retained => {
  const { root, options } = await fixture(true, true, retained)
  try {
    const before = (await options.operations.get(options.operationId, options.ownerId))!
    const backups = before.transaction!.moves.filter(entry => entry.move.phase === 'backup').map(entry => entry.move.to)
    if (retained) for (const path of backups) expect((await lstat(path)).isDirectory()).toBe(true)
    const result = await cleanupCommittedInstallation(options)
    expect(result.outcome).toBe('committed')
    expect(result.cleanup).toHaveLength(2)
    for (const entry of result.cleanup!) expect(entry).toMatchObject({ removed: true, recorded: true })
    for (const path of backups) await expect(lstat(path)).rejects.toMatchObject({ code: 'ENOENT' })
    const reopened = await OperationStore.open(options.operations.root)
    expect(await reopened.listForRecovery()).toEqual([])
    expect((await reopened.get(options.operationId, options.ownerId))!.operation.status.warnings).toEqual([])
    await mkdir(backups[0]!)
    await writeFile(join(backups[0]!, 'new-file'), 'Created after confirmed cleanup')
    expect(await cleanupCommittedInstallation({ ...options, operations: reopened })).toMatchObject({ revision: result.revision, cleanup: [] })
    expect(await readFile(join(backups[0]!, 'new-file'), 'utf8')).toBe('Created after confirmed cleanup')
    for (const path of options.trustedTargetPaths) expect(await readFile(join(path, 'SKILL.md'), 'utf8')).toBe(content)
  } finally { await rm(root, { recursive: true, force: true }) }
})

test.each(['content', 'symlink', 'metadata', 'path', 'untracked-metadata'] as const)('backup %s conflict is retained without undoing the committed installation', async conflict => {
  const { root, options } = await fixture(true, true, true)
  try {
    const record = (await options.operations.get(options.operationId, options.ownerId))!
    const backup = record.transaction!.moves.filter(entry => entry.move.phase === 'backup')[1]!.move
    const originalPath = backup.to
    if (conflict === 'content') await writeFile(join(backup.to, 'SKILL.md'), '# Local backup edit')
    if (conflict === 'untracked-metadata') await writeFile(join(backup.to, '.skillhub/local-notes'), 'User content')
    if (conflict === 'symlink') await symlink(options.trustedTargetPaths[0]!, join(backup.to, 'linked'), 'junction')
    if (conflict === 'metadata') {
      const path = join(backup.to, '.skillhub/metadata.json')
      await writeFile(path, JSON.stringify({ ...JSON.parse(await readFile(path, 'utf8')), registry: 'https://foreign.example' }))
    }
    if (conflict === 'path') {
      backup.to = options.trustedTargetPaths[0]!
      backup.backupDir = backup.to
      await writeFile(join(options.operations.root, `${record.operationId}.json`), JSON.stringify(record))
    }
    const result = await cleanupCommittedInstallation(options)
    expect(result.outcome).toBe('committed')
    expect(result.cleanup![1]).toMatchObject({ path: backup.to, removed: false, recorded: true })
    expect((await lstat(originalPath)).isDirectory()).toBe(true)
    const reopened = await OperationStore.open(options.operations.root)
    expect((await reopened.get(options.operationId, options.ownerId))!.operation.status).toMatchObject({ phase: 'completed', warnings: ['Backup cleanup requires verification.'] })
    expect((await reopened.listForRecovery()).map(entry => entry.operationId)).toEqual([record.operationId])
    for (const path of options.trustedTargetPaths) expect(await readFile(join(path, 'SKILL.md'), 'utf8')).toBe(content)
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('cleanup log failure stops further deletion; restart reconciles the missing directory', async () => {
  const { root, options } = await fixture(true, true, true)
  try {
    const record = (await options.operations.get(options.operationId, options.ownerId))!
    const backups = record.transaction!.moves.filter(entry => entry.move.phase === 'backup').map(entry => entry.move.to)
    options.operations.recordRecoveredCleanup = async () => { throw new Error('Journal unavailable') }
    const result = await cleanupCommittedInstallation(options)
    expect(result.cleanup).toHaveLength(1)
    expect(result.cleanup![0]).toMatchObject({ removed: true, recorded: false })
    await expect(lstat(backups[0]!)).rejects.toMatchObject({ code: 'ENOENT' })
    expect((await lstat(backups[1]!)).isDirectory()).toBe(true)
    const reopened = await OperationStore.open(options.operations.root)
    expect((await cleanupCommittedInstallation({ ...options, operations: reopened })).cleanup?.every(entry => entry.removed && entry.recorded)).toBe(true)
    expect(await reopened.listForRecovery()).toEqual([])
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('Host cleanup denial preserves backups and pending cleanup until a later authorized retry', async () => {
  const { root, options } = await fixture(true, true, true)
  try {
    const record = (await options.operations.get(options.operationId, options.ownerId))!
    const backups = record.transaction!.moves.filter(entry => entry.move.phase === 'backup').map(entry => entry.move.to)
    const checked: string[] = []
    const result = await cleanupCommittedInstallation({ ...options, authorizeMutation: async (paths, phase) => {
      expect(phase).toBe('cleanup')
      checked.push(...paths)
      throw new Error('Host policy denies cleanup')
    } })
    expect(checked).toEqual(backups)
    expect(result.outcome).toBe('committed')
    expect(result.cleanup?.every(entry => !entry.removed && entry.recorded)).toBe(true)
    for (const path of backups) expect(await readFile(join(path, 'SKILL.md'), 'utf8')).toBe(content)
    expect((await options.operations.listForRecovery()).map(entry => entry.operationId)).toEqual([record.operationId])
    const retried = await cleanupCommittedInstallation({ ...options, authorizeMutation: async () => {} })
    expect(retried.cleanup?.every(entry => entry.removed && entry.recorded)).toBe(true)
    expect(await options.operations.listForRecovery()).toEqual([])
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('backup content is revalidated after an asynchronous Host policy check', async () => {
  const { root, options } = await fixture(true, true, true)
  try {
    const result = await cleanupCommittedInstallation({ ...options, authorizeMutation: async paths => {
      await writeFile(join(paths[0]!, 'SKILL.md'), '# Changed during policy check')
    } })
    expect(result.cleanup?.every(entry => !entry.removed && entry.recorded)).toBe(true)
    for (const entry of result.cleanup!) expect(await readFile(join(entry.path, 'SKILL.md'), 'utf8')).toBe('# Changed during policy check')
  } finally { await rm(root, { recursive: true, force: true }) }
})

test.skipIf(process.platform === 'win32' || process.getuid?.() === 0)('unreadable backup is retained and never interpreted as absent', async () => {
  const { root, options } = await fixture(true, true, true)
  const record = (await options.operations.get(options.operationId, options.ownerId))!
  const path = record.transaction!.moves.find(entry => entry.move.phase === 'backup')!.move.to
  try {
    await chmod(path, 0o000)
    const result = await cleanupCommittedInstallation(options)
    expect(result.cleanup![0]).toMatchObject({ path, removed: false, recorded: true })
    expect((await lstat(path)).isDirectory()).toBe(true)
    expect((await options.operations.listForRecovery()).map(entry => entry.operationId)).toEqual([record.operationId])
  } finally {
    await chmod(path, 0o700)
    await rm(root, { recursive: true, force: true })
  }
})
