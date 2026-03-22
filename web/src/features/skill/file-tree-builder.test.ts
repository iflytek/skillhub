import { describe, it, expect } from 'vitest'
import { buildFileTree } from './file-tree-builder'
import type { SkillFile } from '@/api/types'

// Helper to create a minimal SkillFile for testing
function makeFile(filePath: string, fileSize: number): SkillFile {
  return { id: 0, filePath, fileSize, contentType: 'application/octet-stream', sha256: '' }
}

describe('buildFileTree', () => {
  it('should convert flat file list to tree structure', () => {
    const files: SkillFile[] = [
      makeFile('README.md', 1024),
      makeFile('src/index.ts', 2048),
      makeFile('src/utils/helper.ts', 512),
    ]

    const tree = buildFileTree(files)

    expect(tree).toHaveLength(2) // README.md and src/
    expect(tree[0].type).toBe('file')
    expect(tree[0].name).toBe('README.md')
    expect(tree[1].type).toBe('directory')
    expect(tree[1].name).toBe('src')
    expect(tree[1].children).toHaveLength(2) // index.ts and utils/
  })

  it('should handle root-level files', () => {
    const files: SkillFile[] = [
      makeFile('package.json', 256),
    ]

    const tree = buildFileTree(files)

    expect(tree).toHaveLength(1)
    expect(tree[0].type).toBe('file')
    expect(tree[0].path).toBe('package.json')
  })

  it('should handle deeply nested directories', () => {
    const files: SkillFile[] = [
      makeFile('a/b/c/d/file.txt', 100),
    ]

    const tree = buildFileTree(files)

    expect(tree[0].type).toBe('directory')
    expect(tree[0].name).toBe('a')
    expect(tree[0].children![0].name).toBe('b')
  })
})
