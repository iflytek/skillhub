import { describe, expect, it } from 'vitest'
import { MARKDOWN_IMAGE_CLASS_NAME } from './markdown-renderer'

describe('MARKDOWN_IMAGE_CLASS_NAME', () => {
  it('keeps markdown images at their intrinsic width while remaining responsive', () => {
    const classNames = MARKDOWN_IMAGE_CLASS_NAME.split(' ')

    expect(classNames).toContain('h-auto')
    expect(classNames).toContain('max-w-full')
    expect(classNames).not.toContain('w-full')
  })
})
