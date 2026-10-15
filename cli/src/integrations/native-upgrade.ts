import { isDeepStrictEqual } from 'node:util'
import { SkillHubClient } from '../clients/skillhub-client'
import { installSkill } from '../services/install-service'
import { readInstalledSkillMetadata, sameInstalledSkillSource } from '../services/installed-skill-metadata'
import type { InventoryStore } from '../stores/inventory-store'
import type { NativeUpgradeExecution, NativeRecoveryResult } from './native-contract'
import type { OperationStore } from './operation-store'
import { inspectUpgradeBaseline } from './upgrade-baseline'
import { assessSkillUpdate } from './update-assessment'
import { createInstallTransactionObserver } from './transaction-journal'
import { reconcileCommittedInstallation, settleUnpreparedInstallationFailure } from './install-recovery'
import { assertOwnedRegularTree } from './recovery-files'

/** Execute only the complete confirmed snapshot; the shared core owns target locks and inventory CAS. */
export async function executeNativeUpgrade(operations: OperationStore, inventory: InventoryStore,
  input: NativeUpgradeExecution): Promise<NativeRecoveryResult> {
  const request = { ...input, baseline: structuredClone(input.baseline) }
  const { baseline } = request
  const record = await operations.get(request.operationId, request.ownerId)
  if (!record || record.operation.kind !== 'upgrade' || record.operation.status.phase !== 'accepted') throw new Error('Upgrade is not owned and accepted')
  const operation = record.operation
  const paths = baseline.targets.map(target => target.path)
  if (!paths.length || !isDeepStrictEqual([...paths].sort(), [...operation.targetPaths ?? []].sort())
    || operation.context.sourceIdentity !== request.registry || operation.namespace !== request.namespace
    || operation.slug !== request.slug || operation.version !== request.version
    || !sameInstalledSkillSource(baseline.inventoryItem, request) || !Number.isInteger(request.versionId) || request.versionId <= 0
    || assessSkillUpdate(baseline.inventoryItem, request).state !== 'upgradable') throw new Error('Upgrade snapshot changed')
  await operations.update(record.operationId, record.ownerId, record.revision, { ...operation.status, phase: 'staging' })
  let coreStarted = false
  const verifyBaseline = async () => {
    for (const target of baseline.targets) await request.authorize([target.root, target.path], 'staging')
    await operations.assertTargetsAvailable(paths, request)
    const current = await inspectUpgradeBaseline(inventory, baseline.inventoryItem, baseline.targets)
    if (!isDeepStrictEqual(current, baseline)) throw new Error('Upgrade baseline changed')
  }
  try {
    await verifyBaseline()
    const client = new SkillHubClient(request.registry)
    const resolved = await client.resolve(request.namespace, request.slug, request.version)
    if (resolved.namespace !== request.namespace || resolved.slug !== request.slug || resolved.version !== request.version
      || resolved.versionId !== request.versionId || resolved.fingerprint !== request.fingerprint) throw new Error('Upgrade release changed')
    const journal = await createInstallTransactionObserver(operations, record.operationId, record.ownerId)
    coreStarted = true
    await installSkill({ registry: request.registry, namespace: request.namespace, slug: request.slug,
      version: request.version, resolved, client, force: true, allowTargetDrift: false, requireExistingTargets: true,
      inventoryStore: inventory, expectedInventoryItem: baseline.inventoryItem,
      expectedTargetFiles: Object.fromEntries(baseline.targets.map(target => [target.path, target.files])),
      targets: baseline.targets.map(target => ({ agent: 'dsh', rootDir: target.root, scope: target.scope, source: 'explicit' })),
      authorizeTargetMutation: request.authorize, validateStagedSkill: request.validateStaged,
      transactionObserver: { ...journal,
        beforeTargetMutation: async targets => { await journal.beforeTargetMutation?.(targets); await verifyBaseline() },
        afterMove: async move => {
          await journal.afterMove?.(move)
          if (move.phase !== 'backup') return
          const expected = baseline.targets.find(target => target.path === move.canonicalSkillDir)
          await assertOwnedRegularTree(move.to)
          const metadata = await readInstalledSkillMetadata(move.to)
          if (!expected || metadata.status !== 'valid' || !isDeepStrictEqual(metadata.metadata, expected.metadata)) {
            throw new Error('Upgrade metadata changed')
          }
        }
      } })
    const result = await reconcileCommittedInstallation({ operations, inventory,
      operationId: record.operationId, ownerId: record.ownerId, trustedTargetPaths: paths })
    return { operationId: result.operationId, revision: result.revision, outcome: result.outcome, reason: result.reason }
  } catch (error) {
    if (coreStarted) {
      // Reuse the locked, read-only proof that cleanup left no staging and no target move began.
      try {
        await settleUnpreparedInstallationFailure({ operations, inventory,
          operationId: record.operationId, ownerId: record.ownerId, trustedTargetPaths: paths })
      } catch { /* Unverified cleanup remains recovery-required below. */ }
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
