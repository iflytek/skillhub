import { describe, expect, it } from 'vitest'
import { INPUT_BASE_CLASS_NAME } from './input'

describe('INPUT_BASE_CLASS_NAME', () => {
  it('uses muted placeholder styling', () => {
    expect(INPUT_BASE_CLASS_NAME).toContain('placeholder:text-muted-foreground')
    expect(INPUT_BASE_CLASS_NAME).not.toContain('placeholder:text-[var(--text-placeholder)]')
  })
})
