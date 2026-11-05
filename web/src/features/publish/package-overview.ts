import { Unzip, UnzipInflate, type UnzipFile } from 'fflate'

// Optional browser-preview budgets, not upload or server package limits.
export const PACKAGE_OVERVIEW_ARCHIVE_LIMIT = 20 * 1024 * 1024
export const PACKAGE_OVERVIEW_TEXT_LIMIT = 1024 * 1024
const ENTRY_LIMIT = 256
const INPUT_CHUNK_BYTES = 1024

export interface PackageOverview {
  content: string
  source: 'readme' | 'skill' | null
  error?: 'unavailable' | 'tooLarge'
}

class PreviewLimitError extends Error {}

function checkAbort(signal?: AbortSignal) {
  if (signal?.aborted) throw new DOMException('Package preview cancelled', 'AbortError')
}

function archivePath(name: string): string | null {
  const path = name.replace(/\\/g, '/').trim()
  if (path.endsWith('/')) return null
  const parts = path.split('/')
  const filename = parts[parts.length - 1]
  if (parts[0] === '__MACOSX' || filename === '.DS_Store' || filename.startsWith('._')) return null
  if (!path || path.startsWith('/') || path.includes(':') || parts.some(part => !part || part === '.' || part === '..')) {
    throw new Error('Invalid archive path')
  }
  if (filename.toLowerCase() === 'skill.md') parts[parts.length - 1] = 'SKILL.md'
  return parts.join('/')
}

function documentationEntry(entries: Map<string, UnzipFile>): { file: UnzipFile; source: 'readme' | 'skill' } | null {
  // Match SkillPackageArchiveExtractor: strip one shared outer directory,
  // then promote a single immediate skill directory when SKILL.md is absent.
  let paths = [...entries.keys()]
  let prefix = ''
  if (paths.length && paths.every(path => path.includes('/'))) {
    const roots = new Set(paths.map(path => path.split('/')[0]))
    if (roots.size === 1) {
      prefix = paths[0].slice(0, paths[0].indexOf('/') + 1)
      paths = paths.map(path => path.slice(prefix.length))
    }
  }
  if (!paths.includes('SKILL.md')) {
    const skillRoots = paths.filter(path => /^[^/]+\/SKILL\.md$/.test(path))
    if (skillRoots.length > 1) throw new Error('Ambiguous skill directories')
    if (skillRoots.length === 1) {
      prefix += skillRoots[0].slice(0, skillRoots[0].indexOf('/') + 1)
    }
  }
  // Hidden directories and installer/OS metadata must not become the overview.
  if (prefix.split('/').some(part => part.startsWith('.'))) return null
  for (const [name, source] of [['README.md', 'readme'], ['SKILL.md', 'skill']] as const) {
    const file = entries.get(prefix + name)
    if (file) return { file, source }
  }
  return null
}

/** Read only the selected root document; never inflate unrelated package files. */
export async function readPackageOverview(file: File, signal?: AbortSignal): Promise<PackageOverview> {
  checkAbort(signal)
  if (file.size > PACKAGE_OVERVIEW_ARCHIVE_LIMIT) return { content: '', source: null, error: 'tooLarge' }

  try {
    const data = new Uint8Array(await file.arrayBuffer())
    checkAbort(signal)
    const entries = new Map<string, UnzipFile>()
    const unzip = new Unzip(entry => {
      const path = archivePath(entry.name)
      if (!path) return
      if (entries.size >= ENTRY_LIMIT) throw new PreviewLimitError()
      if (entries.has(path)) throw new Error('Duplicate archive path')
      entries.set(path, entry)
    })
    unzip.register(UnzipInflate)
    // Small compressed chunks also bound each inflater output allocation. Yield
    // while indexing so cancellation can interrupt a large optional preview.
    for (let offset = 0; offset < data.length; offset += INPUT_CHUNK_BYTES) {
      checkAbort(signal)
      const end = Math.min(offset + INPUT_CHUNK_BYTES, data.length)
      unzip.push(data.subarray(offset, end), end === data.length)
      if (end < data.length && end % (64 * INPUT_CHUNK_BYTES) === 0) {
        await new Promise<void>(resolve => setTimeout(resolve, 0))
      }
    }
    if (!entries.size) return { content: '', source: null, error: 'unavailable' }
    const document = documentationEntry(entries)
    if (!document) return { content: '', source: null }
    const entry = document.file
    if (entry.originalSize !== undefined && entry.originalSize > PACKAGE_OVERVIEW_TEXT_LIMIT) {
      throw new PreviewLimitError()
    }
    const chunks: Uint8Array[] = []
    let size = 0
    let finished = false
    try {
      entry.ondata = (error, chunk, final) => {
        checkAbort(signal)
        if (error) throw error
        size += chunk.length
        if (size > PACKAGE_OVERVIEW_TEXT_LIMIT) throw new PreviewLimitError()
        chunks.push(chunk)
        finished = final
      }
      entry.start()
    } finally {
      entry.terminate()
    }
    if (!finished) throw new Error('Incomplete document')
    const content = new Uint8Array(size)
    let offset = 0
    for (const chunk of chunks) {
      content.set(chunk, offset)
      offset += chunk.length
    }
    checkAbort(signal)
    return { content: new TextDecoder('utf-8', { fatal: true }).decode(content), source: document.source }
  } catch (error) {
    checkAbort(signal)
    return { content: '', source: null, error: error instanceof PreviewLimitError ? 'tooLarge' : 'unavailable' }
  }
}
