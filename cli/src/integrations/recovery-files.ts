import { lstat, readdir, realpath } from 'node:fs/promises'
import { basename, dirname, isAbsolute, join, resolve } from 'node:path'
import type { OperationRecord } from './operation-store'
import type { InstallMove } from '../services/install-transaction'

export async function verifiedBackupPath(record: OperationRecord, move: InstallMove): Promise<string> {
  const root = dirname(move.canonicalSkillDir)
  const name = basename(move.to)
  const suffix = name.slice(`${record.operation.slug}.skillhub-backup-`.length)
  if (!record.operation.targetPaths?.includes(move.canonicalSkillDir) || move.backupDir !== move.to
    || !isAbsolute(move.to) || resolve(move.to) !== move.to || !isAbsolute(move.from)
    || basename(move.from) !== record.operation.slug || dirname(move.from) !== dirname(move.to)
    || !name.startsWith(`${record.operation.slug}.skillhub-backup-`)
    || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(suffix)
    || await realpath(dirname(move.to)) !== root) throw new Error('Backup path does not match the recorded target')
  return join(root, name)
}

export async function assertOwnedRegularTree(path: string): Promise<void> {
  await inspectTree(path, path)
}

async function inspectTree(path: string, root: string): Promise<void> {
  const info = await lstat(path)
  if (info.isSymbolicLink() || (!info.isDirectory() && !info.isFile())
    || (process.getuid && info.uid !== process.getuid())) throw new Error('Unsafe recovery path')
  if (!info.isDirectory()) return
  const names = await readdir(path)
  // The fingerprint deliberately excludes .skillhub; do not silently delete untracked content there.
  if (basename(path) === '.skillhub' && (path !== join(root, '.skillhub') || names.length !== 1 || names[0] !== 'metadata.json')) {
    throw new Error('Untracked recovery metadata content')
  }
  for (const name of names) await inspectTree(join(path, name), root)
}
