import type { SkillFile } from '@/api/types'

const DOCUMENTATION_PREFERENCE = ['README.md', 'SKILL.md'] as const

export function resolveDocumentationFilePath(files?: SkillFile[] | null): string | null {
  if (!files || files.length === 0) {
    return null
  }

  const filePaths = new Set(files.map((file) => file.filePath))
  for (const preferredPath of DOCUMENTATION_PREFERENCE) {
    if (filePaths.has(preferredPath)) {
      return preferredPath
    }
  }

  return null
}
