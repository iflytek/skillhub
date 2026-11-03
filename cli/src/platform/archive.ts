import { mkdir, readdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, isAbsolute, join, relative, resolve } from 'node:path'
import { zipSync, unzipSync } from 'fflate'
import { MAX_PACKAGE_BYTES } from './download.js'

export const MAX_ZIP_ENTRIES = 500
export const MAX_SINGLE_FILE_BYTES = 10 * 1024 * 1024
const EOCD_SIGNATURE = 0x06054b50
const CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50
const ZIP64_MARKER_16 = 0xffff
const ZIP64_MARKER_32 = 0xffffffff

/**
 * Extract a zip archive buffer into the target directory.
 * Pure JS implementation using fflate — no system commands needed.
 */
export async function extractZip(buffer: ArrayBuffer, targetDir: string, options: { rejectInternalMetadata?: boolean } = {}): Promise<void> {
  await mkdir(targetDir, { recursive: true })
  const archive = new Uint8Array(buffer)
  validateZipCentralDirectory(archive, options.rejectInternalMetadata ?? false)
  const files = unzipSync(archive)
  const entries = Object.entries(files).map(([name, data]) => ({
    name,
    data,
    filePath: safeJoin(targetDir, name),
  }))
  for (const { name, data, filePath } of entries) {
    if (isZipDirectory(name)) {
      await mkdir(filePath, { recursive: true })
      continue
    }
    await mkdir(dirname(filePath), { recursive: true })
    await writeFile(filePath, data)
  }
}

function isZipDirectory(name: string): boolean {
  return name.endsWith('/') || name.endsWith('\\')
}

function validateZipCentralDirectory(archive: Uint8Array, rejectInternalMetadata: boolean): void {
  const view = new DataView(archive.buffer, archive.byteOffset, archive.byteLength)
  const eocdOffset = findEndOfCentralDirectory(view)
  if (eocdOffset < 0) {
    throw new Error('invalid zip central directory')
  }

  const diskNumber = view.getUint16(eocdOffset + 4, true)
  const centralDirectoryDisk = view.getUint16(eocdOffset + 6, true)
  const entriesOnDisk = view.getUint16(eocdOffset + 8, true)
  const totalEntries = view.getUint16(eocdOffset + 10, true)
  const centralDirectorySize = view.getUint32(eocdOffset + 12, true)
  const centralDirectoryOffset = view.getUint32(eocdOffset + 16, true)

  if (
    entriesOnDisk === ZIP64_MARKER_16 ||
    totalEntries === ZIP64_MARKER_16 ||
    centralDirectorySize === ZIP64_MARKER_32 ||
    centralDirectoryOffset === ZIP64_MARKER_32
  ) {
    throw new Error('zip64 archives are not supported')
  }
  if (diskNumber !== 0 || centralDirectoryDisk !== 0 || entriesOnDisk !== totalEntries) {
    throw new Error('multi-disk zip archives are not supported')
  }
  if (totalEntries > MAX_ZIP_ENTRIES) {
    throw new Error('zip entry count exceeds limit')
  }
  if (centralDirectoryOffset + centralDirectorySize > archive.byteLength) {
    throw new Error('invalid zip central directory')
  }

  const destinations = new Map<string, boolean>()
  const localRanges: Array<{ start: number; end: number }> = []
  let offset = centralDirectoryOffset
  let totalUncompressedSize = 0
  const decoder = new TextDecoder()
  for (let i = 0; i < totalEntries; i++) {
    if (offset + 46 > archive.byteLength || view.getUint32(offset, true) !== CENTRAL_DIRECTORY_SIGNATURE) {
      throw new Error('invalid zip central directory')
    }
    const flags = view.getUint16(offset + 8, true)
    const unixMode = view.getUint32(offset + 38, true) >>> 16
    const kind = unixMode & 0xf000
    if (rejectInternalMetadata && ((flags & 1) || (kind !== 0 && kind !== 0x8000 && kind !== 0x4000))) throw new Error('Unsupported zip entry type')
    const uncompressedSize = view.getUint32(offset + 24, true)
    const nameLength = view.getUint16(offset + 28, true)
    const extraLength = view.getUint16(offset + 30, true)
    const commentLength = view.getUint16(offset + 32, true)
    const nameStart = offset + 46
    const nameEnd = nameStart + nameLength
    const nextOffset = nameEnd + extraLength + commentLength
    if (nameEnd > archive.byteLength || nextOffset > archive.byteLength) {
      throw new Error('invalid zip central directory')
    }

    const entryName = decoder.decode(archive.subarray(nameStart, nameEnd))
    const normalized = entryName.replaceAll('\\', '/').split('/').filter(part => part !== '' && part !== '.')
    if (normalized.some(part => part === '..' || (part.includes(':') || [...part].some(character => character.charCodeAt(0) < 32 || character.charCodeAt(0) === 127)) || /[ .]$/.test(part))) {
      throw new Error('unsafe zip entry path')
    }
    const destination = normalized.join('/').toLowerCase()
    const directory = isZipDirectory(entryName)
    if (rejectInternalMetadata && destination === '.skillhub/examples.json' && normalized.join('/') !== destination) {
      throw new Error('Ambiguous skill examples path casing')
    }
    if (rejectInternalMetadata && ((!directory && destination === '.skillhub') || destination === '.skillhub/metadata.json' || destination.startsWith('.skillhub/metadata.json/'))) {
      throw new Error('Installer metadata cannot be supplied by a skill package')
    }
    if (destinations.has(destination) || [...destinations].some(([path, isDirectory]) =>
      (!isDirectory && destination.startsWith(path + '/')) || (!directory && path.startsWith(destination + '/')))) {
      throw new Error('conflicting zip entry destinations')
    }
    destinations.set(destination, directory)
    if (!entryName.endsWith('/') && uncompressedSize > MAX_SINGLE_FILE_BYTES) {
      throw new Error('zip entry size exceeds limit')
    }
    totalUncompressedSize += uncompressedSize
    if (totalUncompressedSize > MAX_PACKAGE_BYTES) {
      throw new Error('zip total uncompressed size exceeds limit')
    }
    const method = view.getUint16(offset + 10, true)
    const compressedSize = view.getUint32(offset + 20, true)
    const localOffset = view.getUint32(offset + 42, true)
    if ((flags & 1) || ![0, 8].includes(method) || method === 0 && compressedSize !== uncompressedSize) throw new Error('invalid zip compression sizes or method')
    if (localOffset + 30 > centralDirectoryOffset || view.getUint32(localOffset, true) !== 0x04034b50) throw new Error('invalid zip local header')
    const localNameLength = view.getUint16(localOffset + 26, true)
    const payloadStart = localOffset + 30 + localNameLength + view.getUint16(localOffset + 28, true)
    const payloadEnd = payloadStart + compressedSize
    if (payloadStart > centralDirectoryOffset || payloadEnd > centralDirectoryOffset
      || localNameLength !== nameLength || view.getUint16(localOffset + 6, true) !== flags
      || view.getUint16(localOffset + 8, true) !== method
      || archive.subarray(localOffset + 30, localOffset + 30 + localNameLength).some((byte, index) => byte !== archive[nameStart + index])) throw new Error('inconsistent zip local header')
    if (!(flags & 8) && (view.getUint32(localOffset + 18, true) !== compressedSize
      || view.getUint32(localOffset + 22, true) !== uncompressedSize
      || view.getUint32(localOffset + 14, true) !== view.getUint32(offset + 16, true))) throw new Error('inconsistent zip local sizes')
    if (localRanges.some(range => localOffset < range.end && payloadEnd > range.start)) throw new Error('overlapping zip entries')
    localRanges.push({ start: localOffset, end: payloadEnd })
    offset = nextOffset
  }
}

function findEndOfCentralDirectory(view: DataView): number {
  const minOffset = Math.max(0, view.byteLength - 0xffff - 22)
  for (let offset = view.byteLength - 22; offset >= minOffset; offset--) {
    if (view.getUint32(offset, true) === EOCD_SIGNATURE) {
      return offset
    }
  }
  return -1
}

/**
 * Create a zip archive from a directory.
 * Returns the archive as a Blob.
 * Pure JS implementation using fflate — no system commands needed.
 */
export interface CreateZipOptions {
  exclude?: (relativePath: string) => boolean
}

export async function createZip(dirPath: string, options: CreateZipOptions = {}): Promise<Blob> {
  const entries: Record<string, Uint8Array> = {}
  await collectFiles(dirPath, dirPath, entries, options)
  const zipped = zipSync(entries, { level: 6 })
  return new Blob([zipped.buffer as ArrayBuffer], { type: 'application/zip' })
}

async function collectFiles(
  basePath: string,
  currentPath: string,
  entries: Record<string, Uint8Array>,
  options: CreateZipOptions
): Promise<void> {
  const items = await readdir(currentPath, { withFileTypes: true })
  for (const item of items) {
    const fullPath = join(currentPath, item.name)
    const relPath = relative(basePath, fullPath).split('\\').join('/')
    if (options.exclude?.(relPath)) continue
    if (item.isDirectory()) {
      entries[relPath + '/'] = new Uint8Array(0)
      await collectFiles(basePath, fullPath, entries, options)
    } else if (item.isFile()) {
      entries[relPath] = new Uint8Array(await readFile(fullPath))
    }
  }
}

/**
 * Detect whether a path is a zip file by checking magic bytes.
 */
export async function isZipFile(filePath: string): Promise<boolean> {
  try {
    const buf = new Uint8Array(await readFile(filePath))
    return buf.length >= 4 && buf[0] === 0x50 && buf[1] === 0x4B && buf[2] === 0x03 && buf[3] === 0x04
  } catch {
    return false
  }
}

function safeJoin(targetDir: string, entryName: string): string {
  if (isAbsolute(entryName)) {
    throw new Error(`unsafe zip entry path: ${entryName}`)
  }

  const root = resolve(targetDir)
  const target = resolve(root, entryName.replaceAll('\\', '/'))
  const rel = relative(root, target)
  if (rel === '' || rel.startsWith('..') || isAbsolute(rel)) {
    throw new Error(`unsafe zip entry path: ${entryName}`)
  }
  return target
}
