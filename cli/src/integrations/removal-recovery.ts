import { lstat, realpath, rename, rm } from 'node:fs/promises'
import { dirname } from 'node:path'
import { isDeepStrictEqual } from 'node:util'
import { captureInstallInventory } from '../services/inventory-cas'
import { readInstalledSkillMetadata, sameInstalledSkillSource } from '../services/installed-skill-metadata'
import { snapshotSkillDirectory } from '../services/skill-fingerprint'
import { acquireSkillTargetLock } from '../services/skill-target-lock'
import type { InventoryStore } from '../stores/inventory-store'
import type { NativeRecoveryResult } from './native-contract'
import type { OperationRecord, OperationStore } from './operation-store'
import { applyRemovalEvent, type RemovalEvent } from './removal-journal'
import { assertOwnedRegularTree } from './recovery-files'

interface RemovalRecoveryOptions {
  operations: OperationStore
  inventory: InventoryStore
  operationId: string
  ownerId: string
  trustedTargetPaths: readonly string[]
  authorizeMutation(paths: readonly string[], phase: 'cleanup' | 'rollback'): Promise<void>
}

/** Caller holds the operation lease. Inventory evidence decides whether to restore or only clean. */
export async function recoverRemoval(options: RemovalRecoveryOptions): Promise<NativeRecoveryResult> {
  const { operations, inventory, operationId, ownerId } = options
  let record = await operations.get(operationId, ownerId)
  if (!record || !['uninstall', 'remove-stale-record'].includes(record.operation.kind)
    || typeof options.authorizeMutation !== 'function') throw new Error('Expected an owned removal with Host authorization')
  const result = (outcome: NativeRecoveryResult['outcome'], reason: string): NativeRecoveryResult =>
    ({ operationId, revision: record!.revision, outcome, reason })
  if (!isDeepStrictEqual(record.operation.targetPaths, [...options.trustedTargetPaths])
    || options.trustedTargetPaths.length !== 1) return result('unknown', 'target-binding-mismatch')
  const append = async (event: RemovalEvent) => {
    record = await operations.recordRemoval(operationId, ownerId, record!.revision, event)
  }
  const update = async (status: OperationRecord['operation']['status']) => {
    record = await operations.update(operationId, ownerId, record!.revision, status)
  }
  let release: (() => Promise<void>) | undefined
  try {
    const journal = record.removal
    if (!journal) {
      // The executor cannot rename anything until an intent has been durably written.
      if (record.operation.status.phase === 'completed') return result('unknown', 'missing-removal-intent')
      if (record.operation.status.phase !== 'failed') {
        if (['unknown', 'recovery-required'].includes(record.operation.status.phase)) {
          await update({ ...record.operation.status, phase: 'recovering' })
        }
        await update({ ...record.operation.status, phase: 'failed', files: 'failed', recovery: 'not-needed' })
      }
      return result('restored', 'no-removal-intent')
    }
    // Revalidate persisted relationships before deriving any filesystem action.
    const intent = journal.intent
    applyRemovalEvent({ ...record, removal: undefined,
      operation: { ...record.operation, status: { ...record.operation.status, phase: 'revalidating' } } }, { type: 'intent', intent })
    if (journal.inventoryCommit) {
      applyRemovalEvent({ ...record, removal: { intent, isolated: journal.isolated },
        operation: { ...record.operation, status: { ...record.operation.status, phase: 'recovering' } } },
      { type: 'beforeCommit', commit: journal.inventoryCommit })
    }
    const { target, quarantine, inventoryItem: item } = intent
    const verifyRoots = async () => {
      if (await realpath(target.root) !== target.root || !(await lstat(target.root)).isDirectory()) throw new Error('Target root changed')
      if (quarantine && (await realpath(dirname(quarantine)) !== dirname(quarantine)
        || !(await lstat(dirname(quarantine))).isDirectory())) throw new Error('Quarantine root changed')
    }
    await verifyRoots()
    release = await acquireSkillTargetLock(target.root, item.slug)
    const locked = await operations.get(operationId, ownerId)
    if (!locked || locked.revision !== record.revision) return result('unknown', 'operation-changed')
    if (!['completed', 'failed', 'recovering'].includes(record.operation.status.phase)) {
      await update({ ...record.operation.status, phase: 'recovering' })
    }
    return await inventory.withLockedSnapshot(async actualInventory => {
      const paths = new Set([target.path])
      const actual = await captureInstallInventory(actualInventory, item, paths)
      const before = await captureInstallInventory({ items: [item] }, item, paths)
      const commit = journal.inventoryCommit
      const after = commit ? await captureInstallInventory(commit.after, item, paths) : null
      if (after === before) throw new Error('Indistinguishable removal snapshots')
      const verifyFiles = async (path: string) => {
        await assertOwnedRegularTree(path)
        const metadata = await readInstalledSkillMetadata(path)
        const snapshot = await snapshotSkillDirectory(path)
        const expected = item.targets.find(entry => entry.installDir === target.path)!
        if (metadata.status !== 'valid' || !sameInstalledSkillSource(metadata.metadata, item)
          || metadata.metadata.version !== item.version || metadata.metadata.fingerprint !== item.fingerprint
          || metadata.metadata.agent !== 'dsh' || metadata.metadata.installedAt !== expected.installedAt
          || !isDeepStrictEqual(metadata.metadata.files, intent.files)
          || !isDeepStrictEqual(snapshot.files, intent.files) || snapshot.fingerprint !== item.fingerprint) {
          throw new Error('Removal files changed')
        }
      }
      if (after !== null && actual === after) {
        if (record!.operation.status.phase === 'failed' || journal.restore) throw new Error('Conflicting removal outcome')
        if (await exists(target.path)) throw new Error('Removed target reappeared')
        if (!commit!.committed) await append({ type: 'committed' })
        let cleanupFailed = false
        if (quarantine) {
          try {
            if (await exists(quarantine)) {
              await options.authorizeMutation([quarantine], 'cleanup')
              await verifyRoots()
              await verifyFiles(quarantine)
              await rm(quarantine, { recursive: true })
            }
            await append({ type: 'cleanup', removed: true })
          } catch {
            cleanupFailed = true
            // Re-read if an atomic journal write succeeded but its acknowledgment was lost.
            record = (await operations.get(operationId, ownerId))!
            await append({ type: 'cleanup', removed: false })
          }
        }
        await update({ ...record!.operation.status, phase: 'completed',
          files: record!.operation.kind === 'uninstall' ? 'removed' : 'stale-record-removed', recovery: 'not-needed',
          warnings: cleanupFailed ? ['Removal quarantine cleanup requires verification.'] : [] })
        return result('committed', cleanupFailed ? 'cleanup-required' : 'removal-verified')
      }
      if (actual !== before || commit?.committed || record!.operation.status.phase === 'completed') {
        throw new Error('Removal inventory conflict')
      }
      if (quarantine) {
        const present = await exists(target.path)
        const isolated = await exists(quarantine)
        if (present === isolated) throw new Error('Ambiguous removal directories')
        if (isolated) {
          if (record!.operation.status.phase === 'failed') throw new Error('Restored target missing')
          await options.authorizeMutation([quarantine, target.path], 'rollback')
          await verifyRoots()
          await verifyFiles(quarantine)
          if (await exists(target.path)) throw new Error('Restore target occupied')
          await append({ type: 'restore', completed: false })
          await rename(quarantine, target.path)
          await append({ type: 'restore', completed: true })
        }
        await verifyFiles(target.path)
      } else if (await exists(target.path)) throw new Error('Stale target reappeared')
      await update({ ...record!.operation.status, phase: 'failed', files: 'failed', recovery: quarantine ? 'restored' : 'not-needed' })
      return result('restored', quarantine ? 'original-target-verified' : 'stale-record-retained')
    })
  } catch {
    const current = await operations.get(operationId, ownerId)
    if (current) {
      record = current
      if (!['completed', 'failed', 'recovery-required'].includes(current.operation.status.phase)) {
        if (current.operation.status.phase !== 'recovering') await update({ ...current.operation.status, phase: 'recovering' })
        await update({ ...record.operation.status, phase: 'recovery-required', files: 'unknown', recovery: 'incomplete' })
      }
    }
    return result('unknown', 'removal-requires-verification')
  } finally { await release?.() }
}

async function exists(path: string): Promise<boolean> {
  try { await lstat(path); return true }
  catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return false; throw error }
}
