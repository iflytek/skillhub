import { createHash } from 'node:crypto'
import { constants } from 'node:fs'
import { mkdtemp, rm, lstat, open, realpath, readdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { zipSync } from 'fflate'
import { extractZip, MAX_SINGLE_FILE_BYTES, MAX_ZIP_ENTRIES } from '../platform/archive'
import { MAX_PACKAGE_BYTES } from '../platform/download'
import { snapshotSkillDirectory } from '../services/skill-fingerprint'
import { snapshotInstalledSkillDirectory } from '../services/installed-skill-snapshot'
import { inspectUpgradeBaseline } from './upgrade-baseline'
import { acquireSkillTargetLock } from '../services/skill-target-lock'
import type { InventoryStore } from '../stores/inventory-store'
import type { UpgradeSelection, InstalledProjectionTarget } from './native-contract'

export const LOCAL_PACKAGE_REGISTRY = 'local:import'
export const MAX_TRANSFER_BYTES = 8 * 1024 * 1024
export interface PackageInspection { name: string; summary?: string; files: number; bytes: number; fingerprint: string }
export async function previewPackage(archive: Uint8Array, validate: (directory: string) => Promise<{name: string; summary?: string}>): Promise<PackageInspection> {
  if (!archive.byteLength || archive.byteLength > MAX_TRANSFER_BYTES) throw new Error('Package exceeds transfer size limit')
  const directory = await mkdtemp(join(tmpdir(), 'skillhub-package-'))
  try {
    await extractZip(archive.slice().buffer, directory, { rejectInternalMetadata: true })
    const entry = await lstat(join(directory, 'SKILL.md'))
    if (!entry.isFile()) throw new Error('Package requires root SKILL.md')
    try { await lstat(join(directory, 'SKILL.md.disabled')); throw new Error('Ambiguous package entry') }
    catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error }
    const declaration = await validate(directory)
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(declaration.name) || declaration.name.length > 128) throw new Error('Invalid skill name')
    const snapshot = await snapshotSkillDirectory(directory)
    let bytes = 0
    for (const path of Object.keys(snapshot.files)) bytes += (await lstat(join(directory, path))).size
    return { ...declaration, files: Object.keys(snapshot.files).length, bytes, fingerprint: snapshot.fingerprint }
  } finally { await rm(directory, { recursive: true, force: true }) }
}

export async function exportPackage(inventory: InventoryStore, selected: UpgradeSelection, target: InstalledProjectionTarget): Promise<{filename: string; archive: Uint8Array}> {
  const release = await acquireSkillTargetLock(target.root, selected.slug)
  try {
    await assertBoundedTree(join(target.root, selected.slug))
    const baseline = await inspectUpgradeBaseline(inventory, selected, [target], true)
    if (baseline.status !== 'ready' || baseline.inventoryItem.targets.length !== 1) throw new Error('Installation cannot be exported')
    const entry = baseline.inventoryItem.targets[0]!
    const before = await snapshotInstalledSkillDirectory(baseline.inventoryItem, entry)
    const paths = Object.keys(before.files)
    if (paths.length > MAX_ZIP_ENTRIES) throw new Error('Too many package files')
    const files: Record<string, Uint8Array> = {}
    let total = 0
    for (const path of paths) {
      const source = path === 'SKILL.md' && entry.installationState === 'disabled' ? 'SKILL.md.disabled' : path
      const full = join(entry.installDir, source)
      if (await realpath(full) !== full) throw new Error('Unsafe package file')
      const stat = await lstat(full)
      if (!stat.isFile() || stat.isSymbolicLink() || stat.size > MAX_SINGLE_FILE_BYTES) throw new Error('Unsafe package file')
      total += stat.size
      if (total > MAX_PACKAGE_BYTES) throw new Error('Package exceeds expanded size limit')
      const handle = await open(full, constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0))
      try {
        const opened = await handle.stat()
        if (opened.ino !== stat.ino || opened.dev !== stat.dev || !opened.isFile()) throw new Error('Package changed')
        const data = new Uint8Array(stat.size)
        let offset = 0
        while (offset < data.length) {
          const chunk = await handle.read(data, offset, data.length - offset, offset)
          if (!chunk.bytesRead) throw new Error('Package changed')
          offset += chunk.bytesRead
        }
        const afterRead = await handle.stat()
        if (afterRead.size !== stat.size || afterRead.mtimeMs !== opened.mtimeMs || createHash('sha256').update(data).digest('hex') !== before.files[path]) throw new Error('Package changed during export')
        files[path] = data
      } finally { await handle.close() }
    }
    const after = await snapshotInstalledSkillDirectory(baseline.inventoryItem, entry)
    if (before.fingerprint !== after.fingerprint) throw new Error('Package changed during export')
    const archive = zipSync(files)
    if (archive.byteLength > MAX_TRANSFER_BYTES) throw new Error('Package exceeds transfer size limit')
    const verifyDirectory = await mkdtemp(join(tmpdir(), 'skillhub-export-check-'))
    try { await extractZip(archive.slice().buffer, verifyDirectory, {rejectInternalMetadata: true}) }
    finally { await rm(verifyDirectory, {recursive: true, force: true}) }
    if (archive.byteLength > MAX_TRANSFER_BYTES) throw new Error('Package exceeds transfer size limit')
    return { filename: `${selected.slug}.zip`, archive }
  } finally { await release() }
}

/** Reject unsafe or oversized content before fingerprint helpers read whole files. */
async function assertBoundedTree(directory: string): Promise<void> {
  if (await realpath(directory) !== directory) throw new Error('Unsafe package root')
  let count = 0
  let bytes = 0
  const walk = async (path: string): Promise<void> => {
    const stat = await lstat(path)
    if (path !== directory && ++count > MAX_ZIP_ENTRIES) throw new Error('Too many package entries')
    if (stat.isSymbolicLink()) throw new Error('Unsafe package entry')
    if (stat.isDirectory()) {
      for (const entry of await readdir(path)) await walk(join(path, entry))
    } else {
      if (!stat.isFile() || stat.size > MAX_SINGLE_FILE_BYTES) throw new Error('Unsafe or oversized package entry')
      bytes += stat.size
      if (bytes > MAX_PACKAGE_BYTES) throw new Error('Package exceeds expanded size limit')
    }
  }
  await walk(directory)
}
