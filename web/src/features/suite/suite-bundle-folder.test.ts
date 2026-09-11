import { describe, expect, it } from 'vitest'
import { validateSuiteBundleFolder, validateSuiteBundleZip } from './suite-bundle-folder'

function fileAt(relativePath: string, size = 1): File {
  const file = new File(['x'], relativePath.split('/').pop() || relativePath)
  Object.defineProperty(file, 'webkitRelativePath', { value: relativePath })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

describe('validateSuiteBundleFolder', () => {
  it('accepts one Bundle root with a manifest and member package', () => {
    expect(validateSuiteBundleFolder([
      fileAt('bundle/SUITE.yaml'),
      fileAt('bundle/skills/member/SKILL.md'),
    ])).toBeNull()
  })

  it('rejects missing and ambiguous roots before reading file bytes', () => {
    expect(validateSuiteBundleFolder([fileAt('bundle/skills/member/SKILL.md')]))
      .toBe('missing-suite-manifest')
    expect(validateSuiteBundleFolder([
      fileAt('bundle-a/SUITE.yaml'),
      fileAt('bundle-b/SUITE.yaml'),
    ])).toBe('mixed-folder-roots')
  })

  it('does not count ignored build and VCS files against Bundle limits', () => {
    expect(validateSuiteBundleFolder([
      fileAt('bundle/SUITE.yaml'),
      fileAt('bundle/.git/objects/large', 20 * 1024 * 1024),
    ])).toBeNull()
  })

  it('rejects oversized files and aggregate selections', () => {
    expect(validateSuiteBundleFolder([
      fileAt('bundle/SUITE.yaml'),
      fileAt('bundle/large.bin', 10 * 1024 * 1024 + 1),
    ])).toBe('file-too-large')
    const files = Array.from({ length: 11 }, (_, index) =>
      fileAt(index === 0 ? 'bundle/SUITE.yaml' : `bundle/chunk-${index}`, 10 * 1024 * 1024))
    expect(validateSuiteBundleFolder(files)).toBe('bundle-too-large')
  })

  it('accepts exact byte limits and rejects the first byte above them', () => {
    expect(validateSuiteBundleFolder([
      fileAt('bundle/SUITE.yaml', 1),
      ...Array.from({ length: 10 }, (_, index) =>
        fileAt(`bundle/chunk-${index}`, 10 * 1024 * 1024 - (index === 0 ? 1 : 0))),
    ])).toBeNull()
    expect(validateSuiteBundleFolder([
      fileAt('bundle/SUITE.yaml'),
      fileAt('bundle/large.bin', 10 * 1024 * 1024 + 1),
    ])).toBe('file-too-large')
  })

  it('rejects more than 50,000 included files using metadata only', () => {
    const files = [fileAt('bundle/SUITE.yaml'), ...Array.from(
      { length: 50_000 },
      (_, index) => fileAt(`bundle/member-${index}.md`, 0),
    )]
    expect(validateSuiteBundleFolder(files)).toBe('too-many-files')
  })
})

describe('validateSuiteBundleZip', () => {
  it('requires a non-empty zip within the archive limit', () => {
    expect(validateSuiteBundleZip(new File(['zip'], 'bundle.zip'))).toBeNull()
    expect(validateSuiteBundleZip(new File(['text'], 'bundle.txt'))).toBe('invalid-zip')
    expect(validateSuiteBundleZip(new File([], 'bundle.zip'))).toBe('empty-folder')
  })

  it('accepts an archive at 100 MB and rejects one byte above it', () => {
    const atLimit = new File(['x'], 'bundle.zip')
    Object.defineProperty(atLimit, 'size', { value: 100 * 1024 * 1024 })
    const aboveLimit = new File(['x'], 'bundle.zip')
    Object.defineProperty(aboveLimit, 'size', { value: 100 * 1024 * 1024 + 1 })
    expect(validateSuiteBundleZip(atLimit)).toBeNull()
    expect(validateSuiteBundleZip(aboveLimit)).toBe('bundle-too-large')
  })
})
