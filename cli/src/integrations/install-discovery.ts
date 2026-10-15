import { isAbsolute, join, resolve } from 'node:path'
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
