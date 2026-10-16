import { isDeepStrictEqual } from 'node:util'
import { acquireSkillTargetLock } from '../services/skill-target-lock'
import { verifyExistingInstallation } from './native-installed'
import { dirname, isAbsolute, join, resolve } from 'node:path'
import { inspectInstallRecovery } from './install-recovery'
import type { OperationStore } from './operation-store'
import type { InventoryStore } from '../stores/inventory-store'

/** Read-only observation. A matching name alone is never proof of this operation's discovery. */
export async function observeInstallationDiscovery(options: {
  operations: OperationStore
  inventory: InventoryStore
  operationId: string
  ownerId: string
  trustedRoots: readonly string[]
  observe(paths: readonly string[]): Promise<boolean>
}): Promise<boolean> {
  const { operations, inventory, operationId, ownerId } = options
  const record = await operations.get(operationId, ownerId)
  if (!record || !['install', 'upgrade'].includes(record.operation.kind)
    || record.operation.status.phase !== 'completed'
    || !['installed', 'already-installed', 'upgraded'].includes(record.operation.status.files)) return false
  const paths = [...record.operation.targetPaths ?? []]
  const roots = [...options.trustedRoots]
  if (!/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/.test(record.operation.slug)
    || !paths.length || new Set(paths).size !== paths.length
    || paths.some(path => !roots.some(root => isAbsolute(root) && resolve(root) === root
      && join(root, record.operation.slug) === path))) return false
  const inspection = { operations, inventory, operationId, ownerId, trustedTargetPaths: paths }
  try {
    if (record.operation.status.files === 'already-installed') {
      const baseline = record.existingInstallation
      // Older receipts have no original fingerprint evidence: never backfill from today's files.
      if (!baseline || record.transaction || record.operation.kind !== 'install' || paths.length !== 1) return false
      const verify = async () => {
        const release = await acquireSkillTargetLock(dirname(paths[0]!), record.operation.slug)
        try {
          return await inventory.withLockedSnapshot(async current => {
            const actual = await verifyExistingInstallation(current, {
              root: dirname(paths[0]!), registry: record.operation.context.sourceIdentity,
              namespace: record.operation.namespace, slug: record.operation.slug, version: record.operation.version,
              fingerprint: baseline.fingerprint, versionId: baseline.versionId
            })
            return isDeepStrictEqual(actual, baseline)
              && (await operations.get(operationId, ownerId))?.revision === record.revision
          })
        } finally { await release() }
      }
      return await verify() && await options.observe(paths) && await verify()
    }
    const before = await inspectInstallRecovery(inspection)
    if (before.outcome !== 'committed' || before.revision !== record.revision) return false
    if (!await options.observe(paths)) return false
    const after = await inspectInstallRecovery(inspection)
    return after.outcome === 'committed' && after.revision === record.revision
  } catch {
    // Missing services, stale content, contention and unreadable state do not invalidate file installation.
    return false
  }
}
