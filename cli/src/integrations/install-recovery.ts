import { lstat, realpath, rm } from 'node:fs/promises'
import { basename, dirname, isAbsolute, resolve } from 'node:path'
import { isDeepStrictEqual } from 'node:util'
import { captureInstallInventory } from '../services/inventory-cas'
import { readInstalledSkillMetadata, sameInstalledSkillSource } from '../services/installed-skill-metadata'
import { snapshotSkillDirectory } from '../services/skill-fingerprint'
import { acquireSkillTargetLock, orderSkillTargetLocks } from '../services/skill-target-lock'
import { targetInstalledBy, type InventoryStore } from '../stores/inventory-store'
import type { OperationRecord, OperationStore } from './operation-store'
import { assertOwnedRegularTree, verifiedBackupPath } from './recovery-files'
import { restoreBeforeCommit } from './install-rollback'
import type { InstallCleanup, InstallMove } from '../services/install-transaction'

export interface InstallRecoveryInspection {
  operationId: string
  revision: number
  outcome: 'committed' | 'inventory-before' | 'restored' | 'unknown'
  reason: string
  cleanup?: Array<InstallCleanup & { recorded: boolean }>
}

export interface InstallRecoveryOptions {
  operations: OperationStore
  operationId: string
  ownerId: string
  inventory: InventoryStore
  trustedTargetPaths: readonly string[]
  /** Revalidate Host policy immediately before each skill-content mutation. */
  authorizeMutation?(paths: readonly string[], phase: 'cleanup' | 'rollback'): Promise<void>
}

/** Read-only evidence at inspection time. Mutating recovery must reacquire locks and revalidate. */
export function inspectInstallRecovery(options: InstallRecoveryOptions): Promise<InstallRecoveryInspection> {
  return inspectInstallation(options, 'inspect')
}

/** Verify and persist a committed outcome without replaying installation or deleting backups. */
export function reconcileCommittedInstallation(options: InstallRecoveryOptions): Promise<InstallRecoveryInspection> {
  return inspectInstallation(options, 'confirm')
}

/** Verify committed files, then clean only journal-bound backups that still match their old version. */
export function cleanupCommittedInstallation(options: InstallRecoveryOptions): Promise<InstallRecoveryInspection> {
  return inspectInstallation(options, 'cleanup')
}

/** Restore an interrupted pre-commit operation; a verified committed operation is never rolled back. */
export function recoverInstallation(options: InstallRecoveryOptions): Promise<InstallRecoveryInspection> {
  return inspectInstallation(options, 'recover')
}

/** After execution has failed, settle only absent staging with no possible target mutation. */
export function settleUnpreparedInstallationFailure(options: InstallRecoveryOptions): Promise<InstallRecoveryInspection> {
  return inspectInstallation(options, 'settle-failure')
}

async function inspectInstallation(options: InstallRecoveryOptions, mode: 'inspect' | 'confirm' | 'cleanup' | 'recover' | 'settle-failure'): Promise<InstallRecoveryInspection> {
  const { operations, operationId, ownerId, inventory, trustedTargetPaths } = options
  const initial = await operations.get(operationId, ownerId)
  if (!initial || !['install', 'upgrade'].includes(initial.operation.kind)) throw new Error('Expected an owned installation operation')
  const result = (outcome: InstallRecoveryInspection['outcome'], reason: string, revision = initial.revision): InstallRecoveryInspection =>
    ({ operationId, revision, outcome, reason })
  const bound = initial.operation.targetPaths
  // Only the live executor can certify it has stopped before the first staging intent.
  const earlyStaging = isEarlyStaging(initial) || (mode === 'settle-failure' && !initial.transaction)
  if (mode === 'settle-failure' && (!earlyStaging
    || !['staging', 'revalidating'].includes(initial.operation.status.phase))) return result('unknown', 'not-unprepared-failure')
  if (!bound?.length || new Set(bound).size !== bound.length || bound.length !== trustedTargetPaths.length
    || bound.some(path => !trustedTargetPaths.includes(path))) return result('unknown', 'target-binding-mismatch')
  for (const path of bound) {
    if (!isAbsolute(path) || resolve(path) !== path || basename(path) !== initial.operation.slug) return result('unknown', 'invalid-target-path')
    try {
      if (earlyStaging ? !await hasCanonicalAncestor(dirname(path)) : await realpath(dirname(path)) !== dirname(path)) return result('unknown', 'target-root-changed')
    } catch { return result('unknown', 'target-root-unavailable') }
  }
  const releases: Array<() => Promise<void>> = []
  let operationError: unknown
  try {
    for (const target of await orderSkillTargetLocks(bound.map(path => ({ rootDir: dirname(path) })), initial.operation.slug)) {
      releases.push(await acquireSkillTargetLock(target.rootDir, initial.operation.slug))
    }
    const current = await operations.get(operationId, ownerId)
    if (!current || current.revision !== initial.revision) return result('unknown', 'operation-changed')
    const commit = current.transaction?.inventoryCommit
    const preparation = current.transaction?.preparation
    if (earlyStaging && (mode === 'recover' || mode === 'settle-failure')) return recoverEmptyStaging(options, current, mode === 'settle-failure')
    if (!commit && (mode !== 'recover' || !preparation)) return result('unknown', 'no-inventory-snapshot')
    const identity = { registry: current.operation.context.sourceIdentity, namespace: current.operation.namespace, slug: current.operation.slug }
    const paths = new Set(bound)
    try {
      return await inventory.withLockedSnapshot(async actualInventory => {
        const actual = await captureInstallInventory(actualInventory, identity, paths)
        if (mode === 'recover' && preparation
          && actual === await captureInstallInventory(preparation.before, identity, paths)) {
          if (commit && actual === await captureInstallInventory(commit.after, identity, paths)) return result('unknown', 'indistinguishable-inventory-snapshots')
          return restoreBeforeCommit(options, current)
        }
        if (!commit) return result('unknown', 'inventory-conflict')
        const before = await captureInstallInventory(commit.before, identity, paths)
        const after = await captureInstallInventory(commit.after, identity, paths)
        if (before === after) return result('unknown', 'indistinguishable-inventory-snapshots')
        // This identifies the current inventory, not a historical guarantee that commit never happened.
        if (actual === before) return result('inventory-before', 'inventory-matches-before')
        if (actual !== after) return result('unknown', 'inventory-conflict')
        const matches = commit.after.items.filter(item => sameInstalledSkillSource(item, identity))
        if (matches.length !== 1 || matches[0]!.version !== current.operation.version || !matches[0]!.fingerprint) {
          return result('unknown', 'invalid-committed-identity')
        }
        const item = matches[0]!
        for (const path of bound) {
          // Check again under the target locks; a parent alias must not redirect the inspection.
          if (await realpath(dirname(path)) !== dirname(path)) return result('unknown', 'target-root-changed')
          await assertOwnedRegularTree(path)
          const targets = []
          for (const target of item.targets) {
            if (await realpath(target.installDir) === path) targets.push(target)
          }
          if (targets.length !== 1) return result('unknown', 'inventory-target-mismatch')
          const target = targets[0]!
          const read = await readInstalledSkillMetadata(path)
          if (read.status !== 'valid') return result('unknown', 'metadata-unavailable')
          const metadata = read.metadata
          if (!sameInstalledSkillSource(metadata, identity) || metadata.version !== item.version
            || metadata.fingerprint !== item.fingerprint || metadata.agent !== target.agent
            || metadata.installedAt !== target.installedAt || metadata.source !== 'skillhub') {
            return result('unknown', 'metadata-conflict')
          }
          const snapshot = await snapshotSkillDirectory(path)
          if (snapshot.fingerprint !== item.fingerprint || !isDeepStrictEqual(snapshot.files, metadata.files)) {
            return result('unknown', 'installed-content-changed')
          }
        }
        if (await captureInstallInventory(await inventory.read(), identity, paths) !== actual
          || (await operations.get(operationId, ownerId))?.revision !== current.revision) return result('unknown', 'state-changed-during-inspection')
        if (mode !== 'inspect') {
          try {
            const saved = await operations.confirmCommittedInstallation(operationId, ownerId, current.revision)
            if (mode === 'cleanup' || mode === 'recover') {
              const cleaned = await cleanupBackups(options, saved)
              return { ...result('committed', 'committed-outcome-persisted', cleaned.revision), cleanup: cleaned.results }
            }
            return result('committed', 'committed-outcome-persisted', saved.revision)
          } catch { return result('unknown', 'commit-confirmation-not-persisted') }
        }
        return result('committed', 'inventory-and-all-targets-match')
      })
    } catch { return result('unknown', 'state-unreadable-or-unsafe') }
  } catch (error) { operationError = error; throw error }
  finally { await releaseLocks(releases, operationError) }
}

function isEarlyStaging(record: OperationRecord): boolean {
  const transaction = record.transaction
  return !!transaction?.staging?.length && !transaction.preparation && !transaction.inventoryCommit
    && !transaction.moves.length && !transaction.recoveryActions?.length && !transaction.cleanup.length
}

/** A missing root is expected before mkdir; only ENOENT permits walking to its parent. */
async function hasCanonicalAncestor(path: string): Promise<boolean> {
  try { await lstat(path) }
  catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'ENOENT' || dirname(path) === path) throw error
    return hasCanonicalAncestor(dirname(path))
  }
  return await realpath(path) === path
}

/** No target move can precede preparation. Never remove content from an unprepared staging intent. */
async function recoverEmptyStaging(options: InstallRecoveryOptions, initial: OperationRecord, settleFailure = false): Promise<InstallRecoveryInspection> {
  let current = initial
  const result = (outcome: 'restored' | 'unknown', reason: string): InstallRecoveryInspection =>
    ({ operationId: current.operationId, revision: current.revision, outcome, reason })
  if (current.operation.status.phase === 'failed') return current.operation.status.recovery === 'restored'
    ? result('restored', 'previously-restored') : result('unknown', 'terminal-operation-not-restored')
  try {
    for (const intent of current.transaction?.staging ?? []) {
      const root = dirname(intent.canonicalSkillDir)
      const prefix = `.${current.operation.slug}.install-`
      if (!current.operation.targetPaths!.includes(intent.canonicalSkillDir) || intent.rootDir !== root
        || dirname(intent.stagingDir) !== root || !basename(intent.stagingDir).startsWith(prefix)
        || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(basename(intent.stagingDir).slice(prefix.length))
        || !await hasCanonicalAncestor(root)) return result('unknown', 'staging-binding-mismatch')
      try { await lstat(intent.stagingDir); return result('unknown', 'unprepared-staging-remains') }
      catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error }
    }
    if (settleFailure) {
      current = await options.operations.update(current.operationId, current.ownerId, current.revision,
        { ...current.operation.status, phase: 'failed', files: 'failed', recovery: 'not-needed' })
      return result('restored', 'staging-absent-no-target-moves')
    }
    current = await options.operations.update(current.operationId, current.ownerId, current.revision,
      { ...current.operation.status, phase: 'recovering', files: 'unknown', recovery: 'incomplete' })
    current = await options.operations.update(current.operationId, current.ownerId, current.revision,
      { ...current.operation.status, phase: 'failed', files: 'failed', recovery: 'restored' })
    return result('restored', 'staging-absent-no-target-moves')
  } catch { return result('unknown', 'early-staging-recovery-unverified') }
}

async function cleanupBackups(options: InstallRecoveryOptions, current: OperationRecord): Promise<{
  revision: number; results: Array<InstallCleanup & { recorded: boolean }>
}> {
  let revision = current.revision
  const results: Array<InstallCleanup & { recorded: boolean }> = []
  const acknowledged = new Map(current.transaction!.cleanup.map(entry => [entry.path, entry.removed]))
  for (const entry of current.transaction!.moves.filter(entry => entry.move.phase === 'backup')) {
    if (acknowledged.get(entry.move.to) === true) continue
    let cleanup: InstallCleanup
    try {
      const path = await verifiedBackupPath(current, entry.move)
      let exists = true
      try { await lstat(path) }
      catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') exists = false; else throw error }
      if (exists) {
        await options.authorizeMutation?.([path], 'cleanup')
        // Policy checks may yield; verify the same bound content again before deletion.
        if (await verifiedBackupPath(current, entry.move) !== path) throw new Error('Backup path changed')
        await assertBackupContent(current, entry.move, path)
        if ((await options.operations.get(current.operationId, current.ownerId))?.revision !== revision) throw new Error('Operation changed before backup cleanup')
        await rm(path, { recursive: true })
      }
      cleanup = { path: entry.move.to, removed: true }
    } catch (error) {
      cleanup = { path: entry.move.to, removed: false, error: error instanceof Error ? error.message.slice(0, 2048) : 'Backup cleanup could not be verified' }
    }
    try {
      const saved = await options.operations.recordRecoveredCleanup(current.operationId, current.ownerId, revision, cleanup)
      revision = saved.revision
      results.push({ ...cleanup, recorded: true })
    } catch {
      results.push({ ...cleanup, recorded: false })
      break // Do not continue deleting if the result cannot be durably recorded.
    }
  }
  return { revision, results }
}

async function assertBackupContent(record: OperationRecord, move: InstallMove, path: string): Promise<void> {
  await assertOwnedRegularTree(path)
  const identity = { registry: record.operation.context.sourceIdentity, namespace: record.operation.namespace, slug: record.operation.slug }
  const items = record.transaction!.inventoryCommit!.before.items.filter(item => sameInstalledSkillSource(item, identity))
  if (items.length !== 1 || !items[0]!.fingerprint) throw new Error('Backup baseline is unavailable')
  const item = items[0]!
  const targets = []
  for (const target of item.targets) {
    if (await realpath(target.installDir) === move.canonicalSkillDir) targets.push(target)
  }
  if (targets.length !== 1) throw new Error('Backup target baseline does not match')
  const target = targets[0]!
  const sources = targetInstalledBy(item, target)
  if (sources.length !== 1 || sources[0] !== 'direct') throw new Error('Backup has shared ownership')
  const read = await readInstalledSkillMetadata(path)
  if (read.status !== 'valid') throw new Error('Backup metadata is unavailable')
  const metadata = read.metadata
  if (!sameInstalledSkillSource(metadata, identity) || metadata.version !== item.version
    || metadata.fingerprint !== item.fingerprint || metadata.installedAt !== target.installedAt
    || metadata.agent !== target.agent || metadata.source !== 'skillhub') throw new Error('Backup metadata does not match its baseline')
  const snapshot = await snapshotSkillDirectory(path)
  if (snapshot.fingerprint !== item.fingerprint || !isDeepStrictEqual(snapshot.files, metadata.files)) throw new Error('Backup content was modified')
}

async function releaseLocks(releases: Array<() => Promise<void>>, operationError: unknown): Promise<void> {
  const failures: unknown[] = []
  for (const release of releases.reverse()) {
    try { await release() } catch (error) { failures.push(error) }
  }
  if (failures.length) throw new AggregateError(operationError ? [operationError, ...failures] : failures, 'Recovery inspection lock release failed')
}

