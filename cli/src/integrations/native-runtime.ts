import { previewPackage, exportPackage, LOCAL_PACKAGE_REGISTRY } from './package-transfer'
import { FileResetStore } from './native-file-reset'
import { backupChanges, inspectChanges, prepareVersionChanges, readChangeDiff } from './native-changes'
import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { canonicalizeExistingPath, pathsOverlap } from '../platform/paths'
import { isDeepStrictEqual } from 'node:util'
import { inspectToggle, executeNativeToggle, recoverToggle } from './native-toggle'
import { readSkillExamples } from '../services/skill-examples'
import { observeInstallationDiscovery } from './install-discovery'
import { readInstalledProjection, readAllInstalledProjection, openInstalledProjectionSnapshot } from './installed-projection'
import { assessSkillUpdate } from './update-assessment'
import { inspectUpgradeBaseline } from './upgrade-baseline'
import { inspectRemovalBaseline } from './removal-baseline'
import { InventoryStore } from '../stores/inventory-store'
import { recoverInstallation } from './install-recovery'
import { OperationStore, OperationStoreError, operationRequestDigest } from './operation-store'
import { projectOperation } from './operation-projection'
import type { NativeRuntime } from './native-contract'
import { executeNativeInstall } from './native-install'
import { executeNativeUpgrade } from './native-upgrade'
import { executeNativeRemoval } from './native-removal'
import { recoverRemoval } from './removal-recovery'
import { acquireSkillTargetLock } from '../services/skill-target-lock'

/** Private native adapter. Paths must come from the Host, never a Remote request or tool argument. */
export async function openNativeRuntime(options: { stateDirectory: string; inventoryHome: string }): Promise<NativeRuntime> {
  const operations = await OperationStore.open(options.stateDirectory)
  const inventory = new InventoryStore(options.inventoryHome)
  const imports = new InventoryStore(join(options.stateDirectory, 'local-imports'))
  const storeFor = (registry: string) => registry === LOCAL_PACKAGE_REGISTRY ? imports : inventory
  const resets = new FileResetStore(options.stateDirectory, operations.hostInstanceId, inventory, async () => {
    if ((await operations.listForRecovery()).length) throw new Error('Operation recovery required')
  })
  const ensureCompatible = async () => {
    if (await inventory.diskVersion() === 3 && (await operations.listForRecovery()).length) {
      throw new OperationStoreError('TARGET_RECOVERY_REQUIRED')
    }
    if ((await resets.list()).some(record => !['completed', 'failed'].includes(record.operation.phase))) throw new Error('File reset recovery required')
    await inventory.ensureWritable()
  }
  const guardForeignClaims = (request: import('./native-contract').NativeInstallExecution, foreign: InventoryStore): typeof request.authorize => async (paths, phase) => {
    await request.authorize(paths, phase)
    if (phase !== 'staging' && phase !== 'replace') return
    // Replacement callbacks run under the same target lock used by both sources.
    // A missing directory does not erase a persisted ownership claim.
    const destination = join(request.root, request.slug)
    const state = await foreign.read()
    const claims = [...state.items.flatMap(item => item.targets.map(target => target.installDir)),
      ...(state.suites ?? []).flatMap(suite => suite.members.flatMap(member => member.installDirs))]
    for (const claim of claims) {
      if (pathsOverlap(await canonicalizeExistingPath(claim), destination)) throw new Error('Import conflicts with a Registry installation record or local installation record')
    }
  }
  const runtime: NativeRuntime = {
    hostInstanceId: operations.hostInstanceId,
    previewPackage,
    exportPackage: (selected, target) => exportPackage(storeFor(selected.registry), selected, target),
    executePackageImport: async request => {
      if (request.registry !== LOCAL_PACKAGE_REGISTRY || request.namespace !== 'local' || request.version !== 'local') throw new Error('Invalid import identity')
      await ensureCompatible()
      await imports.ensureWritable()
      const authorize = guardForeignClaims(request, inventory)
      return withOperationLease(operations, request.operationId, request.ownerId, () => executeNativeInstall(operations, imports, {...request, authorize}))
    },
    inspectChanges: (selected, target) => inspectChanges(inventory, selected, target),
    readChangeDiff: (snapshot, path) => readChangeDiff(options.stateDirectory, inventory, snapshot, path),
    prepareVersionChanges: (selected, target, candidate) => prepareVersionChanges(options.stateDirectory, inventory, selected, target, candidate),
    backupChanges: (snapshot, authorize) => backupChanges(options.stateDirectory, inventory, snapshot, authorize),
    acceptFileReset: async (request, validate) => {
      return resets.accept(request, async () => {
        await ensureCompatible()
        if ((await operations.listForRecovery()).length) throw new Error('Operation recovery required')
        return validate()
      })
    },
    executeFileReset: request => resets.execute(request),
    readInstallationEntry: async (selected, target) => {
      const baseline = await inspectUpgradeBaseline(storeFor(selected.registry), { ...selected }, [{ ...target }], selected.registry === LOCAL_PACKAGE_REGISTRY)
      if (baseline.status !== 'ready' || baseline.targets.length !== 1) return undefined
      const entry = baseline.inventoryItem.targets[0]!
      const content = await readFile(join(entry.installDir, entry.installationState === 'disabled' ? 'SKILL.md.disabled' : 'SKILL.md'))
      if (createHash('sha256').update(content).digest('hex') !== baseline.targets[0]!.files['SKILL.md']
        || !isDeepStrictEqual(baseline, await inspectUpgradeBaseline(storeFor(selected.registry), { ...selected }, [{ ...target }], selected.registry === LOCAL_PACKAGE_REGISTRY))) return undefined
      return content.toString('utf8')
    },
    inspectToggle: (selected, target, desiredState) => inspectToggle(storeFor(selected.registry), { ...selected }, { ...target }, desiredState),
    acceptToggle: async (request, validateNew) => {
      const result = await operations.accept({ hostInstanceId: request.hostInstanceId, ownerId: request.ownerId,
        requestKey: request.requestKey, mode: request.mode, requestDigest: operationRequestDigest({ ...request.parameters, kind: 'toggle' }) },
      async () => {
        await ensureCompatible()
        const input = await validateNew()
        if (!['enable', 'disable'].includes(input.kind)) throw new Error('Expected toggle acceptance')
        return input
      })
      return result ? projectOperation(result) : undefined
    },
    executeToggle: async request => {
      await ensureCompatible()
      const snapshot = { ...request, baseline: structuredClone(request.baseline) }
      return withOperationLease(operations, snapshot.operationId, snapshot.ownerId,
        () => executeNativeToggle(operations, storeFor(snapshot.baseline.inventoryItem.registry), snapshot))
    },
    readSkillExamples,
    observeInstallation: async (operationId, ownerId, trustedRoots, observe) => {
      const record = await operations.get(operationId, ownerId)
      return observeInstallationDiscovery({operations, inventory: storeFor(record?.operation.context.sourceIdentity ?? ''), operationId, ownerId, trustedRoots, observe})
    },
    findPlanOperation: async request => {
      const reset = (await resets.list()).find(record => record.ownerId === request.ownerId && record.requestKey === request.requestKey)
      if (reset) {
        if (reset.requestDigest !== operationRequestDigest({ ...request.parameters, kind: 'reset-files' })) throw new OperationStoreError('REQUEST_KEY_CONFLICT')
        return reset.operation
      }
      let conflict: OperationStoreError | undefined
      // Preserve existing per-kind digests, including operations accepted by earlier plugin builds.
      for (const kind of ['upgrade', 'removal', 'toggle'] as const) {
        try {
          const result = await operations.accept({ hostInstanceId: request.hostInstanceId, ownerId: request.ownerId,
            requestKey: request.requestKey, mode: 'retry',
            requestDigest: operationRequestDigest({ ...request.parameters, kind }) },
          async () => { throw new Error('Plan lookup cannot accept new work') })
          if (result) {
            if (kind === 'upgrade' ? result.operation.kind !== 'upgrade'
              : kind === 'toggle' ? !['enable', 'disable'].includes(result.operation.kind) : !['uninstall', 'remove-stale-record'].includes(result.operation.kind)) throw new Error('Plan operation kind mismatch')
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
    executeRemoval: async request => {
      await ensureCompatible()
      const snapshot = { ...request, baseline: structuredClone(request.baseline) }
      return withOperationLease(operations, snapshot.operationId, snapshot.ownerId,
        () => executeNativeRemoval(operations, storeFor(snapshot.baseline.inventoryItem.registry), snapshot))
    },
    acceptRemoval: async (request, validateNew) => {
      const result = await operations.accept({ hostInstanceId: request.hostInstanceId, ownerId: request.ownerId,
        requestKey: request.requestKey, mode: request.mode, requestDigest: operationRequestDigest({ ...request.parameters, kind: 'removal' }) },
      async () => {
        await ensureCompatible()
        const input = await validateNew()
        if (!['uninstall', 'remove-stale-record'].includes(input.kind)) throw new Error('Expected removal acceptance')
        return input
      })
      return result ? projectOperation(result) : undefined
    },
    inspectRemoval: (selected, target) => inspectRemovalBaseline(storeFor(selected.registry), { ...selected }, { ...target }),
    executeUpgrade: async request => {
      if (request.baseline.inventoryItem.registry === LOCAL_PACKAGE_REGISTRY) throw new Error('Local packages cannot be upgraded')
      await ensureCompatible()
      const snapshot = { ...request, baseline: structuredClone(request.baseline), ...(request.localChangesAuthorization ? { localChangesAuthorization: structuredClone(request.localChangesAuthorization) } : {}) }
      return withOperationLease(operations, snapshot.operationId, snapshot.ownerId,
        () => executeNativeUpgrade(operations, inventory, snapshot))
    },
    acceptUpgrade: async (request, validateNew) => {
      const result = await operations.accept({ hostInstanceId: request.hostInstanceId, ownerId: request.ownerId,
        requestKey: request.requestKey, mode: request.mode, requestDigest: operationRequestDigest({ ...request.parameters, kind: 'upgrade' }) },
      async () => { await ensureCompatible(); return { ...await validateNew(), kind: 'upgrade' } })
      return result ? projectOperation(result) : undefined
    },
    assessUpdate: assessSkillUpdate,
    inspectUpgrade: (selected, targets) => inspectUpgradeBaseline(storeFor(selected.registry), { ...selected }, targets.map(target => ({ ...target }))),
    openInstalledSnapshot: (registry, targets) => openInstalledProjectionSnapshot(inventory, registry, targets),
    listAllInstalled: async targets => {
      const results = await Promise.all([inventory, imports].map(store => readAllInstalledProjection(store, targets.map(target => ({ ...target })))))
      return {items: results.flatMap(result => result.items), complete: results.every(result => result.complete), issues: [...new Set(results.flatMap(result => result.issues))]}
    },
    listInstalled: (registry, targets) => readInstalledProjection(inventory, registry, targets.map(target => ({ ...target }))),
    executeInstall: async request => {
      await ensureCompatible()
      const snapshot = { ...request }
      return withOperationLease(operations, snapshot.operationId, snapshot.ownerId,
        () => executeNativeInstall(operations, inventory, {...snapshot, authorize: guardForeignClaims(snapshot, imports)}))
    },
    acceptInstall: async (request, validateNew) => {
      const result = await operations.accept({ hostInstanceId: request.hostInstanceId, ownerId: request.ownerId,
        requestKey: request.requestKey, mode: request.mode, requestDigest: operationRequestDigest(request.parameters) },
      async () => { await ensureCompatible(); return { ...await validateNew(), kind: 'install' } })
      return result ? projectOperation(result) : undefined
    },
    getOperation: async (operationId, ownerId) => {
      const reset = await resets.get(operationId, ownerId)
      if (reset) return reset.operation
      const result = await operations.get(operationId, ownerId)
      return result ? projectOperation(result) : undefined
    },
    listPending: async () => [...(await resets.list()).filter(record => !['completed', 'failed'].includes(record.operation.phase)).map(record => ({ operationId: record.operationId, ownerId: record.ownerId, kind: record.operation.kind, namespace: record.operation.namespace, slug: record.operation.slug, targetPaths: record.targetPaths })), ...(await operations.listForRecovery()).map(record => ({
      operationId: record.operationId, ownerId: record.ownerId, kind: record.operation.kind,
      namespace: record.operation.namespace, slug: record.operation.slug, targetPaths: [...record.operation.targetPaths ?? []]
    }))],
    recover: async (operationId, ownerId, trustedTargetPaths, authorize) => {
      if (typeof authorize !== 'function') throw new Error('Native recovery requires Host authorization')
      if (await resets.get(operationId, ownerId)) return resets.recover(operationId, ownerId, trustedTargetPaths, authorize)
      const paths = [...trustedTargetPaths]
      const result = await withOperationLease(operations, operationId, ownerId, async () => {
        const record = await operations.get(operationId, ownerId)
        const selectedStore = storeFor(record?.operation.context.sourceIdentity ?? '')
        const input = { operations, inventory: selectedStore, operationId, ownerId, trustedTargetPaths: paths, authorizeMutation: authorize }
        if (record && ['enable', 'disable'].includes(record.operation.kind)) return recoverToggle(operations, selectedStore, operationId, ownerId, paths, authorize)
        return record && ['uninstall', 'remove-stale-record'].includes(record.operation.kind)
          ? recoverRemoval(input) : recoverInstallation(input)
      })
      return { operationId: result.operationId, revision: result.revision, outcome: result.outcome, reason: result.reason }
    }
  }
  const wrap = <T extends import('./native-contract').NativeInstallAcceptance>(accept: (
    request: import('./native-contract').NativeOperationRequest, validate: () => Promise<T>
  ) => Promise<import('./native-contract').NativeOperation | undefined>) => async (
    request: import('./native-contract').NativeOperationRequest, validate: () => Promise<T>
  ) => {
    const release = await acquireAdmission(options.stateDirectory)
    try { return await accept(request, validate) } finally { await release() }
  }
  runtime.acceptFileReset = wrap(runtime.acceptFileReset)
  runtime.acceptToggle = wrap(runtime.acceptToggle)
  runtime.acceptRemoval = wrap(runtime.acceptRemoval)
  runtime.acceptUpgrade = wrap(runtime.acceptUpgrade)
  runtime.acceptInstall = wrap(runtime.acceptInstall)
  return runtime
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

/** Admission is short-lived; independent target acceptances queue rather than spuriously failing. */
async function acquireAdmission(stateDirectory: string): Promise<() => Promise<void>> {
  const deadline = Date.now() + 10000
  for (;;) {
    try { return await acquireSkillTargetLock(stateDirectory, 'native-admission') }
    catch (error) {
      if (!(error instanceof Error) || !('details' in error)
        || (error.details as {reason?: string})?.reason !== 'TARGET_BUSY' || Date.now() >= deadline) throw error
      await new Promise(resolve => setTimeout(resolve, 20))
    }
  }
}
