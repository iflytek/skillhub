import { describe, expect, it } from 'vitest'
import { zipSync, strToU8 } from 'fflate'
import {
  PACKAGE_OVERVIEW_ARCHIVE_LIMIT,
  PACKAGE_OVERVIEW_TEXT_LIMIT,
  readPackageOverview,
} from './package-overview'

function archive(files: Record<string, string | Uint8Array>, level: 0 | 6 = 6): File {
  const data = Object.fromEntries(Object.entries(files).map(([name, value]) => [name, typeof value === 'string' ? strToU8(value) : value]))
  return new File([zipSync(data, { level })], 'skill.zip', { type: 'application/zip' })
}

describe('readPackageOverview', () => {
  it.each([0, 6] as const)('reads real STORE/deflate archives (level %i) and prefers root README', async level => {
    expect(await readPackageOverview(archive({ 'SKILL.md': 'original instructions', 'README.md': '# 中文介绍\n\n正文。' }, level)))
      .toEqual({ content: '# 中文介绍\n\n正文。', source: 'readme' })
  })

  it('retains original SKILL frontmatter without changing package bytes', async () => {
    const content = '---\nname: example\n---\n\n# 原始指令'
    const file = archive({ 'skill.md': content })
    const before = await file.arrayBuffer()
    expect(await readPackageOverview(file)).toEqual({ content, source: 'skill' })
    expect(await file.arrayBuffer()).toEqual(before)
  })

  it('strips a shared wrapper while ignoring OS metadata', async () => {
    expect(await readPackageOverview(archive({
      'wrapped/SKILL.md': 'skill', 'wrapped/README.md': 'readme',
      '__MACOSX/._README.md': 'resource fork', '.DS_Store': 'junk',
    }))).toEqual({ content: 'readme', source: 'readme' })
  })

  it('promotes only the immediate skill directory like the backend', async () => {
    expect(await readPackageOverview(archive({
      'outside.txt': 'ignored by backend promotion', 'skill/SKILL.md': 'skill', 'skill/README.md': 'promoted',
    }))).toEqual({ content: 'promoted', source: 'readme' })
  })

  it('does not select nested or hidden documentation when the root skill exists', async () => {
    expect(await readPackageOverview(archive({
      'SKILL.md': 'root', 'docs/README.md': 'nested', '.hidden/README.md': 'hidden',
    }))).toEqual({ content: 'root', source: 'skill' })
  })

  it('does not read a hidden skill directory', async () => {
    expect(await readPackageOverview(archive({ '.hidden/SKILL.md': 'secret' }))).toEqual({ content: '', source: null })
  })

  it('does not choose arbitrarily among ambiguous skill directories', async () => {
    expect(await readPackageOverview(archive({ 'one/SKILL.md': 'one', 'two/SKILL.md': 'two' })))
      .toEqual({ content: '', source: null, error: 'unavailable' })
  })

  it('returns an empty result when no root documentation can be resolved', async () => {
    expect(await readPackageOverview(archive({ 'icon.txt': 'no document' }))).toEqual({ content: '', source: null })
  })

  it('reports malformed archives and invalid UTF-8 instead of replacement text', async () => {
    expect(await readPackageOverview(new File(['not zip'], 'invalid.zip'))).toEqual({ content: '', source: null, error: 'unavailable' })
    expect(await readPackageOverview(archive({ 'README.md': new Uint8Array([0xc3, 0x28]) })))
      .toEqual({ content: '', source: null, error: 'unavailable' })
  })

  it('rejects non-normalized paths rather than picking a misleading README', async () => {
    expect(await readPackageOverview(archive({ '../README.md': 'outside', 'SKILL.md': 'root' })))
      .toEqual({ content: '', source: null, error: 'unavailable' })
  })

  it('bounds compressed input as an optional-preview limit', async () => {
    const file = new File([new Uint8Array(PACKAGE_OVERVIEW_ARCHIVE_LIMIT + 1)], 'large.zip')
    expect(await readPackageOverview(file)).toEqual({ content: '', source: null, error: 'tooLarge' })
  })

  it('bounds declared and actual decompressed text bytes', async () => {
    const file = archive({ 'README.md': 'x'.repeat(PACKAGE_OVERVIEW_TEXT_LIMIT + 1) })
    expect(await readPackageOverview(file)).toEqual({ content: '', source: null, error: 'tooLarge' })
    // Lie about the local header size: the output budget must still catch it.
    const bytes = new Uint8Array(await file.arrayBuffer())
    new DataView(bytes.buffer).setUint32(22, 1, true)
    expect(await readPackageOverview(new File([bytes], 'misleading-size.zip')))
      .toEqual({ content: '', source: null, error: 'tooLarge' })
  })

  it('does not inflate oversized unrelated assets', async () => {
    expect(await readPackageOverview(archive({
      'README.md': 'small document', 'asset.txt': 'x'.repeat(PACKAGE_OVERVIEW_TEXT_LIMIT * 2),
    }))).toEqual({ content: 'small document', source: 'readme' })
  })

  it('supports an exact text budget without truncation', async () => {
    const content = 'x'.repeat(PACKAGE_OVERVIEW_TEXT_LIMIT)
    expect(await readPackageOverview(archive({ 'README.md': content }))).toEqual({ content, source: 'readme' })
  })

  it('aborts before reading and while indexing without returning stale content', async () => {
    const controller = new AbortController()
    controller.abort()
    await expect(readPackageOverview(archive({ 'SKILL.md': 'old' }), controller.signal)).rejects.toMatchObject({ name: 'AbortError' })
    const pending = new AbortController()
    const result = readPackageOverview(archive({ 'asset.bin': new Uint8Array(200_000), 'SKILL.md': 'old' }, 0), pending.signal)
    setTimeout(() => pending.abort(), 0)
    await expect(result).rejects.toMatchObject({ name: 'AbortError' })
  })
})
