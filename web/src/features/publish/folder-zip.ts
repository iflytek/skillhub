/**
 * Dependency-free packaging of a selected folder into a skill ZIP.
 *
 * Browsers expose a picked folder as a flat FileList (each File carries a
 * `webkitRelativePath` like `my-skill/SKILL.md`). We build a STORE-method ZIP
 * (no compression — skill packages are small text files, and STORE keeps this
 * dependency-free) from those files so the result flows through the exact same
 * upload/publish path as a hand-made ZIP.
 *
 * We drop VCS/build/OS junk that a real on-disk folder almost always contains
 * (`.git/`, `node_modules/`, `.DS_Store`, …) so it never bloats the package or
 * the file-count limit. The server additionally strips a single root directory
 * and OS-metadata entries, so paths are kept as-is (`my-skill/SKILL.md`).
 */

/** Directory names whose entire subtree is excluded from the package. */
const IGNORED_DIR_SEGMENTS = new Set([
  '.git',
  '.svn',
  '.hg',
  'node_modules',
  '__pycache__',
  '__MACOSX',
])

/** Exact file names that are always excluded. */
const IGNORED_FILE_NAMES = new Set(['.DS_Store', 'Thumbs.db', 'desktop.ini'])

/** Returns true if a relative path should be excluded from the package. */
export function isIgnoredPath(relativePath: string): boolean {
  const parts = relativePath.split('/')
  const name = parts[parts.length - 1]
  if (!name) return true // trailing slash / directory marker
  if (parts.some((segment) => IGNORED_DIR_SEGMENTS.has(segment))) return true
  if (IGNORED_FILE_NAMES.has(name)) return true
  if (name.startsWith('._')) return true
  if (name.endsWith('.pyc') || name.endsWith('.swp')) return true
  return false
}

// --- CRC-32 (IEEE 802.3, polynomial 0xEDB88320) -------------------------------

const CRC_TABLE = (() => {
  const table = new Uint32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    }
    table[n] = c >>> 0
  }
  return table
})()

export function crc32(bytes: Uint8Array): number {
  let crc = 0xffffffff
  for (let i = 0; i < bytes.length; i++) {
    crc = CRC_TABLE[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8)
  }
  return (crc ^ 0xffffffff) >>> 0
}

// --- ZIP writer (STORE method, no data descriptors) ---------------------------

export interface ZipEntry {
  path: string
  data: Uint8Array
}

const utf8 = new TextEncoder()

/**
 * Builds a ZIP archive (STORE method) containing the given entries and returns
 * it as a Blob. 32-bit size fields are used; skill packages are far below the
 * 4 GB boundary where ZIP64 would be required.
 */
export function createZipBlob(entries: ZipEntry[]): Blob {
  const localParts: Uint8Array[] = []
  const centralParts: Uint8Array[] = []
  let offset = 0

  for (const entry of entries) {
    const nameBytes = utf8.encode(entry.path)
    const crc = crc32(entry.data)
    const size = entry.data.length

    const local = new Uint8Array(30 + nameBytes.length)
    const lv = new DataView(local.buffer)
    lv.setUint32(0, 0x04034b50, true) // local file header signature
    lv.setUint16(4, 20, true) // version needed
    lv.setUint16(6, 0x0800, true) // flags: bit 11 = UTF-8 names
    lv.setUint16(8, 0, true) // method: STORE
    lv.setUint16(10, 0, true) // mod time
    lv.setUint16(12, 0, true) // mod date
    lv.setUint32(14, crc, true)
    lv.setUint32(18, size, true) // compressed size (== uncompressed for STORE)
    lv.setUint32(22, size, true) // uncompressed size
    lv.setUint16(26, nameBytes.length, true)
    lv.setUint16(28, 0, true) // extra length
    local.set(nameBytes, 30)

    localParts.push(local, entry.data)

    const central = new Uint8Array(46 + nameBytes.length)
    const cv = new DataView(central.buffer)
    cv.setUint32(0, 0x02014b50, true) // central directory header signature
    cv.setUint16(4, 20, true) // version made by
    cv.setUint16(6, 20, true) // version needed
    cv.setUint16(8, 0x0800, true) // flags
    cv.setUint16(10, 0, true) // method: STORE
    cv.setUint16(12, 0, true) // mod time
    cv.setUint16(14, 0, true) // mod date
    cv.setUint32(16, crc, true)
    cv.setUint32(20, size, true)
    cv.setUint32(24, size, true)
    cv.setUint16(28, nameBytes.length, true)
    cv.setUint16(30, 0, true) // extra length
    cv.setUint16(32, 0, true) // comment length
    cv.setUint16(34, 0, true) // disk number start
    cv.setUint16(36, 0, true) // internal attrs
    cv.setUint32(38, 0, true) // external attrs
    cv.setUint32(42, offset, true) // relative offset of local header
    central.set(nameBytes, 46)
    centralParts.push(central)

    offset += local.length + entry.data.length
  }

  const centralSize = centralParts.reduce((n, p) => n + p.length, 0)
  const eocd = new Uint8Array(22)
  const ev = new DataView(eocd.buffer)
  ev.setUint32(0, 0x06054b50, true) // end of central directory signature
  ev.setUint16(4, 0, true) // disk number
  ev.setUint16(6, 0, true) // central dir start disk
  ev.setUint16(8, entries.length, true) // entries on this disk
  ev.setUint16(10, entries.length, true) // total entries
  ev.setUint32(12, centralSize, true) // central dir size
  ev.setUint32(16, offset, true) // central dir offset
  ev.setUint16(20, 0, true) // comment length

  // Concatenate into a single buffer so the Blob part is a Uint8Array<ArrayBuffer>.
  const parts = [...localParts, ...centralParts, eocd]
  const total = parts.reduce((n, p) => n + p.length, 0)
  const out = new Uint8Array(total)
  let pos = 0
  for (const part of parts) {
    out.set(part, pos)
    pos += part.length
  }
  return new Blob([out], { type: 'application/zip' })
}

// --- Folder -> File ------------------------------------------------------------

/** Reads the picked folder's files into ZIP entries, skipping junk. */
export async function collectFolderEntries(files: File[]): Promise<ZipEntry[]> {
  const entries: ZipEntry[] = []
  for (const file of files) {
    const path = (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name
    if (isIgnoredPath(path)) continue
    const data = new Uint8Array(await file.arrayBuffer())
    entries.push({ path, data })
  }
  entries.sort((a, b) => (a.path < b.path ? -1 : a.path > b.path ? 1 : 0))
  return entries
}

/** Top-level folder name of a webkitdirectory selection, for naming the zip. */
function rootFolderName(files: File[]): string {
  for (const file of files) {
    const rel = (file as File & { webkitRelativePath?: string }).webkitRelativePath
    if (rel && rel.includes('/')) return rel.slice(0, rel.indexOf('/'))
  }
  return 'skill'
}

/**
 * Packages a picked folder into a `<folder>.zip` File ready for the existing
 * upload flow. Throws if every file was filtered out as junk.
 */
export async function packageFolderAsZip(fileList: FileList | File[]): Promise<File> {
  const files = Array.from(fileList)
  const entries = await collectFolderEntries(files)
  if (entries.length === 0) {
    throw new Error('empty-folder')
  }
  const blob = createZipBlob(entries)
  return new File([blob], `${rootFolderName(files)}.zip`, { type: 'application/zip' })
}
