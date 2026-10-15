import { isAbsolute, join } from 'node:path'
import { SkillHubClient } from '../clients/skillhub-client'
import { canonicalizeExistingPath } from '../platform/paths'
import { installSkill } from '../services/install-service'
import type { InventoryStore } from '../stores/inventory-store'
import type { NativeInstallExecution, NativeRecoveryResult } from './native-contract'
import { reconcileCommittedInstallation, settleUnpreparedInstallationFailure } from './install-recovery'
import type { OperationStore } from './operation-store'
import { createInstallTransactionObserver } from './transaction-journal'
import { confirmExistingNativeInstall } from './native-installed'

/** Claim an accepted operation exactly once before performing network or target writes. */
export async function executeNativeInstall(operations: OperationStore, inventory: InventoryStore, request: NativeInstallExecution): Promise<NativeRecoveryResult> {
  request = { ...request }
  const record = await operations.get(request.operationId, request.ownerId)
  if (!record || record.operation.kind !== 'install' || record.operation.status.phase !== 'accepted') throw new Error('Installation is not owned and accepted')
  const operation = record.operation
  if (!isAbsolute(request.root) || !/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/.test(request.slug)
    || operation.context.sourceIdentity !== request.registry || operation.namespace !== request.namespace
    || operation.slug !== request.slug || operation.version !== request.version
    || !request.fingerprint || !Number.isInteger(request.versionId) || request.versionId <= 0) throw new Error('Installation snapshot changed')
  const root = request.root
  const target = join(root, request.slug)
  if (operation.targetPaths?.length !== 1 || operation.targetPaths[0] !== target) throw new Error('Installation target changed')
  // Revision CAS arbitrates two runtime instances, including simultaneous retries.
  await operations.update(record.operationId, record.ownerId, record.revision, { ...operation.status, phase: 'staging' })
  let coreStarted = false
  try {
    if (await canonicalizeExistingPath(root) !== root) throw new Error('Installation target changed')
    await request.authorize([root, target], 'staging')
    const existing = await confirmExistingNativeInstall(operations, inventory, request)
    if (existing) return existing
    const client = new SkillHubClient(request.registry)
    const resolved = await client.resolve(request.namespace, request.slug, request.version)
    if (resolved.namespace !== request.namespace || resolved.slug !== request.slug || resolved.version !== request.version
      || resolved.versionId !== request.versionId || resolved.fingerprint !== request.fingerprint) throw new Error('Installation release changed')
    const observer = await createInstallTransactionObserver(operations, record.operationId, record.ownerId)
    coreStarted = true
    await installSkill({ registry: request.registry, namespace: request.namespace, slug: request.slug,
      version: request.version, resolved, client, force: false, inventoryStore: inventory,
      targets: [{ agent: 'dsh', rootDir: root, scope: request.scope, source: 'explicit' }],
      transactionObserver: observer, authorizeTargetMutation: request.authorize, validateStagedSkill: request.validateStaged })
    const result = await reconcileCommittedInstallation({ operations, inventory,
      operationId: record.operationId, ownerId: record.ownerId, trustedTargetPaths: [target] })
    return { operationId: result.operationId, revision: result.revision, outcome: result.outcome, reason: result.reason }
  } catch (error) {
    if (coreStarted) {
      // The shared core has finished its finally cleanup. Inspect under target locks; never
      // roll back or delete anything here, and keep uncertainty on lock/inspection failure.
      try {
        await settleUnpreparedInstallationFailure({ operations, inventory,
          operationId: record.operationId, ownerId: record.ownerId, trustedTargetPaths: [target] })
      } catch { /* Preserve the original failure and require explicit recovery below. */ }
    }
    const current = await operations.get(record.operationId, record.ownerId)
    if (current && ['staging', 'revalidating', 'committing'].includes(current.operation.status.phase)) {
      await operations.update(current.operationId, current.ownerId, current.revision, {
        ...current.operation.status, phase: coreStarted ? 'recovery-required' : 'failed',
        files: coreStarted ? 'unknown' : 'failed', recovery: coreStarted ? 'unknown' : 'not-needed'
      })
    }
    throw error
  }
}
