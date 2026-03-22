import { describe, it, expect } from 'vitest'
import { buildFileTree, type FileTreeNode } from './file-tree-builder'
import type { SkillFile } from '@/api/types'

describe('buildFileTree', () => {
  it('should convert flat file list to tree structure', () => {
    const files: SkillFile[] = [
      { filePath: 'README.md', fileSize: 1024 },
      { filePath: 'src/index.ts', fileSize: 2048 },
      { filePath: 'src/utils/helper.ts', fileSize: 512 },
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
      { filePath: 'package.json', fileSize: 256 },
    ]

    const tree = buildFileTree(files)

    expect(tree).toHaveLength(1)
    expect(tree[0].type).toBe('file')
    expect(tree[0].path).toBe('package.json')
  })

  it('should handle deeply nested directories', () => {
    const files: SkillFile[] = [
      { filePath: 'a/b/c/d/file.txt', fileSize: 100 },
    ]

    const tree = buildFileTree(files)

    expect(tree[0].type).toBe('directory')
    expect(tree[0].name).toBe('a')
    expect(tree[0].children![0].name).toBe('b')
  })
})
