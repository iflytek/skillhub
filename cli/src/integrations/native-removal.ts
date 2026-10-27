import { lstat, mkdir, realpath, rename, rm } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { isDeepStrictEqual } from 'node:util'
import { acquireSkillTargetLock } from '../services/skill-target-lock'
import { readInstalledSkillMetadata, sameInstalledSkillSource } from '../services/installed-skill-metadata'
import { snapshotSkillDirectory } from '../services/skill-fingerprint'
import type { InventoryStore } from '../stores/inventory-store'
import type { NativeRecoveryResult, NativeRemovalExecution } from './native-contract'
import type { OperationStore } from './operation-store'
import type { RemovalEvent } from './removal-journal'
import { inspectRemovalBaseline } from './removal-baseline'
import { commitRemovalInventory } from './removal-inventory'
import { assertOwnedRegularTree } from './recovery-files'

/** Executes under an outer operation lease; never rolls back a possibly committed inventory. */
export async function executeNativeRemoval(operations: OperationStore, inventory: InventoryStore,
  input: NativeRemovalExecution): Promise<NativeRecoveryResult> {
  const baseline = structuredClone(input.baseline)
  const { target, inventoryItem: item } = baseline
  const releaseTarget = item.targets.find(entry => entry.installDir === target.path)
  if (!releaseTarget) throw new Error('Removal target missing')
  await inventory.ensureWritable()
  let record = await operations.get(input.operationId, input.ownerId)
  if (!record || record.operation.status.phase !== 'accepted' || record.operation.kind !== baseline.kind
    || !isDeepStrictEqual(record.operation.targetPaths, [target.path])
    || !sameInstalledSkillSource(item, { registry: record.operation.context.sourceIdentity,
      namespace: record.operation.namespace, slug: record.operation.slug })
    || releaseTarget.version !== record.operation.version) throw new Error('Removal is not owned and accepted')
  record = await operations.update(record.operationId, record.ownerId, record.revision, { ...record.operation.status, phase: 'revalidating' })
  const authorize = (paths: readonly string[], phase: 'staging' | 'replace' | 'cleanup' | 'rollback') =>
    input.authorize(paths, phase, input.operationId)
  const append = async (event: RemovalEvent) => {
    record = await operations.recordRemoval(record!.operationId, record!.ownerId, record!.revision, event)
  }
  let release: (() => Promise<void>) | undefined
  let intentWritten = false
  try {
    await authorize([target.root, target.path], 'staging')
    release = await acquireSkillTargetLock(target.root, item.slug)
    await operations.assertTargetsAvailable([target.path], input)
    if (!isDeepStrictEqual(await inspectRemovalBaseline(inventory, { ...item, version: releaseTarget.version }, target), baseline)) throw new Error('Removal baseline changed')
    const quarantine = baseline.kind === 'uninstall'
      ? join(dirname(target.root), '.skillhub-trash', record.operationId) : null
    const verifyRoots = async () => {
      if (await realpath(target.root) !== target.root || !(await lstat(target.root)).isDirectory()) throw new Error('Removal root changed')
      if (quarantine && (await realpath(dirname(quarantine)) !== dirname(quarantine)
        || !(await lstat(dirname(quarantine))).isDirectory())) throw new Error('Quarantine root changed')
    }
    const verifyQuarantine = async () => {
      if (!quarantine || baseline.kind !== 'uninstall') return
      await assertOwnedRegularTree(quarantine)
      const metadata = await readInstalledSkillMetadata(quarantine)
      const snapshot = await snapshotSkillDirectory(quarantine)
      if (metadata.status !== 'valid' || !isDeepStrictEqual(metadata.metadata, baseline.target.metadata)
        || snapshot.fingerprint !== releaseTarget.fingerprint || !isDeepStrictEqual(snapshot.files, baseline.target.files)) {
        throw new Error('Isolated removal files changed')
      }
    }
    if (quarantine) {
      await authorize([target.root, target.path, dirname(quarantine), quarantine], 'staging')
      await mkdir(dirname(quarantine), { mode: 0o700 }).catch(error => {
        if ((error as NodeJS.ErrnoException).code !== 'EEXIST') throw error
      })
      await verifyRoots()
      const directory = await lstat(dirname(quarantine))
      if (!directory.isDirectory() || directory.isSymbolicLink()
        || (process.getuid && directory.uid !== process.getuid())
        || (process.platform !== 'win32' && (directory.mode & 0o077) !== 0)
        || directory.dev !== (await lstat(target.path)).dev) throw new Error('Unsafe quarantine directory')
      await assertMissing(quarantine)
    }
    // Persist the intent before a rename whose acknowledgment can be lost.
    intentWritten = true
    await append({ type: 'intent', intent: { inventoryItem: item, target: { root: target.root, path: target.path, scope: target.scope },
      quarantine, files: baseline.kind === 'uninstall' ? baseline.target.files : null } })
    if (quarantine) {
      await authorize([target.path, quarantine], 'replace')
      await verifyRoots()
      if (!isDeepStrictEqual(await inspectRemovalBaseline(inventory, { ...item, version: releaseTarget.version }, target), baseline)) throw new Error('Removal baseline changed')
      await assertMissing(quarantine)
      await rename(target.path, quarantine)
      await append({ type: 'isolated' })
      await verifyQuarantine()
    }
    await commitRemovalInventory(inventory, baseline, async (before, after) => {
      await authorize([target.root, target.path, ...(quarantine ? [quarantine] : [])], 'replace')
      await verifyRoots()
      await assertMissing(target.path)
      await verifyQuarantine()
      await append({ type: 'beforeCommit', commit: { before, after } })
    })
    await append({ type: 'committed' })
    let cleanupFailed = false
    if (quarantine) {
      try {
        await authorize([quarantine], 'cleanup')
        await verifyRoots()
        await verifyQuarantine()
        await rm(quarantine, { recursive: true })
        await append({ type: 'cleanup', removed: true })
      } catch {
        cleanupFailed = true
        await append({ type: 'cleanup', removed: false })
      }
    }
    record = await operations.update(record.operationId, record.ownerId, record.revision, { ...record.operation.status,
      phase: 'completed', files: baseline.kind === 'uninstall' ? 'removed' : 'stale-record-removed', recovery: 'not-needed',
      warnings: cleanupFailed ? ['Removal quarantine cleanup requires verification.'] : [] })
    return { operationId: record.operationId, revision: record.revision, outcome: 'committed',
      reason: cleanupFailed ? 'cleanup-required' : baseline.kind }
  } catch (error) {
    const current = await operations.get(input.operationId, input.ownerId)
    if (current && ['revalidating', 'committing'].includes(current.operation.status.phase)) {
      await operations.update(current.operationId, current.ownerId, current.revision, { ...current.operation.status,
        phase: intentWritten ? 'recovery-required' : 'failed', files: intentWritten ? 'unknown' : 'failed',
        recovery: intentWritten ? 'unknown' : 'not-needed' })
    }
    throw error
  } finally { await release?.() }
}

async function assertMissing(path: string): Promise<void> {
  try { await lstat(path) }
  catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return; throw error }
  throw new Error('Removal path is occupied')
}
