import { createHash } from 'node:crypto'
import { lstat } from 'node:fs/promises'
import { join } from 'node:path'
import { isDeepStrictEqual } from 'node:util'
import { canonicalizeExistingPath } from '../platform/paths'
import { readInstalledSkillMetadata, sameInstalledSkillSource } from '../services/installed-skill-metadata'
import { snapshotSkillDirectory } from '../services/skill-fingerprint'
import { acquireSkillTargetLock } from '../services/skill-target-lock'
import { captureInstallInventory } from '../services/inventory-cas'
import { targetInstalledBy, type Inventory, type InventoryStore } from '../stores/inventory-store'
import type { NativeInstallExecution, NativeRecoveryResult } from './native-contract'
import type { OperationStore } from './operation-store'
import { assertOwnedRegularTree } from './recovery-files'

/** No network: verify the complete existing installation while holding target and inventory locks. */
export async function confirmExistingNativeInstall(operations: OperationStore, inventory: InventoryStore,
  request: NativeInstallExecution): Promise<NativeRecoveryResult | undefined> {
  const target = join(request.root, request.slug)
  const release = await acquireSkillTargetLock(request.root, request.slug)
  try {
    await request.authorize([request.root, target], 'staging')
    await operations.assertTargetsAvailable([target], request)
    try { await lstat(target) }
    catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return undefined; throw error }
    return await inventory.withLockedSnapshot(async current => {
      const snapshot = await verifyExistingInstallation(current, request)
      await request.validateStaged(target)
      const record = await operations.get(request.operationId, request.ownerId)
      if (!record || record.operation.status.phase !== 'staging') throw new Error('Installation claim changed')
      const checked = await operations.update(record.operationId, record.ownerId, record.revision, { ...record.operation.status, phase: 'revalidating' })
      // Validation may yield; retain only evidence that still matches immediately before completion.
      if (!isDeepStrictEqual(snapshot, await verifyExistingInstallation(await inventory.read(), request))) throw new Error('Existing installation changed during validation')
      const completed = await operations.completeExistingInstallation(checked.operationId, checked.ownerId, checked.revision, snapshot)
      return { operationId: completed.operationId, revision: completed.revision, outcome: 'committed', reason: 'already-installed' }
    })
  } finally { await release() }
}

/** Read-only exact baseline, called with target and inventory locks held. */
export async function verifyExistingInstallation(current: Inventory,
  request: Pick<NativeInstallExecution, 'root' | 'registry' | 'namespace' | 'slug' | 'version' | 'versionId' | 'fingerprint'>) {
  const target = join(request.root, request.slug)
  if (await canonicalizeExistingPath(request.root) !== request.root) throw new Error('Installation root changed')
  await assertOwnedRegularTree(target)
  const owners = []
  for (const item of current.items) {
    for (const entry of item.targets) {
      if (await canonicalizeExistingPath(entry.installDir) === target) owners.push({ item, entry })
    }
  }
  const owner = owners[0]
  if (owners.length !== 1 || !owner || !sameInstalledSkillSource(owner.item, request)
    || owner.entry.version !== request.version || owner.entry.fingerprint !== request.fingerprint
    || owner.entry.agent !== 'dsh' || !targetInstalledBy(owner.item, owner.entry).includes('direct')
    || await canonicalizeExistingPath(owner.entry.rootDir) !== request.root) throw new Error('Existing installation conflicts with the requested skill')
  const metadata = await readInstalledSkillMetadata(target)
  if (metadata.status !== 'valid' || !sameInstalledSkillSource(metadata.metadata, request)
    || metadata.metadata.version !== request.version || metadata.metadata.versionId !== request.versionId
    || metadata.metadata.fingerprint !== request.fingerprint || metadata.metadata.agent !== 'dsh'
    || metadata.metadata.installedAt !== owner.entry.installedAt || metadata.metadata.source !== 'skillhub') throw new Error('Existing installation metadata does not match')
  const snapshot = await snapshotSkillDirectory(target)
  if (snapshot.fingerprint !== request.fingerprint || !isDeepStrictEqual(snapshot.files, metadata.metadata.files)) throw new Error('Existing installation has local changes')
  return { fingerprint: request.fingerprint, versionId: request.versionId,
    inventoryDigest: createHash('sha256').update(await captureInstallInventory(current, request, new Set([target]))).digest('hex') }
}
