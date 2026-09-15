import { isIgnoredPath } from '@/features/publish/folder-zip'

const MAX_BUNDLE_BYTES = 100 * 1024 * 1024
const MAX_FILE_BYTES = 10 * 1024 * 1024
const MAX_FILE_COUNT = 50_000

export type SuiteBundleFolderError =
  | 'empty-folder'
  | 'mixed-folder-roots'
  | 'missing-suite-manifest'
  | 'duplicate-suite-manifest'
  | 'too-many-files'
  | 'file-too-large'
  | 'bundle-too-large'

function relativePath(file: File): string {
  return (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name
}

function withoutRoot(path: string): string {
  const separator = path.indexOf('/')
  return separator < 0 ? path : path.slice(separator + 1)
}

/** Fast metadata-only checks before any selected file is read into the browser ZIP. */
export function validateSuiteBundleFolder(files: File[]): SuiteBundleFolderError | null {
  const included = files.filter((file) => !isIgnoredPath(relativePath(file)))
  if (included.length === 0) return 'empty-folder'
  if (included.length > MAX_FILE_COUNT) return 'too-many-files'

  const roots = new Set<string>()
  let total = 0
  let manifests = 0
  for (const file of included) {
    const path = relativePath(file)
    const separator = path.indexOf('/')
    if (separator > 0) roots.add(path.slice(0, separator))
    if (file.size > MAX_FILE_BYTES) return 'file-too-large'
    total += file.size
    if (total > MAX_BUNDLE_BYTES) return 'bundle-too-large'
    if (withoutRoot(path) === 'SUITE.yaml') manifests += 1
  }
  if (roots.size > 1) return 'mixed-folder-roots'
  if (manifests === 0) return 'missing-suite-manifest'
  if (manifests > 1) return 'duplicate-suite-manifest'
  return null
}

export function validateSuiteBundleZip(file: File): SuiteBundleFolderError | 'invalid-zip' | null {
  if (!file.name.toLowerCase().endsWith('.zip')) return 'invalid-zip'
  if (file.size === 0) return 'empty-folder'
  if (file.size > MAX_BUNDLE_BYTES) return 'bundle-too-large'
  return null
}
