import { link, lstat, open, realpath, unlink } from 'node:fs/promises'
import { join } from 'node:path'
import { isDeepStrictEqual } from 'node:util'
import type { InventoryStore } from '../stores/inventory-store'
import { captureInstallInventory } from '../services/inventory-cas'
import { acquireSkillTargetLock } from '../services/skill-target-lock'
import { assertEntryAbsent, snapshotJournalInstallation } from '../services/installed-skill-snapshot'
import { sameInstalledSkillSource } from '../services/installed-skill-metadata'
import { inspectUpgradeBaseline } from './upgrade-baseline'
import type { OperationRecord, OperationStore } from './operation-store'
import type { InstalledProjectionTarget, InstallationState, ToggleBaseline, UpgradeSelection, NativeToggleExecution, NativeRecoveryResult } from './native-contract'

export async function inspectToggle(inventory: Pick<InventoryStore, 'read'>, selected: UpgradeSelection,
  target: InstalledProjectionTarget, desiredState: InstallationState): Promise<ToggleBaseline> {
  if (!['enabled', 'disabled'].includes(desiredState)) throw new Error('Invalid installation state')
  const baseline = await inspectUpgradeBaseline(inventory, selected, [target])
  if (baseline.status !== 'ready') return baseline
  if (baseline.targets.length !== 1 || baseline.inventoryItem.targets.length !== 1) return { status: 'blocked', reason: 'target-alias' }
  const beforeState = baseline.inventoryItem.targets[0]!.installationState ?? 'enabled'
  if (baseline.targets[0]!.files['SKILL.md.disabled']) return { status: 'blocked', reason: 'entry-conflict' }
  if (beforeState === desiredState) return { status: 'unchanged', installationState: beforeState }
  return { status: 'ready', inventoryItem: baseline.inventoryItem, target: baseline.targets[0]!, beforeState, afterState: desiredState }
}

async function syncDirectory(path: string): Promise<void> {
  if (process.platform === 'win32') return
  const file = await open(path, 'r')
  try { await file.sync() } finally { await file.close() }
}

export async function executeNativeToggle(operations: OperationStore, inventory: InventoryStore,
  request: NativeToggleExecution): Promise<NativeRecoveryResult> {
  const baseline = structuredClone(request.baseline)
  const item = baseline.inventoryItem
  const target = item.targets[0]!
  let record = await operations.get(request.operationId, request.ownerId)
  if (!record || record.operation.status.phase !== 'accepted'
    || record.operation.kind !== (baseline.afterState === 'enabled' ? 'enable' : 'disable')
    || !isDeepStrictEqual(record.operation.targetPaths, [baseline.target.path])
    || !sameInstalledSkillSource(item, { registry: record.operation.context.sourceIdentity, namespace: record.operation.namespace, slug: record.operation.slug })
    || record.operation.version !== target.version) throw new Error('Toggle is not owned and accepted')
  const release = await acquireSkillTargetLock(baseline.target.root, item.slug)
  try {
    record = await operations.update(record.operationId, record.ownerId, record.revision, { ...record.operation.status, phase: 'revalidating' })
    await operations.assertTargetsAvailable([baseline.target.path], request)
    await request.authorize([baseline.target.root, baseline.target.path], 'replace', record.operationId)
    const current = await inspectToggle(inventory, { ...item, version: target.version }, baseline.target, baseline.afterState)
    if (!isDeepStrictEqual(current, baseline)) throw new Error('Toggle baseline changed')
    await assertEntryAbsent(join(target.installDir, baseline.afterState === 'enabled' ? 'SKILL.md' : 'SKILL.md.disabled'))
    record = await operations.recordToggle(record.operationId, record.ownerId, record.revision, {
      inventoryItem: item, target: { root: baseline.target.root, path: baseline.target.path, scope: baseline.target.scope },
      files: baseline.target.files, beforeState: baseline.beforeState, afterState: baseline.afterState,
      from: join(target.installDir, baseline.beforeState === 'enabled' ? 'SKILL.md' : 'SKILL.md.disabled'),
      to: join(target.installDir, baseline.afterState === 'enabled' ? 'SKILL.md' : 'SKILL.md.disabled'), stage: 'intent'
    })
    const intent = record.toggle!
    await link(intent.from, intent.to)
    record = await operations.recordToggle(record.operationId, record.ownerId, record.revision, { ...intent, stage: 'linked' })
    await assertSameEntry(intent.from, intent.to)
    await unlink(intent.from)
    await syncDirectory(target.installDir)
    record = await operations.recordToggle(record.operationId, record.ownerId, record.revision, { ...intent, stage: 'moved' })
    const paths = new Set([target.installDir])
    const expected = await captureInstallInventory({ items: [item] }, item, paths)
    await inventory.mutateAtomic(current => {
      const match = current.items.find(candidate => sameInstalledSkillSource(candidate, item))
      const entry = match?.targets.find(entry => entry.installDir === target.installDir)
      if (!entry || !isDeepStrictEqual(entry, target)) throw new Error('Toggle inventory changed')
      entry.installationState = baseline.afterState
    }, async before => {
      if (await captureInstallInventory(before, item, paths) !== expected) throw new Error('Toggle ownership changed')
      await request.authorize([target.rootDir, target.installDir], 'replace', request.operationId)
      await verifyFiles(record!, baseline.afterState)
    })
    record = await operations.recordToggle(record.operationId, record.ownerId, record.revision, { ...intent, stage: 'committed' })
    return complete(operations, record)
  } catch (error) {
    const current = await operations.get(request.operationId, request.ownerId)
    if (current?.toggle) {
      // Reconcile the actual inventory before considering a reverse rename.
      const result = await reconcileToggle(operations, inventory, current, request.authorize)
      if (result.outcome === 'committed') return result
    } else if (current && current.operation.status.phase === 'revalidating') {
      await operations.update(current.operationId, current.ownerId, current.revision,
        { ...current.operation.status, phase: 'failed', files: 'failed', recovery: 'not-needed' })
    }
    throw error
  } finally { await release() }
}

async function verifyFiles(record: OperationRecord, state: InstallationState): Promise<void> {
  const intent = record.toggle!
  const item = intent.inventoryItem
  const target = item.targets[0]!
  if (await realpath(target.rootDir) !== target.rootDir || await realpath(target.installDir) !== target.installDir) throw new Error('Toggle root changed')
  const snapshot = await snapshotJournalInstallation(record, target.installDir, state)
  if (snapshot.fingerprint !== target.fingerprint || !isDeepStrictEqual(snapshot.files, intent.files)) throw new Error('Toggle content changed')
  if (state === 'enabled' && !await isLinkedEntry(intent.from, intent.to)) await assertEntryAbsent(join(target.installDir, 'SKILL.md.disabled'))
}

async function complete(operations: OperationStore, record: OperationRecord): Promise<NativeRecoveryResult> {
  const saved = await operations.update(record.operationId, record.ownerId, record.revision, { ...record.operation.status,
    phase: 'completed', files: record.toggle!.afterState, recovery: 'not-needed' })
  return { operationId: saved.operationId, revision: saved.revision, outcome: 'committed', reason: saved.toggle!.afterState }
}

/** Caller holds the operation lease and target lock. No inventory write is replayed. */
async function reconcileToggle(operations: OperationStore, inventory: InventoryStore, record: OperationRecord,
  authorize: NativeToggleExecution['authorize']): Promise<NativeRecoveryResult> {
  const unknown = async (): Promise<NativeRecoveryResult> => {
    const current = await operations.get(record.operationId, record.ownerId)
    if (current && !['completed', 'failed', 'recovery-required'].includes(current.operation.status.phase)) {
      record = await operations.update(current.operationId, current.ownerId, current.revision,
        { ...current.operation.status, phase: 'recovery-required', files: 'unknown', recovery: 'unknown' })
    }
    return { operationId: record.operationId, revision: record.revision, outcome: 'unknown', reason: 'toggle-requires-verification' }
  }
  try {
    const intent = record.toggle!
    const before = intent.inventoryItem
    const after = structuredClone(before)
    after.targets[0]!.installationState = intent.afterState
    const paths = new Set([intent.target.path])
    return await inventory.withLockedSnapshot(async actual => {
      const fingerprint = await captureInstallInventory(actual, before, paths)
      if (fingerprint === await captureInstallInventory({ items: [after] }, after, paths)) {
        await verifyFiles(record, intent.afterState)
        if (record.operation.status.phase === 'completed') return { operationId: record.operationId, revision: record.revision, outcome: 'committed', reason: intent.afterState }
        if (['unknown', 'recovery-required'].includes(record.operation.status.phase)) record = await operations.update(record.operationId, record.ownerId, record.revision, { ...record.operation.status, phase: 'recovering' })
        if (record.toggle!.stage !== 'committed') record = await operations.recordToggle(record.operationId, record.ownerId, record.revision, { ...intent, stage: 'committed' })
        return complete(operations, record)
      }
      if (record.toggle!.stage === 'committed' || fingerprint !== await captureInstallInventory({ items: [before] }, before, paths)) return unknown()
      if (record.operation.status.phase === 'failed' && record.operation.status.recovery === 'restored') {
        await verifyFiles(record, intent.beforeState)
        return { operationId: record.operationId, revision: record.revision, outcome: 'restored', reason: 'toggle-restored' }
      }
      record = await operations.update(record.operationId, record.ownerId, record.revision, { ...record.operation.status, phase: 'recovering' })
      // An interrupted exclusive link leaves two names for exactly the same inode.
      // Only this persisted intent and a full-content check authorize removing the extra name.
      if (await isLinkedEntry(intent.from, intent.to)) {
        await verifyFiles(record, intent.beforeState)
        await authorize([intent.target.root, intent.from, intent.to], 'rollback', record.operationId)
        await assertSameEntry(intent.from, intent.to)
        await unlink(intent.to)
        await syncDirectory(intent.target.path)
      }
      let unchanged = false
      try { await verifyFiles(record, intent.beforeState); unchanged = true } catch { /* Inspect the other exact state before any mutation. */ }
      if (!unchanged) {
        await verifyFiles(record, intent.afterState)
        await authorize([intent.target.root, intent.from, intent.to], 'rollback', record.operationId)
        await assertEntryAbsent(intent.from)
        record = await operations.recordToggle(record.operationId, record.ownerId, record.revision, { ...intent, stage: 'restoring' })
        await link(intent.to, intent.from)
        record = await operations.recordToggle(record.operationId, record.ownerId, record.revision, { ...intent, stage: 'restore-linked' })
        await assertSameEntry(intent.from, intent.to)
        await unlink(intent.to)
        await syncDirectory(intent.target.path)
        await verifyFiles(record, intent.beforeState)
      }
      if (record.toggle!.stage !== 'restored') record = await operations.recordToggle(record.operationId, record.ownerId, record.revision, { ...intent, stage: 'restored' })
      record = await operations.update(record.operationId, record.ownerId, record.revision, { ...record.operation.status,
        phase: 'failed', files: 'failed', recovery: 'restored' })
      return { operationId: record.operationId, revision: record.revision, outcome: 'restored', reason: 'toggle-restored' }
    })
  } catch { return unknown() }
}

export async function recoverToggle(operations: OperationStore, inventory: InventoryStore, operationId: string, ownerId: string,
  trustedPaths: readonly string[], authorize: (paths: readonly string[], phase: 'cleanup' | 'rollback') => Promise<void>): Promise<NativeRecoveryResult> {
  let record = await operations.get(operationId, ownerId)
  if (!record || !isDeepStrictEqual(record.operation.targetPaths, [...trustedPaths]) || trustedPaths.length !== 1) throw new Error('Untrusted toggle recovery')
  const root = record.toggle?.target.root ?? trustedPaths[0]!.slice(0, -(record.operation.slug.length + 1))
  const release = await acquireSkillTargetLock(root, record.operation.slug)
  try {
    if (!record.toggle) {
      if (!['accepted', 'revalidating'].includes(record.operation.status.phase)) throw new Error('Missing toggle evidence')
      record = await operations.update(operationId, ownerId, record.revision, { ...record.operation.status, phase: 'failed', files: 'failed', recovery: 'not-needed' })
      return { operationId, revision: record.revision, outcome: 'restored', reason: 'toggle-not-started' }
    }
    return reconcileToggle(operations, inventory, record, (paths, phase) => authorize(paths, phase === 'rollback' ? 'rollback' : 'cleanup'))
  } finally { await release() }
}

async function isLinkedEntry(left: string, right: string): Promise<boolean> {
  try {
    const [a, b] = await Promise.all([lstat(left), lstat(right)])
    return a.isFile() && b.isFile() && !a.isSymbolicLink() && !b.isSymbolicLink() && a.dev === b.dev && a.ino === b.ino
  } catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return false; throw error }
}
async function assertSameEntry(left: string, right: string): Promise<void> {
  if (!await isLinkedEntry(left, right)) throw new Error('Toggle entry changed during transition')
}
