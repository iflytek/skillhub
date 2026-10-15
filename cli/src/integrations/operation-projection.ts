import type { NativeOperation } from './native-contract'
import type { OperationRecord } from './operation-store'

/** Project the original journal only, never the current inventory or raw error messages. */
export function projectOperation(record: OperationRecord): NativeOperation {
  const { operation, transaction, removal } = record
  const before = removal?.intent.inventoryItem ?? (transaction?.preparation?.before ?? transaction?.inventoryCommit?.before)?.items.find(item =>
    item.registry === operation.context.sourceIdentity && item.namespace === operation.namespace && item.slug === operation.slug)
  // A same-coordinate installation in another scope is not this operation's prior target.
  const beforeVersion = operation.targetPaths?.length && operation.targetPaths.every(path =>
    before?.targets.some(target => target.installDir === path)) ? before?.version ?? null : null
  const completed = operation.status.phase === 'completed'
  const files = operation.status.files
  const outcome = completed && ['installed', 'already-installed', 'upgraded', 'removed', 'stale-record-removed'].includes(files)
    ? files === 'already-installed' ? 'installed' : files as 'installed' | 'upgraded' | 'removed' | 'stale-record-removed'
    : operation.status.phase === 'failed' && operation.status.recovery === 'restored' ? 'restored' : 'unknown'
  return { operationId: record.operationId, revision: record.revision, kind: operation.kind,
    namespace: operation.namespace, slug: operation.slug, version: operation.version, targetLabel: operation.targetLabel,
    context: { ...operation.context }, phase: operation.status.phase, files,
    discovery: operation.status.discovery, recovery: operation.status.recovery, warningCount: operation.status.warnings.length,
    resultDetails: {
      beforeVersion: completed && files === 'already-installed' ? operation.version : beforeVersion,
      afterVersion: completed && ['installed', 'already-installed', 'upgraded'].includes(files) ? operation.version : null,
      targets: (operation.targetPaths ?? []).map((_, index, targets) => ({
        label: targets.length === 1 ? operation.targetLabel : `目标 ${index + 1}（确认计划顺序）`, outcome
      })),
      warnings: operation.status.warnings.map(warning => warning === 'Backup cleanup requires verification.' ? 'backup-cleanup'
        : warning === 'Removal quarantine cleanup requires verification.' ? 'removal-cleanup' : 'verification-required')
    } }
}
