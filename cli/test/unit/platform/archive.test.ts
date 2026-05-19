import { mkdtemp, readFile, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, test } from 'bun:test'
import { zipSync } from 'fflate'
import { createZip, extractZip, isZipFile } from '../../../src/platform/archive'

describe('archive helpers', () => {
  test('creates and extracts zip archives', async () => {
    const source = await mkdtemp(join(tmpdir(), 'skillhub-archive-source-'))
    const target = await mkdtemp(join(tmpdir(), 'skillhub-archive-target-'))
    await writeFile(join(source, 'SKILL.md'), '# Demo')

    const archive = await createZip(source)
    await extractZip(await archive.arrayBuffer(), target)

    expect(await readFile(join(target, 'SKILL.md'), 'utf-8')).toBe('# Demo')
  })

  test('detects zip files by magic bytes', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'skillhub-archive-detect-'))
    const zipPath = join(dir, 'skill.zip')
    await writeFile(zipPath, zipSync({ 'SKILL.md': new TextEncoder().encode('# Demo') }))

    expect(await isZipFile(zipPath)).toBe(true)
  })

  test('rejects zip entries that escape target directory', async () => {
    const target = await mkdtemp(join(tmpdir(), 'skillhub-archive-unsafe-'))
    const unsafe = zipSync({ '../escape.txt': new TextEncoder().encode('bad') })

    await expect(extractZip(unsafe.buffer as ArrayBuffer, target)).rejects.toThrow('unsafe zip entry path')
  })

  test('rejects zip entries with absolute paths', async () => {
    const target = await mkdtemp(join(tmpdir(), 'skillhub-archive-abs-'))
    const unsafe = zipSync({ '/etc/passwd': new TextEncoder().encode('bad') })

    await expect(extractZip(unsafe.buffer as ArrayBuffer, target)).rejects.toThrow('unsafe zip entry path')
  })

  test('rejects zip entries with multi-level ../ traversal', async () => {
    const target = await mkdtemp(join(tmpdir(), 'skillhub-archive-multi-'))
    const unsafe = zipSync({ 'foo/../../escape.txt': new TextEncoder().encode('escaped') })

    await expect(extractZip(unsafe.buffer as ArrayBuffer, target)).rejects.toThrow('unsafe zip entry path')
  })

  test('handles empty zip gracefully', async () => {
    const target = await mkdtemp(join(tmpdir(), 'skillhub-archive-empty-'))
    const empty = zipSync({})

    await extractZip(empty.buffer as ArrayBuffer, target)

    const { readdir } = await import('node:fs/promises')
    const entries = await readdir(target)
    expect(entries).toEqual([])
  })
})
