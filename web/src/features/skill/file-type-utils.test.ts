import { describe, it, expect } from 'vitest'
import { getFileIcon, isPreviewable, getFileTypeLabel, canPreviewFile } from './file-type-utils'

describe('file-type-utils', () => {
  describe('isPreviewable', () => {
    it('should identify markdown files as previewable', () => {
      expect(isPreviewable('README.md', 1024)).toBe(true)
      expect(isPreviewable('docs/guide.mdx', 2048)).toBe(true)
    })

    it('should identify text files as previewable', () => {
      expect(isPreviewable('config.json', 512)).toBe(true)
      expect(isPreviewable('script.sh', 256)).toBe(true)
      expect(isPreviewable('index.ts', 1024)).toBe(true)
    })

    it('should reject binary files', () => {
      expect(isPreviewable('image.png', 1024)).toBe(false)
      expect(isPreviewable('video.mp4', 2048)).toBe(false)
      expect(isPreviewable('archive.zip', 512)).toBe(false)
    })

    it('should reject files larger than 1MB', () => {
      const oneMB = 1024 * 1024
      expect(isPreviewable('large.txt', oneMB + 1)).toBe(false)
      expect(isPreviewable('large.md', oneMB + 1)).toBe(false)
    })
  })

  describe('canPreviewFile', () => {
    it('should return reason for non-previewable files', () => {
      expect(canPreviewFile('large.txt', 2 * 1024 * 1024)).toEqual({
        canPreview: false,
        reason: 'too-large',
      })

      expect(canPreviewFile('image.png', 1024)).toEqual({
        canPreview: false,
        reason: 'binary',
      })

      expect(canPreviewFile('unknown.xyz', 1024)).toEqual({
        canPreview: false,
        reason: 'unsupported',
      })
    })

    it('should return canPreview true for valid files', () => {
      expect(canPreviewFile('README.md', 1024)).toEqual({
        canPreview: true,
      })
    })
  })

  describe('getFileTypeLabel', () => {
    it('should return correct labels', () => {
      expect(getFileTypeLabel('test.md')).toBe('markdown')
      expect(getFileTypeLabel('script.sh')).toBe('bash')
      expect(getFileTypeLabel('config.json')).toBe('json')
    })
  })
})
