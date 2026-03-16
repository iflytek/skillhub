import { describe, expect, it } from 'vitest'
import { resolveDocumentationFilePath } from '@/shared/lib/skill-documentation'
import type { SkillFile } from '@/api/types'

function createFile(filePath: string): SkillFile {
  return {
    id: 1,
    filePath,
    fileSize: 1,
    contentType: 'text/plain',
    sha256: 'hash',
  }
}

describe('resolveDocumentationFilePath', () => {
  it('prefers README over SKILL markdown when both exist', () => {
    const files = [createFile('SKILL.md'), createFile('README.md')]

    expect(resolveDocumentationFilePath(files)).toBe('README.md')
  })

  it('falls back to SKILL markdown when readme is absent', () => {
    const files = [createFile('SKILL.md'), createFile('_meta.json')]

    expect(resolveDocumentationFilePath(files)).toBe('SKILL.md')
  })

  it('returns null when the package has no documentation files', () => {
    const files = [createFile('_meta.json'), createFile('icon.png')]

    expect(resolveDocumentationFilePath(files)).toBeNull()
  })
})
