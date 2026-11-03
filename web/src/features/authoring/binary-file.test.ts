import { describe, expect, it } from 'vitest'
import { formatBytes } from './binary-file'

describe('formatBytes', () => {
  it('formats byte sizes for humans', () => {
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(2048)).toBe('2.0 KB')
    expect(formatBytes(3 * 1024 * 1024)).toBe('3.0 MB')
  })
})
