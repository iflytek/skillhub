import { lstat } from 'node:fs/promises'
import { isAbsolute, join, resolve } from 'node:path'
import { isDeepStrictEqual } from 'node:util'
import { canonicalizeExistingPath, pathsOverlap } from '../platform/paths'
import { sameInstalledSkillSource } from '../services/installed-skill-metadata'
import { targetInstalledBy } from '../stores/inventory-store'
import type { InventoryStore } from '../stores/inventory-store'
import type { InstalledProjectionTarget, RemovalBaseline, UpgradeSelection } from './native-contract'
import { inspectUpgradeBaseline } from './upgrade-baseline'

/** Inspect exactly one Host-selected target; bind only that position for execution-time CAS. */
export async function inspectRemovalBaseline(store: Pick<InventoryStore, 'read'>,
  selected: UpgradeSelection, target: InstalledProjectionTarget): Promise<RemovalBaseline> {
  if (!isAbsolute(target.root) || resolve(target.root) !== target.root
    || !/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/.test(selected.slug)) return { status: 'blocked', reason: 'target-outside-context' }
  const inventory = structuredClone(await store.read())
  const matches = inventory.items.filter(item => sameInstalledSkillSource(item, selected))
  const item = matches[0]
  if (matches.length !== 1 || !item) return { status: 'blocked', reason: 'record-changed' }
  const path = join(target.root, selected.slug)
  const entries = item.targets.filter(entry => entry.installDir === path)
  const entry = entries[0]
  if (entries.length !== 1 || !entry || entry.version !== selected.version || entry.agent !== 'dsh' || entry.rootDir !== target.root) {
    return { status: 'blocked', reason: 'target-outside-context' }
  }
  const owners = targetInstalledBy(item, entry)
  if (owners.length !== 1 || owners[0] !== 'direct') return { status: 'blocked', reason: 'shared-target' }
  if (!entry.fingerprint) return { status: 'blocked', reason: 'metadata-invalid' }
  for (const suite of inventory.suites ?? []) for (const member of suite.members) {
    for (const claimed of member.installDirs) if (pathsOverlap(await canonicalizeExistingPath(claimed), path)) return { status: 'blocked', reason: 'shared-target' }
  }
  try {
    if (await canonicalizeExistingPath(target.root) !== target.root) return { status: 'blocked', reason: 'target-alias' }
    // Windows may report ENOENT for a child of a regular file, not ENOTDIR.
    if (!(await lstat(target.root)).isDirectory()) return { status: 'blocked', reason: 'target-unavailable' }
    for (const other of inventory.items) {
      for (const otherTarget of other.targets) {
        if (other === item && otherTarget === entry) continue
        if (pathsOverlap(await canonicalizeExistingPath(otherTarget.installDir), path)) return { status: 'blocked', reason: 'shared-target' }
      }
    }
  } catch { return { status: 'blocked', reason: 'target-unavailable' } }
  let missing = false
  try { await lstat(path) }
  catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'ENOENT') return { status: 'blocked', reason: 'target-unavailable' }
    missing = true
  }
  let result: RemovalBaseline
  if (missing) {
    result = { status: 'ready', kind: 'remove-stale-record', inventoryItem: { ...item, targets: [entry] }, target: { ...target, path } }
  } else {
    // Reuse the same metadata, regular-tree and fingerprint checks, but restrict inspection
    // to this exact target. Unselected positions are preserved by the inventory reducer.
    const inspected = await inspectUpgradeBaseline({ read: async () => ({ ...inventory,
      items: inventory.items.map(record => record === item ? { ...record, targets: [entry] } : record) }) }, selected, [target])
    if (inspected.status === 'blocked') return inspected
    result = { status: 'ready', kind: 'uninstall', inventoryItem: { ...item, targets: [entry] }, target: inspected.targets[0]! }
  }
  const latest = await store.read()
  const current = latest.items.filter(record => sameInstalledSkillSource(record, selected))
  if (!isDeepStrictEqual(current.map(record => ({ ...record, targets: record.targets.filter(value => value.installDir === path) })), [{ ...item, targets: [entry] }])) return { status: 'blocked', reason: 'record-changed' }
  for (const record of latest.items) {
    for (const targetEntry of record.targets) {
      if (sameInstalledSkillSource(record, selected) && targetEntry.installDir === path) continue
      if (pathsOverlap(await canonicalizeExistingPath(targetEntry.installDir), path)) return { status: 'blocked', reason: 'shared-target' }
    }
  }
  if (missing) {
    // Inventory reads can yield while another installer recreates the directory.
    // Never offer stale-record cleanup after observing that it exists again.
    try {
      await lstat(path)
      return { status: 'blocked', reason: 'record-changed' }
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT') return { status: 'blocked', reason: 'target-unavailable' }
    }
  }
  return result
}
