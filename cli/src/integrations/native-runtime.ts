import { observeInstallationDiscovery } from './install-discovery'
import { readInstalledProjection } from './installed-projection'
import { assessSkillUpdate } from './update-assessment'
import { inspectUpgradeBaseline } from './upgrade-baseline'
import { inspectRemovalBaseline } from './removal-baseline'
import { InventoryStore } from '../stores/inventory-store'
import { recoverInstallation } from './install-recovery'
import { OperationStore, OperationStoreError, operationRequestDigest, type OperationRecord } from './operation-store'
import type { NativeRuntime, NativeOperation } from './native-contract'
import { executeNativeInstall } from './native-install'
import { executeNativeUpgrade } from './native-upgrade'
import { executeNativeRemoval } from './native-removal'
import { recoverRemoval } from './removal-recovery'
import { acquireSkillTargetLock } from '../services/skill-target-lock'

/** Private native adapter. Paths must come from the Host, never a Remote request or tool argument. */
export async function openNativeRuntime(options: { stateDirectory: string; inventoryHome: string }): Promise<NativeRuntime> {
  const operations = await OperationStore.open(options.stateDirectory)
  const inventory = new InventoryStore(options.inventoryHome)
  return {
    hostInstanceId: operations.hostInstanceId,
    observeInstallation: (operationId, ownerId, trustedRoots, observe) => observeInstallationDiscovery({
      operations, inventory, operationId, ownerId, trustedRoots, observe
    }),
    findPlanOperation: async request => {
      let conflict: OperationStoreError | undefined
      // Preserve existing per-kind digests, including operations accepted by earlier plugin builds.
      for (const kind of ['upgrade', 'removal'] as const) {
        try {
          const result = await operations.accept({ hostInstanceId: request.hostInstanceId, ownerId: request.ownerId,
            requestKey: request.requestKey, mode: 'retry',
            requestDigest: operationRequestDigest({ ...request.parameters, kind }) },
          async () => { throw new Error('Plan lookup cannot accept new work') })
          if (result) {
            if (kind === 'upgrade' ? result.operation.kind !== 'upgrade'
              : !['uninstall', 'remove-stale-record'].includes(result.operation.kind)) throw new Error('Plan operation kind mismatch')
            return projectOperation(result)
          }
        } catch (error) {
          if (!(error instanceof OperationStoreError) || error.code !== 'REQUEST_KEY_CONFLICT') throw error
          conflict = error
        }
      }
      if (conflict) throw conflict
      return undefined
    },
    executeRemoval: request => {
      const snapshot = { ...request, baseline: structuredClone(request.baseline) }
      return withOperationLease(operations, snapshot.operationId, snapshot.ownerId,
        () => executeNativeRemoval(operations, inventory, snapshot))
    },
    acceptRemoval: async (request, validateNew) => {
      const result = await operations.accept({ hostInstanceId: request.hostInstanceId, ownerId: request.ownerId,
        requestKey: request.requestKey, mode: request.mode, requestDigest: operationRequestDigest({ ...request.parameters, kind: 'removal' }) },
      async () => {
        const input = await validateNew()
        if (!['uninstall', 'remove-stale-record'].includes(input.kind)) throw new Error('Expected removal acceptance')
        return input
      })
      return result ? projectOperation(result) : undefined
    },
    inspectRemoval: (selected, target) => inspectRemovalBaseline(inventory, { ...selected }, { ...target }),
    executeUpgrade: request => {
      const snapshot = { ...request, baseline: structuredClone(request.baseline) }
      return withOperationLease(operations, snapshot.operationId, snapshot.ownerId,
        () => executeNativeUpgrade(operations, inventory, snapshot))
    },
    acceptUpgrade: async (request, validateNew) => {
      const result = await operations.accept({ hostInstanceId: request.hostInstanceId, ownerId: request.ownerId,
        requestKey: request.requestKey, mode: request.mode, requestDigest: operationRequestDigest({ ...request.parameters, kind: 'upgrade' }) },
      async () => ({ ...await validateNew(), kind: 'upgrade' }))
      return result ? projectOperation(result) : undefined
    },
    assessUpdate: assessSkillUpdate,
    inspectUpgrade: (selected, targets) => inspectUpgradeBaseline(inventory, { ...selected }, targets.map(target => ({ ...target }))),
    listInstalled: (registry, targets) => readInstalledProjection(inventory, registry, targets.map(target => ({ ...target }))),
    executeInstall: request => {
      const snapshot = { ...request }
      return withOperationLease(operations, snapshot.operationId, snapshot.ownerId,
        () => executeNativeInstall(operations, inventory, snapshot))
    },
    acceptInstall: async (request, validateNew) => {
      const result = await operations.accept({ hostInstanceId: request.hostInstanceId, ownerId: request.ownerId,
        requestKey: request.requestKey, mode: request.mode, requestDigest: operationRequestDigest(request.parameters) },
      async () => ({ ...await validateNew(), kind: 'install' }))
      return result ? projectOperation(result) : undefined
    },
    getOperation: async (operationId, ownerId) => {
      const result = await operations.get(operationId, ownerId)
      return result ? projectOperation(result) : undefined
    },
    listPending: async () => (await operations.listForRecovery()).map(record => ({
      operationId: record.operationId, ownerId: record.ownerId, kind: record.operation.kind,
      namespace: record.operation.namespace, slug: record.operation.slug, targetPaths: [...record.operation.targetPaths ?? []]
    })),
    recover: async (operationId, ownerId, trustedTargetPaths, authorize) => {
      if (typeof authorize !== 'function') throw new Error('Native recovery requires Host authorization')
      const paths = [...trustedTargetPaths]
      const result = await withOperationLease(operations, operationId, ownerId, async () => {
        const record = await operations.get(operationId, ownerId)
        const input = { operations, inventory, operationId, ownerId, trustedTargetPaths: paths, authorizeMutation: authorize }
        return record && ['uninstall', 'remove-stale-record'].includes(record.operation.kind)
          ? recoverRemoval(input) : recoverInstallation(input)
      })
      return { operationId: result.operationId, revision: result.revision, outcome: result.outcome, reason: result.reason }
    }
  }
}

/** Serialize a whole native execution/recovery, including staging before target locks are acquired. */
async function withOperationLease<T>(operations: OperationStore, operationId: string, ownerId: string, run: () => Promise<T>): Promise<T> {
  // Validate the UUID and owner before using the operation identity as a lock key.
  if (!await operations.get(operationId, ownerId)) throw new Error('Operation is not owned')
  const release = await acquireSkillTargetLock(operations.root, `execution-${operationId}`)
  let result: T
  try { result = await run() }
  catch (failure) {
    try { await release() }
    catch (error) { throw new AggregateError([failure, error], 'Native operation and lease release failed') }
    throw failure
  }
  await release()
  return result
}

function projectOperation(record: OperationRecord): NativeOperation {
  const { operation } = record
  return { operationId: record.operationId, revision: record.revision, kind: operation.kind,
    namespace: operation.namespace, slug: operation.slug, version: operation.version, targetLabel: operation.targetLabel,
    context: { ...operation.context }, phase: operation.status.phase, files: operation.status.files,
    discovery: operation.status.discovery, recovery: operation.status.recovery, warningCount: operation.status.warnings.length }
}
