import { createHash, randomUUID } from 'node:crypto'
import { chmod, cp, lstat, mkdir, mkdtemp, open, readFile, readdir, realpath, rm, stat } from 'node:fs/promises'
import { dirname, isAbsolute, join } from 'node:path'
import { isDeepStrictEqual } from 'node:util'
import { SkillHubClient } from '../clients/skillhub-client'
import { extractZip } from '../platform/archive'
import { MAX_PACKAGE_BYTES, readBoundedResponseBody } from '../platform/download'
import { diffSkillFiles, snapshotSkillDirectory } from '../services/skill-fingerprint'
import { InventoryStore } from '../stores/inventory-store'
import { acquireSkillTargetLock } from '../services/skill-target-lock'
import { inspectUpgradeBaseline } from './upgrade-baseline'
import { assertOwnedRegularTree } from './recovery-files'
import type { BoundedFileDiff, ChangeFile, InstalledProjectionTarget, LocalChangesSnapshot, NativeRemovalExecution, UpgradeSelection, VerifiedBackupReceipt, VersionChangesSnapshot } from './native-contract'

export type ReadyChanges = Extract<LocalChangesSnapshot, {status: 'ready'}>
export const digest = (value: unknown): string => createHash('sha256').update(JSON.stringify(value)).digest('hex')
export function changeFiles(before: Record<string, string>, after: Record<string, string>): ChangeFile[] {
  return diffSkillFiles(before, after).map(path => ({ path, kind: !before[path] ? 'added' : !after[path] ? 'deleted' : 'modified' }))
}
export async function inspectChanges(inventory: InventoryStore, selected: UpgradeSelection, target: InstalledProjectionTarget): Promise<LocalChangesSnapshot> {
  const baseline = await inspectUpgradeBaseline(inventory, selected, [target], true)
  if (baseline.status !== 'ready') return baseline
  if (baseline.targets.length !== 1) return { status: 'blocked', reason: 'target-alias' }
  const entry = baseline.inventoryItem.targets[0]!
  try { await treeBytes(entry.installDir) } catch { return { status: 'blocked', reason: 'target-unavailable' } }
  if (!baseline.targets[0]!.files['SKILL.md']) return { status: 'blocked', reason: 'entry-conflict' }
  const value = { baseline, selection: { ...selected }, target: { ...target }, installationState: entry.installationState ?? 'enabled',
    changes: changeFiles(baseline.targets[0]!.metadata.files!, baseline.targets[0]!.files) }
  return { status: 'ready', kind: 'local', ...value, digest: digest(value) }
}
export async function assertChanges(inventory: InventoryStore, snapshot: ReadyChanges): Promise<void> {
  const current = await inspectChanges(inventory, snapshot.selection, snapshot.target)
  if (!isDeepStrictEqual(current, snapshot)) throw new Error('Local changes snapshot is stale')
}
export function physicalPath(snapshot: ReadyChanges, path: string): string {
  if (!path || isAbsolute(path) || path.includes('\\') || path.split('/').some(part => !part || part === '.' || part === '..')
    || path === '.skillhub/metadata.json' || path === 'SKILL.md.disabled') throw new Error('Invalid change path')
  return path === 'SKILL.md' && snapshot.installationState === 'disabled' ? 'SKILL.md.disabled' : path
}
export async function privateDirectory(path: string): Promise<void> {
  await mkdir(path, { recursive: true, mode: 0o700 })
  if (await realpath(path) !== path) throw new Error('Private directory redirects')
  const info = await lstat(path)
  if (!info.isDirectory() || info.isSymbolicLink() || (process.getuid && info.uid !== process.getuid())) throw new Error('Unsafe private directory')
  if (process.platform !== 'win32') await chmod(path, 0o700)
}
export async function durableJson(path: string, value: unknown): Promise<void> {
  const temporary = `${path}.${randomUUID()}.tmp`
  const file = await open(temporary, 'wx', 0o600)
  try { await file.writeFile(JSON.stringify(value)); await file.sync() } finally { await file.close() }
  const { renameAtomicFile } = await import('../platform/atomic-rename')
  await renameAtomicFile(temporary, path)
  await syncDirectory(dirname(path))
}
export async function syncDirectory(path: string): Promise<void> {
  if (process.platform === 'win32') return
  const directory = await open(path, 'r'); try { await directory.sync() } finally { await directory.close() }
}
export async function syncTree(path: string): Promise<void> {
  for (const entry of await readdir(path, {withFileTypes:true})) {
    const child = join(path, entry.name)
    if (entry.isDirectory()) await syncTree(child)
    else {
      // Windows FlushFileBuffers needs write access; r+ preserves the copied bytes without truncation.
      const file = await open(child, process.platform === 'win32' ? 'r+' : 'r')
      try { await file.sync() } finally { await file.close() }
    }
  }
  await syncDirectory(path)
}
/** Exact release only; temporary content is never trusted without hashes and full release identity. */
export async function withRelease<T>(state: string, identity: UpgradeSelection & {versionId: number; fingerprint: string},
  run: (directory: string, files: Record<string, string>) => Promise<T>): Promise<T> {
  const parent = join(dirname(state), 'change-staging')
  await privateDirectory(parent)
  const directory = await mkdtemp(join(parent, 'release-'))
  try {
    const client = new SkillHubClient(identity.registry)
    const release = await client.resolve(identity.namespace, identity.slug, identity.version)
    if (release.namespace !== identity.namespace || release.slug !== identity.slug || release.version !== identity.version
      || release.versionId !== identity.versionId || release.fingerprint !== identity.fingerprint) throw new Error('Original release identity is unavailable')
    const response = await client.downloadFromUrl(release.downloadUrl)
    await extractZip(await readBoundedResponseBody(response), directory, { rejectInternalMetadata: true })
    const snapshot = await snapshotSkillDirectory(directory)
    if (snapshot.fingerprint !== identity.fingerprint) throw new Error('Release fingerprint mismatch')
    return await run(directory, snapshot.files)
  } finally { await rm(directory, { recursive: true, force: true }) }
}
export async function withOriginal<T>(state: string, snapshot: ReadyChanges, run: (directory: string) => Promise<T>): Promise<T> {
  const metadata = snapshot.baseline.targets[0]!.metadata
  if (!metadata.versionId || !metadata.fingerprint || !metadata.files) throw new Error('Original release baseline is unavailable')
  return withRelease(state, { ...snapshot.selection, versionId: metadata.versionId, fingerprint: metadata.fingerprint }, async (directory, files) => {
    if (!isDeepStrictEqual(files, metadata.files)) throw new Error('Original release file map mismatch')
    return run(directory)
  })
}
export function boundedDiff(path: string, before: Buffer | undefined, after: Buffer | undefined): BoundedFileDiff {
  const result: BoundedFileDiff = { path, state: 'text', lines: [], truncated: false }
  if ((before?.length ?? 0) > 256 * 1024 || (after?.length ?? 0) > 256 * 1024) return { ...result, state: 'too-large' }
  let a: string[], b: string[]
  try {
    if (before?.includes(0) || after?.includes(0)) return { ...result, state: 'binary' }
    const decoder = new TextDecoder('utf-8', { fatal: true })
    a = before === undefined ? [] : decoder.decode(before).split('\n')
    b = after === undefined ? [] : decoder.decode(after).split('\n')
  } catch { return { ...result, state: 'binary' } }
  // Linear common prefix/suffix; no quadratic LCS on untrusted authored files.
  let prefix = 0, suffix = 0
  while (prefix < a.length && prefix < b.length && a[prefix] === b[prefix]) prefix++
  while (suffix < a.length - prefix && suffix < b.length - prefix && a[a.length - 1 - suffix] === b[b.length - 1 - suffix]) suffix++
  const push = (kind: BoundedFileDiff['lines'][number]['kind'], text: string) => {
    if (result.lines.length < 2000) result.lines.push({ kind, text }); else result.truncated = true
  }
  for (const line of a.slice(Math.max(0, prefix - 3), prefix)) push('context', line)
  for (const line of a.slice(prefix, a.length - suffix)) push('deleted', line)
  for (const line of b.slice(prefix, b.length - suffix)) push('added', line)
  for (const line of a.slice(a.length - suffix, a.length - suffix + 3)) push('context', line)
  return result
}
async function readOptional(directory: string, path: string, files: Record<string, string>, logical = path): Promise<Buffer | undefined> {
  if (!files[logical]) return undefined
  const info = await stat(join(directory, path))
  if (info.size > 256 * 1024) return Buffer.alloc(256 * 1024 + 1)
  const content = await readFile(join(directory, path))
  if (createHash('sha256').update(content).digest('hex') !== files[logical]) throw new Error('Compared file changed')
  return content
}
export async function readChangeDiff(state: string, inventory: InventoryStore, snapshot: ReadyChanges | VersionChangesSnapshot, path: string): Promise<BoundedFileDiff> {
  const local = snapshot.kind === 'local' ? snapshot : snapshot.local
  const physical = physicalPath(local, path)
  if (!snapshot.changes.some(file => file.path === path)) throw new Error('Path is not in the change snapshot')
  await assertChanges(inventory, local)
  const result = await withOriginal(state, local, async directory => {
    const before = await readOptional(directory, path, local.baseline.targets[0]!.metadata.files!)
    if (snapshot.kind === 'local') return boundedDiff(path, before,
      await readOptional(local.baseline.targets[0]!.path, physical, local.baseline.targets[0]!.files, path))
    return withRelease(state, { ...local.selection, ...snapshot.candidate }, async (candidate, files) => {
      if (!isDeepStrictEqual(files, snapshot.files)) throw new Error('Candidate changed')
      return boundedDiff(path, before, await readOptional(candidate, path, files))
    })
  })
  await assertChanges(inventory, local)
  return result
}
export async function prepareVersionChanges(state: string, inventory: InventoryStore, selected: UpgradeSelection,
  target: InstalledProjectionTarget, candidate: VersionChangesSnapshot['candidate']): Promise<VersionChangesSnapshot> {
  const local = await inspectChanges(inventory, selected, target)
  if (local.status !== 'ready') throw new Error('Installation cannot be compared')
  const assessment = (await import('./update-assessment')).assessSkillUpdate({ version: selected.version,
    fingerprint: local.baseline.targets[0]!.metadata.fingerprint! }, candidate)
  if (assessment.state !== 'upgradable') throw new Error('Candidate is not a newer version')
  return withOriginal(state, local, () => withRelease(state, { ...selected, ...candidate }, async (_directory, files) => {
    await assertChanges(inventory, local)
    return { status: 'ready', kind: 'version', local, candidate: { ...candidate }, files,
      changes: changeFiles(local.baseline.targets[0]!.metadata.files!, files) }
  }))
}
export async function rawSnapshot(directory: string): Promise<{files: Record<string, string>; metadata: string}> {
  await assertOwnedRegularTree(directory)
  await treeBytes(directory)
  return { files: (await snapshotSkillDirectory(directory)).files, metadata: await readFile(join(directory, '.skillhub', 'metadata.json'), 'utf8') }
}
export async function treeBytes(directory: string): Promise<number> {
  let bytes = 0
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name), info = await lstat(path)
    if (info.isSymbolicLink() || (!info.isFile() && !info.isDirectory()) || (info.isFile() && info.nlink !== 1)) throw new Error('Unsafe backup content')
    bytes += info.isDirectory() ? await treeBytes(path) : info.size
    if (bytes > MAX_PACKAGE_BYTES * 11) throw new Error('Backup quota exceeded')
  }
  return bytes
}
export async function backupChanges(state: string, inventory: InventoryStore, snapshot: ReadyChanges,
  authorize: NativeRemovalExecution['authorize']): Promise<VerifiedBackupReceipt> {
  const parent = join(dirname(state), 'backups')
  await privateDirectory(parent)
  const quotaRelease = await acquireSkillTargetLock(parent, 'backup-quota')
  const targetRelease = await acquireSkillTargetLock(snapshot.target.root, snapshot.selection.slug)
  try {
    await assertChanges(inventory, snapshot)
    const id = randomUUID(), directory = join(parent, id), target = snapshot.baseline.targets[0]!.path
    await authorize([target, parent, directory], 'staging', id)
    const entries = await readdir(parent)
    const bytes = await treeBytes(target)
    if (entries.length >= 20 || bytes > MAX_PACKAGE_BYTES || await treeBytes(parent) + bytes > 1024 * 1024 * 1024) throw new Error('Backup quota exceeded')
    await mkdir(directory, { mode: 0o700 })
    await privateDirectory(directory)
    try {
      const before = await rawSnapshot(target)
      await cp(target, join(directory, 'files'), { recursive: true, errorOnExist: true, force: false, preserveTimestamps: true })
      if (!isDeepStrictEqual(before, await rawSnapshot(join(directory, 'files')))) throw new Error('Backup verification failed')
      await assertChanges(inventory, snapshot)
      if (await treeBytes(target) !== bytes || (await readdir(parent)).length > 20 || await treeBytes(parent) > 1024 * 1024 * 1024) throw new Error('Backup quota exceeded')
      await syncTree(join(directory, 'files'))
      const receipt = { id, snapshotDigest: snapshot.digest, rawDigest: digest(before), createdAt: new Date().toISOString() }
      await durableJson(join(directory, 'manifest.json'), { receipt, snapshot, raw: before, bytes })
      if (await treeBytes(parent) > 1024 * 1024 * 1024) throw new Error('Backup quota exceeded')
      await syncDirectory(parent)
      return receipt
    } catch (error) { await rm(directory, { recursive: true, force: true }); throw error }
  } finally { await targetRelease(); await quotaRelease() }
}
export async function verifyBackup(state: string, snapshot: ReadyChanges, receipt: VerifiedBackupReceipt): Promise<void> {
  if (!/^[0-9a-f-]{36}$/i.test(receipt.id) || receipt.snapshotDigest !== snapshot.digest) throw new Error('Invalid backup receipt')
  const directory = join(dirname(state), 'backups', receipt.id)
  if (await realpath(directory) !== directory) throw new Error('Backup redirects')
  const manifest = join(directory, 'manifest.json'), info = await lstat(manifest)
  if (!info.isFile() || info.isSymbolicLink() || info.nlink !== 1 || (process.getuid && info.uid !== process.getuid())) throw new Error('Unsafe backup manifest')
  const saved = JSON.parse(await readFile(manifest, 'utf8'))
  if (!isDeepStrictEqual(saved.receipt, receipt) || !isDeepStrictEqual(saved.snapshot, snapshot)
    || digest(saved.raw) !== receipt.rawDigest || !isDeepStrictEqual(await rawSnapshot(join(directory, 'files')), saved.raw)) throw new Error('Backup changed')
}
