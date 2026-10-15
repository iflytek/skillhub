import { realpath } from 'node:fs/promises'
import { isAbsolute, join, resolve } from 'node:path'
import { isDeepStrictEqual } from 'node:util'
import { canonicalizeExistingPath } from '../platform/paths'
import { readInstalledSkillMetadata, sameInstalledSkillSource } from '../services/installed-skill-metadata'
import { snapshotSkillDirectory } from '../services/skill-fingerprint'
import { targetInstalledBy } from '../stores/inventory-store'
import type { InventoryItem, InventoryStore } from '../stores/inventory-store'
import type { InstalledProjectionTarget, UpgradeBaseline } from './native-contract'
export type { UpgradeBaseline } from './native-contract'
import { assertOwnedRegularTree } from './recovery-files'

/** Read-only preparation. Execution must compare this complete snapshot again under mutation locks. */
export async function inspectUpgradeBaseline(store: Pick<InventoryStore, 'read'>,
  selected: Pick<InventoryItem, 'registry' | 'namespace' | 'slug' | 'version'>,
  approvedRoots: readonly InstalledProjectionTarget[]): Promise<UpgradeBaseline> {
  const inventory = structuredClone(await store.read())
  const matches = inventory.items.filter(item => sameInstalledSkillSource(item, selected))
  const item = matches[0]
  if (matches.length !== 1 || !item || item.version !== selected.version || !item.targets.length
    || !/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/.test(item.slug)) return { status: 'blocked', reason: 'record-changed' }
  const targets: Extract<UpgradeBaseline, { status: 'ready' }>['targets'] = []
  const paths = new Set<string>()
  // Check the entire shared target set before reading any skill content.
  for (const target of item.targets) {
    const roots = approvedRoots.filter(root => isAbsolute(root.root) && root.root === resolve(root.root)
      && root.root === target.rootDir)
    const root = roots[0]
    if (target.agent !== 'dsh' || !root || !isAbsolute(target.installDir)
      || target.installDir !== join(root.root, item.slug)) return { status: 'blocked', reason: 'target-outside-context' }
    if (roots.length !== 1 || paths.has(target.installDir)) return { status: 'blocked', reason: 'target-alias' }
    const owners = targetInstalledBy(item, target)
    if (owners.length !== 1 || owners[0] !== 'direct') return { status: 'blocked', reason: 'shared-target' }
    paths.add(target.installDir)
  }
  for (const other of inventory.items) {
    if (other === item) continue
    for (const target of other.targets) {
      if (paths.has(await canonicalizeExistingPath(target.installDir))) return { status: 'blocked', reason: 'shared-target' }
    }
  }
  for (const target of item.targets) {
    try {
      if (await realpath(target.rootDir) !== target.rootDir || await realpath(target.installDir) !== target.installDir) {
        return { status: 'blocked', reason: 'target-alias' }
      }
      await assertOwnedRegularTree(target.installDir)
      const read = await readInstalledSkillMetadata(target.installDir)
      if (read.status !== 'valid') return { status: 'blocked', reason: 'metadata-invalid' }
      const metadata = read.metadata
      if (!metadata.files || !item.fingerprint || !sameInstalledSkillSource(metadata, item)
        || metadata.version !== item.version || metadata.fingerprint !== item.fingerprint
        || metadata.agent !== 'dsh' || metadata.installedAt !== target.installedAt) {
        return { status: 'blocked', reason: 'metadata-invalid' }
      }
      const snapshot = await snapshotSkillDirectory(target.installDir)
      if (snapshot.fingerprint !== item.fingerprint || !isDeepStrictEqual(snapshot.files, metadata.files)) {
        return { status: 'blocked', reason: 'local-changes' }
      }
      targets.push({ scope: approvedRoots.find(root => root.root === target.rootDir)!.scope,
        root: target.rootDir, path: target.installDir, metadata, files: snapshot.files })
    } catch { return { status: 'blocked', reason: 'target-unavailable' } }
  }
  const latest = (await store.read()).items.filter(entry => sameInstalledSkillSource(entry, selected)
    || entry.targets.some(target => paths.has(target.installDir)))
  if (!isDeepStrictEqual(latest, [item])) return { status: 'blocked', reason: 'record-changed' }
  return { status: 'ready', inventoryItem: item, targets }
}
