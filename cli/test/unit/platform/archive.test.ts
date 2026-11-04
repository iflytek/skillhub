import { mkdtemp, readFile, rm, stat, writeFile } from 'node:fs/promises'
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

  test.each(['/', '\\'])('extracts explicit directories and files with %s separators', async separator => {
    const root = await mkdtemp(join(tmpdir(), 'skillhub-archive-directories-'))
    try {
      const source = join(root, 'source')
      const target = join(root, 'target')
      const examples = new TextEncoder().encode('{"schemaVersion":1,"examples":{"zh-CN":[]}}\n')
      const archive = zipSync({
        'SKILL.md': new TextEncoder().encode('# Demo'),
        [`.skillhub${separator}`]: new Uint8Array(0),
        [`.skillhub${separator}examples.json`]: examples,
        [`resources${separator}`]: new Uint8Array(0),
        [`resources${separator}empty${separator}`]: new Uint8Array(0),
        [`resources${separator}data.bin`]: new Uint8Array([0, 255, 42]),
      })

      await extractZip(archive.buffer as ArrayBuffer, source, { rejectInternalMetadata: true })
      const repacked = await createZip(source)
      await extractZip(await repacked.arrayBuffer(), target, { rejectInternalMetadata: true })

      for (const directory of [source, target]) {
        expect(await readFile(join(directory, 'SKILL.md'), 'utf-8')).toBe('# Demo')
        expect(new Uint8Array(await readFile(join(directory, '.skillhub', 'examples.json')))).toEqual(examples)
        expect(new Uint8Array(await readFile(join(directory, 'resources', 'data.bin')))).toEqual(new Uint8Array([0, 255, 42]))
        expect((await stat(join(directory, '.skillhub'))).isDirectory()).toBe(true)
        expect((await stat(join(directory, 'resources', 'empty'))).isDirectory()).toBe(true)
      }
    } finally {
      await rm(root, { recursive: true, force: true })
    }
  })

  test.each(['.skillhub/metadata.json', '.skillhub/./metadata.json', '.SKILLHUB/METADATA.JSON', '.skillhub\\metadata.json', '.skillhub/metadata.json/child'])('install rejects reserved metadata alias %s before writing', async path => {
    const target = await mkdtemp(join(tmpdir(), 'skillhub-reserved-'))
    const archive = zipSync({ 'SKILL.md': new TextEncoder().encode('safe'), [path]: new TextEncoder().encode('untrusted') })
    await expect(extractZip(archive.buffer as ArrayBuffer, target, { rejectInternalMetadata: true })).rejects.toThrow('Installer metadata')
    await expect(readFile(join(target, 'SKILL.md'))).rejects.toThrow()
  })

  test.each(['.SKILLHUB/examples.json', '.skillhub/Examples.JSON'])('install rejects ambiguous cross-platform examples path %s', async path => {
    const target = await mkdtemp(join(tmpdir(), 'skillhub-case-alias-'))
    const archive = zipSync({ 'SKILL.md': new TextEncoder().encode('safe'), [path]: new TextEncoder().encode('{}') })
    await expect(extractZip(archive.buffer as ArrayBuffer, target, { rejectInternalMetadata: true })).rejects.toThrow('Ambiguous skill examples')
    await expect(readFile(join(target, 'SKILL.md'))).rejects.toThrow()
  })

  test('rejects canonical duplicate and file/directory conflicts', async () => {
    for (const entries of [{ 'a.json': 'one', './a.json': 'two' }, { a: 'file', 'a/b': 'child' }]) {
      const target = await mkdtemp(join(tmpdir(), 'skillhub-duplicate-'))
      const archive = zipSync(Object.fromEntries(Object.entries(entries).map(([path, content]) => [path, new TextEncoder().encode(content)])))
      await expect(extractZip(archive.buffer as ArrayBuffer, target)).rejects.toThrow('conflicting zip')
    }
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

  test('rejects unsafe zip before writing earlier safe entries', async () => {
    const target = await mkdtemp(join(tmpdir(), 'skillhub-archive-partial-'))
    const unsafe = zipSync({
      'SKILL.md': new TextEncoder().encode('# Partial'),
      '../escape.txt': new TextEncoder().encode('bad'),
    })

    await expect(extractZip(unsafe.buffer as ArrayBuffer, target)).rejects.toThrow('unsafe zip entry path')
    await expect(readFile(join(target, 'SKILL.md'), 'utf-8')).rejects.toThrow()
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

  test('rejects zip archives with more than 500 entries before extraction', async () => {
    const target = await mkdtemp(join(tmpdir(), 'skillhub-archive-many-'))
    const manyEntries = Object.fromEntries(
      Array.from({ length: 501 }, (_, index) => [`file-${index}.txt`, new Uint8Array(0)])
    )
    const archive = zipSync(manyEntries)

    await expect(extractZip(archive.buffer as ArrayBuffer, target)).rejects.toThrow('zip entry count exceeds limit')
  })

  test('rejects zip entries larger than 10 MiB before extraction', async () => {
    const target = await mkdtemp(join(tmpdir(), 'skillhub-archive-large-file-'))
    const archive = zipSync({ 'large.bin': new Uint8Array(10 * 1024 * 1024 + 1) })

    await expect(extractZip(archive.buffer as ArrayBuffer, target)).rejects.toThrow('zip entry size exceeds limit')
  })

  test('rejects zip archives larger than 100 MiB after decompression before extraction', async () => {
    const target = await mkdtemp(join(tmpdir(), 'skillhub-archive-large-total-'))
    const oneMiB = new Uint8Array(1024 * 1024)
    const entries = Object.fromEntries(
      Array.from({ length: 101 }, (_, index) => [`file-${index}.bin`, oneMiB])
    )
    const archive = zipSync(entries)

    await expect(extractZip(archive.buffer as ArrayBuffer, target)).rejects.toThrow('zip total uncompressed size exceeds limit')
  })
})

test('rejects stored-size lies and reused local offsets before writing', async () => {
  for (const attack of ['size', 'offset', 'method', 'payload'] as const) {
    const target = await mkdtemp(join(tmpdir(), 'skillhub-invalid-zip-'))
    try {
      const archive = zipSync({'one.txt':new Uint8Array([1,2,3,4]),'two.txt':new Uint8Array([5,6,7,8])},{level:0})
      const view = new DataView(archive.buffer,archive.byteOffset,archive.byteLength)
      const headers: number[] = []
      for (let i = 0; i + 46 <= archive.length; i++) if (view.getUint32(i,true) === 0x02014b50) headers.push(i)
      const first = headers[0]!
      const second = headers[1]!
      if (attack === 'size') view.setUint32(first + 24,0,true)
      if (attack === 'offset') view.setUint32(second + 42,view.getUint32(first + 42,true),true)
      if (attack === 'method') view.setUint16(first + 10,99,true)
      if (attack === 'payload') { view.setUint32(first + 20,archive.length,true); view.setUint32(first + 24,archive.length,true) }
      await expect(extractZip(archive.slice().buffer,target,{rejectInternalMetadata:true})).rejects.toThrow()
      await expect(readFile(join(target,'one.txt'))).rejects.toMatchObject({code:'ENOENT'})
    } finally { await rm(target,{recursive:true,force:true}) }
  }
})
