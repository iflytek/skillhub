import { mkdir, readdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, isAbsolute, join, relative, resolve } from 'node:path'
import { zipSync, unzipSync } from 'fflate'
import { MAX_PACKAGE_BYTES } from './download'

const MAX_ZIP_ENTRIES = 500
const MAX_SINGLE_FILE_BYTES = 10 * 1024 * 1024
const EOCD_SIGNATURE = 0x06054b50
const CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50
const ZIP64_MARKER_16 = 0xffff
const ZIP64_MARKER_32 = 0xffffffff

/**
 * Extract a zip archive buffer into the target directory.
 * Pure JS implementation using fflate — no system commands needed.
 */
export async function extractZip(buffer: ArrayBuffer, targetDir: string): Promise<void> {
  await mkdir(targetDir, { recursive: true })
  const archive = new Uint8Array(buffer)
  validateZipCentralDirectory(archive)
  const files = unzipSync(archive)
  const entries = Object.entries(files).map(([name, data]) => ({
    name,
    data,
    filePath: safeJoin(targetDir, name),
  }))
  for (const { name, data, filePath } of entries) {
    if (name.endsWith('/')) {
      await mkdir(filePath, { recursive: true })
      continue
    }
    await mkdir(dirname(filePath), { recursive: true })
    await writeFile(filePath, data)
  }
}

function validateZipCentralDirectory(archive: Uint8Array): void {
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

  let offset = centralDirectoryOffset
  let totalUncompressedSize = 0
  const decoder = new TextDecoder()
  for (let i = 0; i < totalEntries; i++) {
    if (offset + 46 > archive.byteLength || view.getUint32(offset, true) !== CENTRAL_DIRECTORY_SIGNATURE) {
      throw new Error('invalid zip central directory')
    }
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
    if (!entryName.endsWith('/') && uncompressedSize > MAX_SINGLE_FILE_BYTES) {
      throw new Error('zip entry size exceeds limit')
    }
    totalUncompressedSize += uncompressedSize
    if (totalUncompressedSize > MAX_PACKAGE_BYTES) {
      throw new Error('zip total uncompressed size exceeds limit')
    }
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
  const target = resolve(root, entryName)
  const rel = relative(root, target)
  if (rel === '' || rel.startsWith('..') || isAbsolute(rel)) {
    throw new Error(`unsafe zip entry path: ${entryName}`)
  }
  return target
}
