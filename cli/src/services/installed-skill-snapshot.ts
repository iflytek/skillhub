import type { OperationRecord } from '../integrations/operation-store'
import type { InstallPreparation, InstallMove } from './install-transaction'
import { createHash } from 'node:crypto'
import { lstat, realpath } from 'node:fs/promises'
import { basename, dirname, join } from 'node:path'
import { isDeepStrictEqual } from 'node:util'
import type { InventoryItem, InventoryTarget } from '../stores/inventory-store'
import { readInstalledSkillMetadata, sameInstalledSkillSource } from './installed-skill-metadata'
import { snapshotSkillDirectory, type SkillSnapshot } from './skill-fingerprint'
import { assertOwnedRegularTree } from '../integrations/recovery-files'

export async function assertEntryAbsent(path: string): Promise<void> {
  try { await lstat(path) } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'ENOENT') return
    throw error
  }
  throw new Error('Skill entry destination is occupied')
}

export async function snapshotInstalledSkillDirectory(item: InventoryItem, target: InventoryTarget): Promise<SkillSnapshot> {
  if (!item.targets.some(entry => isDeepStrictEqual(entry, target))
    || await realpath(target.rootDir) !== target.rootDir || await realpath(target.installDir) !== target.installDir
    || join(target.rootDir, item.slug) !== target.installDir) throw new Error('Installation identity changed')
  return snapshotBoundInstallation(target.installDir, item, target)
}

/** Internal lifecycle evidence: callers must bind this location to their persisted move intent before reading it. */
async function snapshotBoundInstallation(directory: string, item: InventoryItem, target: InventoryTarget, linkedTransition = false): Promise<SkillSnapshot> {
  await assertOwnedRegularTree(directory)
  const read = await readInstalledSkillMetadata(directory)
  if (read.status !== 'valid' || !sameInstalledSkillSource(read.metadata, item)
    || read.metadata.version !== target.version || read.metadata.agent !== target.agent
    || read.metadata.installedAt !== target.installedAt || read.metadata.fingerprint !== target.fingerprint
    || !read.metadata.files || !target.fingerprint) throw new Error('Installation metadata changed')
  const disabled = target.installationState === 'disabled'
  const entry = disabled ? 'SKILL.md.disabled' : 'SKILL.md'
  const info = await lstat(join(directory, entry))
  if (!info.isFile() || info.isSymbolicLink()) throw new Error('Unsafe skill entry')
  if (disabled && !linkedTransition) await assertEntryAbsent(join(directory, 'SKILL.md'))
  const snapshot = await snapshotSkillDirectory(directory)
  if (!disabled && !linkedTransition) return snapshot
  if ('SKILL.md.disabled' in read.metadata.files) throw new Error('Disabled entry collides with authored content')
  const files: Record<string, string> = { ...snapshot.files, 'SKILL.md': snapshot.files['SKILL.md.disabled']! }
  delete files['SKILL.md.disabled']
  const aggregate = createHash('sha256')
  for (const path of Object.keys(files).sort()) aggregate.update(`${path}:${files[path]}\n`, 'utf8')
  return { files, fingerprint: `sha256:${aggregate.digest('hex')}` }
}

/** Only persisted operation bindings may authorize nonstandard locations or transition states. */
export async function snapshotJournalInstallation(record: OperationRecord, directory: string,
  toggleState?: 'enabled' | 'disabled'): Promise<SkillSnapshot> {
  if (record.toggle) {
    const intent = record.toggle
    if (directory !== intent.target.path || !record.operation.targetPaths?.includes(directory)
      || !toggleState || ![intent.beforeState, intent.afterState].includes(toggleState)) throw new Error('Unbound toggle snapshot')
    let linked = false
    try {
      const [a, b] = await Promise.all([lstat(intent.from), lstat(intent.to)])
      linked = a.isFile() && b.isFile() && !a.isSymbolicLink() && !b.isSymbolicLink() && a.dev === b.dev && a.ino === b.ino
    } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error }
    if (linked && ['committed', 'restored'].includes(intent.stage)) throw new Error('Unexpected linked entry after completion')
    return snapshotBoundInstallation(directory, intent.inventoryItem,
      { ...intent.inventoryItem.targets[0]!, installationState: toggleState }, linked)
  }
  if (toggleState) throw new Error('Transition state requires toggle evidence')
  if (record.removal) {
    const intent = record.removal.intent
    if (![intent.target.path, intent.quarantine].includes(directory)
      || !record.operation.targetPaths?.includes(intent.target.path)) throw new Error('Unbound removal snapshot')
    const target = intent.inventoryItem.targets.find(target => target.installDir === intent.target.path)
    if (!target) throw new Error('Removal snapshot missing target')
    return snapshotBoundInstallation(directory, intent.inventoryItem, target)
  }
  const preparation = record.transaction?.preparation
  if (!preparation) throw new Error('Installation snapshot missing preparation')
  for (const staged of preparation.targets) {
    if (!record.operation.targetPaths?.includes(staged.canonicalSkillDir)) continue
    const old = preparation.before.items.flatMap(item => item.targets.map(target => ({ item, target })))
      .find(entry => entry.target.installDir === staged.skillDir)
    const backup = record.transaction!.moves.find(entry => entry.move.phase === 'backup' && entry.move.canonicalSkillDir === staged.canonicalSkillDir && entry.move.to === directory)
    if (backup) return snapshotPreparedBackup(preparation, backup.move)
    if (![staged.skillDir, staged.stagingDir].includes(directory)) continue
    const read = await readInstalledSkillMetadata(directory)
    if (read.status !== 'valid') throw new Error('Installation snapshot metadata unavailable')
    if (old && read.metadata.version === old.target.version && read.metadata.installedAt === old.target.installedAt) {
      return snapshotBoundInstallation(directory, old.item, old.target)
    }
    const target: InventoryTarget = { agent: staged.agent, rootDir: dirname(staged.skillDir), installDir: staged.skillDir,
      installedAt: staged.installedAt, version: preparation.release.version, fingerprint: preparation.release.fingerprint,
      installationState: old?.target.installationState ?? 'enabled' }
    const item = { ...preparation.release, targets: [target] }
    // A crash can precede the staged entry conversion; both states are authorized only in this exact staging directory.
    if (directory === staged.stagingDir && target.installationState === 'disabled') {
      try { return await snapshotBoundInstallation(directory, item, target) } catch {
        const enabled = await snapshotBoundInstallation(directory, item, { ...target, installationState: 'enabled' })
        if (enabled.files['SKILL.md.disabled']) throw new Error('Candidate entry collision')
        return enabled
      }
    }
    return snapshotBoundInstallation(directory, item, target)
  }
  throw new Error('Snapshot path is not bound to the operation')
}

/** Called only after the shared installer has awaited its durable preparation and move intent hooks. */
export async function snapshotPreparedBackup(preparation: InstallPreparation, move: InstallMove): Promise<SkillSnapshot> {
  const staged = preparation.targets[move.index]
  if (move.phase !== 'backup' || !staged || staged.skillDir !== move.from || staged.canonicalSkillDir !== move.canonicalSkillDir
    || staged.stagingDir !== move.stagingDir || move.backupDir !== move.to || dirname(move.to) !== dirname(move.from)
    || !basename(move.to).startsWith(`${basename(move.from)}.skillhub-backup-`)) throw new Error('Unbound backup snapshot')
  const matches = preparation.before.items.flatMap(item => item.targets.map(target => ({ item, target })))
    .filter(entry => entry.target.installDir === staged.skillDir && sameInstalledSkillSource(entry.item, preparation.release))
  if (matches.length !== 1) throw new Error('Backup snapshot has ambiguous ownership')
  return snapshotBoundInstallation(move.to, matches[0]!.item, matches[0]!.target)
}
