import { describe, expect, it } from 'vitest'
import {
  collectFolderEntries,
  createZipBlob,
  crc32,
  isIgnoredPath,
  packageFolderAsZip,
} from './folder-zip'

const utf8 = new TextEncoder()

function fileAt(relativePath: string, content = 'x'): File {
  const name = relativePath.split('/').pop() || relativePath
  const file = new File([content], name)
  Object.defineProperty(file, 'webkitRelativePath', { value: relativePath })
  return file
}

async function bytesOf(blob: Blob): Promise<Uint8Array> {
  return new Uint8Array(await blob.arrayBuffer())
}

describe('isIgnoredPath', () => {
  it('keeps normal skill files', () => {
    expect(isIgnoredPath('my-skill/SKILL.md')).toBe(false)
    expect(isIgnoredPath('my-skill/scripts/run.sh')).toBe(false)
  })

  it('drops VCS, build and OS junk', () => {
    expect(isIgnoredPath('my-skill/.git/config')).toBe(true)
    expect(isIgnoredPath('my-skill/node_modules/x/index.js')).toBe(true)
    expect(isIgnoredPath('my-skill/__pycache__/m.pyc')).toBe(true)
    expect(isIgnoredPath('my-skill/.DS_Store')).toBe(true)
    expect(isIgnoredPath('my-skill/._resource')).toBe(true)
    expect(isIgnoredPath('my-skill/Thumbs.db')).toBe(true)
  })
})

describe('crc32', () => {
  it('matches known CRC-32/ISO-HDLC vectors', () => {
    expect(crc32(utf8.encode(''))).toBe(0x00000000)
    expect(crc32(utf8.encode('a'))).toBe(0xe8b7be43)
    expect(crc32(utf8.encode('abc'))).toBe(0x352441c2)
  })
})

describe('createZipBlob', () => {
  it('writes a STORE archive with local, central and EOCD records', async () => {
    const blob = createZipBlob([{ path: 'SKILL.md', data: utf8.encode('hello') }])
    const bytes = await bytesOf(blob)
    const view = new DataView(bytes.buffer)

    // Local file header signature at offset 0.
    expect(view.getUint32(0, true)).toBe(0x04034b50)
    // Contains a central directory header and an end-of-central-directory record.
    const eocd = bytes.length - 22
    expect(view.getUint32(eocd, true)).toBe(0x06054b50)
    expect(view.getUint16(eocd + 10, true)).toBe(1) // total entries
    // Central dir offset points at a central directory header signature.
    const cdOffset = view.getUint32(eocd + 16, true)
    expect(view.getUint32(cdOffset, true)).toBe(0x02014b50)
  })
})

describe('collectFolderEntries', () => {
  it('filters junk and sorts remaining files by path', async () => {
    const entries = await collectFolderEntries([
      fileAt('my-skill/scripts/run.sh', 'run'),
      fileAt('my-skill/.git/config', 'gitcfg'),
      fileAt('my-skill/SKILL.md', 'md'),
    ])
    expect(entries.map((e) => e.path)).toEqual(['my-skill/SKILL.md', 'my-skill/scripts/run.sh'])
  })
})

describe('packageFolderAsZip', () => {
  it('names the zip after the top-level folder', async () => {
    const file = await packageFolderAsZip([fileAt('my-skill/SKILL.md', 'md')])
    expect(file.name).toBe('my-skill.zip')
    expect(file.type).toBe('application/zip')
    expect(file.size).toBeGreaterThan(0)
  })

  it('throws when everything was filtered out', async () => {
    await expect(packageFolderAsZip([fileAt('my-skill/.git/config', 'x')])).rejects.toThrow(
      'empty-folder'
    )
  })
})
