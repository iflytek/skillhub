import { snapshotJournalInstallation } from '../services/installed-skill-snapshot'
import { lstat, realpath, rename, rm } from 'node:fs/promises'
import { basename, dirname, isAbsolute, join, resolve } from 'node:path'
import { isDeepStrictEqual } from 'node:util'
import { captureInstallInventory } from '../services/inventory-cas'
import { readInstalledSkillMetadata, sameInstalledSkillSource, type InstalledSkillMetadata } from '../services/installed-skill-metadata'
import { targetInstalledBy } from '../stores/inventory-store'
import type { InstallRecoveryInspection, InstallRecoveryOptions } from './install-recovery'
import type { OperationRecord } from './operation-store'
import type { RecoveryAction } from './transaction-journal'
import { assertOwnedRegularTree, verifiedBackupPath } from './recovery-files'

/** Internal only: caller holds every target lock and the inventory lock. */
export async function restoreBeforeCommit(options: InstallRecoveryOptions, initial: OperationRecord): Promise<InstallRecoveryInspection> {
  let current = initial
  const result = (outcome: 'restored' | 'unknown', reason: string): InstallRecoveryInspection =>
    ({ operationId: current.operationId, revision: current.revision, outcome, reason })
  if (!current.transaction?.preparation || current.transaction.inventoryCommit?.committed || current.operation.status.phase === 'completed') {
    return result('unknown', 'rollback-baseline-unavailable')
  }
  if (current.operation.status.phase === 'failed') return current.operation.status.recovery === 'restored'
    ? result('restored', 'previously-restored') : result('unknown', 'terminal-operation-not-restored')
  try {
    const actions = await planRestore(current)
    const { operationId, ownerId } = current
    current = await options.operations.update(operationId, ownerId, current.revision,
      { ...current.operation.status, phase: 'recovering', files: 'unknown', recovery: 'incomplete' })
    for (const action of actions) {
      current = await options.operations.recordRecoveryAction(operationId, ownerId, current.revision, action, false)
      await options.authorizeMutation?.(action.to === null ? [action.from] : [action.from, action.to], 'rollback')
      // Recheck content and occupancy immediately before each change, after its durable intent.
      const remaining = await planRestore(current)
      if (!remaining.some(candidate => isDeepStrictEqual(candidate, action))) throw new Error('Recovery action no longer matches disk state')
      if ((await options.operations.get(operationId, ownerId))?.revision !== current.revision) throw new Error('Operation changed before recovery mutation')
      if (action.kind === 'remove-staging') await rm(action.from, { recursive: true })
      else {
        if (await exists(action.to!)) throw new Error('Recovery destination is occupied')
        await rename(action.from, action.to!)
      }
      current = await options.operations.recordRecoveryAction(operationId, ownerId, current.revision, action, true)
    }
    if ((await planRestore(current)).length) throw new Error('Recovery still has pending filesystem changes')
    const preparation = current.transaction!.preparation!
    const paths = new Set(current.operation.targetPaths!)
    if (await captureInstallInventory(await options.inventory.read(), preparation.release, paths)
      !== await captureInstallInventory(preparation.before, preparation.release, paths)) throw new Error('Inventory changed during recovery')
    current = await options.operations.update(operationId, ownerId, current.revision,
      { ...current.operation.status, phase: 'failed', files: 'failed', recovery: 'restored' })
    return result('restored', 'original-targets-restored')
  } catch {
    try {
      if (current.operation.status.phase !== 'recovering') current = await options.operations.update(current.operationId, current.ownerId,
        current.revision, { ...current.operation.status, phase: 'recovering', files: 'unknown', recovery: 'incomplete' })
      current = await options.operations.update(current.operationId, current.ownerId, current.revision,
        { ...current.operation.status, phase: 'recovery-required', files: 'unknown', recovery: 'incomplete' })
    } catch { /* Keep the last durable intent; a later scan must recheck disk state. */ }
    return result('unknown', 'rollback-requires-verification')
  }
}

function validStagingSuffix(record: OperationRecord, target: string, path: string): boolean {
  const suffix = basename(path).slice(`.${record.operation.slug}.install-`.length)
  if (!record.transaction?.staging) return /^[a-zA-Z0-9]{6}$/.test(suffix)
  return /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(suffix)
    && record.transaction.staging.some(intent => intent.canonicalSkillDir === target && intent.stagingDir === path)
}

async function planRestore(record: OperationRecord): Promise<RecoveryAction[]> {
  const preparation = record.transaction!.preparation!
  const actions: RecoveryAction[] = []
  if (preparation.targets.length !== record.operation.targetPaths?.length
    || new Set(preparation.targets.map(target => target.canonicalSkillDir)).size !== preparation.targets.length) throw new Error('Invalid preparation targets')
  for (const [index, target] of preparation.targets.entries()) {
    const root = dirname(target.canonicalSkillDir)
    if (!record.operation.targetPaths!.includes(target.canonicalSkillDir) || await realpath(root) !== root
      || !isAbsolute(target.skillDir) || resolve(target.skillDir) !== target.skillDir || basename(target.skillDir) !== record.operation.slug
      || await realpath(dirname(target.skillDir)) !== root
      || !isAbsolute(target.stagingDir) || resolve(target.stagingDir) !== target.stagingDir
      || dirname(target.stagingDir) !== dirname(target.skillDir)
      || !basename(target.stagingDir).startsWith(`.${record.operation.slug}.install-`)
      || !validStagingSuffix(record, target.canonicalSkillDir, target.stagingDir)) throw new Error('Unsafe preparation path')
    const original = await oldMetadata(record, target.canonicalSkillDir)
    const fresh = { ...preparation.release, agent: target.agent, installedAt: target.installedAt }
    const backupMoves = record.transaction!.moves.filter(entry => entry.move.phase === 'backup' && entry.move.index === index)
    if (backupMoves.length > 1) throw new Error('Ambiguous backup')
    const backup = backupMoves[0] ? await verifiedBackupPath(record, backupMoves[0].move) : null
    const backupExists = backup !== null && await exists(backup)
    if (backupExists) {
      if (!original) throw new Error('Backup has no old identity')
      await assertContent(record, backup!, original)
    }
    const stageExists = await exists(target.stagingDir)
    if (stageExists) await assertContent(record, target.stagingDir, fresh)
    const targetExists = await exists(target.skillDir)
    let targetIsOld = false
    let targetIsFresh = false
    if (targetExists) {
      if (original) targetIsOld = await matchesContent(record, target.skillDir, original)
      targetIsFresh = await matchesContent(record, target.skillDir, fresh)
      if (targetIsOld === targetIsFresh) throw new Error('Target identity is unknown or ambiguous')
    }
    if (targetIsFresh) {
      if (stageExists || original && !backupExists) throw new Error('Cannot return replacement to staging')
      actions.push({ kind: 'return-staging', index, from: target.skillDir, to: target.stagingDir })
    }
    if (original && !targetIsOld) {
      if (!backupExists) throw new Error('Original content cannot be recovered')
      actions.push({ kind: 'restore-backup', index, from: backupMoves[0]!.move.to, to: target.skillDir })
    } else if (backupExists) throw new Error('Both original and backup exist')
    if (stageExists || targetIsFresh) actions.push({ kind: 'remove-staging', index, from: target.stagingDir, to: null })
  }
  return actions
}

async function oldMetadata(record: OperationRecord, path: string): Promise<InstalledSkillMetadata | undefined> {
  const preparation = record.transaction!.preparation!
  const owners: InstalledSkillMetadata[] = []
  for (const item of preparation.before.items) {
    for (const target of item.targets) {
      const canonical = join(await realpath(dirname(target.installDir)), basename(target.installDir))
      if (canonical !== path) continue
      if (!sameInstalledSkillSource(item, preparation.release) || !target.fingerprint
        || !isDeepStrictEqual(targetInstalledBy(item, target), ['direct'])) throw new Error('Old target is shared or belongs to another source')
      owners.push({ ...item, version: target.version, fingerprint: target.fingerprint, agent: target.agent, installedAt: target.installedAt })
    }
  }
  if (owners.length > 1) throw new Error('Multiple records own the old target')
  return owners[0]
}

async function exists(path: string): Promise<boolean> {
  try { await lstat(path); return true }
  catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return false; throw error }
}

async function matchesContent(record: OperationRecord, path: string, expected: InstalledSkillMetadata): Promise<boolean> {
  try { await assertContent(record, path, expected); return true } catch { return false }
}

async function assertContent(record: OperationRecord, path: string, expected: InstalledSkillMetadata): Promise<void> {
  await assertOwnedRegularTree(path)
  const read = await readInstalledSkillMetadata(path)
  if (read.status !== 'valid') throw new Error('Recovery metadata is unavailable')
  const value = read.metadata
  if (!sameInstalledSkillSource(value, expected) || value.source !== 'skillhub' || value.version !== expected.version
    || value.fingerprint !== expected.fingerprint || value.agent !== expected.agent || value.installedAt !== expected.installedAt) throw new Error('Recovery identity changed')
  const snapshot = await snapshotJournalInstallation(record, path)
  if (snapshot.fingerprint !== expected.fingerprint || !isDeepStrictEqual(snapshot.files, value.files)) throw new Error('Recovery content changed')
}
