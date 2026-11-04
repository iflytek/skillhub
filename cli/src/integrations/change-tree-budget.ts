import { lstat, readdir } from 'node:fs/promises'
import { join, relative } from 'node:path'
import { MAX_SINGLE_FILE_BYTES, MAX_ZIP_ENTRIES } from '../platform/archive'
import { MAX_PACKAGE_BYTES } from '../platform/download'

/** New automatic comparison reads enforce the package budget before allocating file bodies. */
export async function assertChangeTreeBudget(root: string): Promise<void> {
  let bytes = 0, files = 0, entries = 0
  const walk = async (path: string): Promise<void> => {
    if (++entries > MAX_ZIP_ENTRIES * 4) throw new Error('Change tree exceeds entry limit')
    const info = await lstat(path)
    if (info.isSymbolicLink() || (!info.isFile() && !info.isDirectory())
      || (process.getuid && info.uid !== process.getuid())) throw new Error('Unsafe change tree')
    if (info.isDirectory()) {
      for (const name of await readdir(path)) await walk(join(path, name))
    } else {
      if (info.nlink !== 1 || info.size > MAX_SINGLE_FILE_BYTES) throw new Error('Change file exceeds package limit')
      bytes += info.size
      if (relative(root, path).split('\\').join('/') !== '.skillhub/metadata.json') files++
      if (bytes > MAX_PACKAGE_BYTES || files > MAX_ZIP_ENTRIES) throw new Error('Change tree exceeds package limit')
    }
  }
  await walk(root)
}
