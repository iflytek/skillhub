import { mkdir, readdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, isAbsolute, join, relative, resolve } from 'node:path'
import { zipSync, unzipSync } from 'fflate'

/**
 * Extract a zip archive buffer into the target directory.
 * Pure JS implementation using fflate — no system commands needed.
 */
export async function extractZip(buffer: ArrayBuffer, targetDir: string): Promise<void> {
  await mkdir(targetDir, { recursive: true })
  const files = unzipSync(new Uint8Array(buffer))
  for (const [name, data] of Object.entries(files)) {
    const filePath = safeJoin(targetDir, name)
    if (name.endsWith('/')) {
      await mkdir(filePath, { recursive: true })
      continue
    }
    await mkdir(dirname(filePath), { recursive: true })
    await writeFile(filePath, data)
  }
}

/**
 * Create a zip archive from a directory.
 * Returns the archive as a Blob.
 * Pure JS implementation using fflate — no system commands needed.
 */
export async function createZip(dirPath: string): Promise<Blob> {
  const entries: Record<string, Uint8Array> = {}
  await collectFiles(dirPath, dirPath, entries)
  const zipped = zipSync(entries, { level: 6 })
  return new Blob([zipped.buffer as ArrayBuffer], { type: 'application/zip' })
}

async function collectFiles(basePath: string, currentPath: string, entries: Record<string, Uint8Array>): Promise<void> {
  const items = await readdir(currentPath, { withFileTypes: true })
  for (const item of items) {
    const fullPath = join(currentPath, item.name)
    const relPath = relative(basePath, fullPath)
    if (item.isDirectory()) {
      entries[relPath + '/'] = new Uint8Array(0)
      await collectFiles(basePath, fullPath, entries)
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
