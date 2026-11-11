import { describe, expect, it } from 'vitest'
import { INPUT_BASE_CLASS_NAME } from './input'

describe('INPUT_BASE_CLASS_NAME', () => {
  it('uses the dedicated opaque placeholder token', () => {
    expect(INPUT_BASE_CLASS_NAME).toContain('placeholder:text-[hsl(var(--text-placeholder))]')
    expect(INPUT_BASE_CLASS_NAME).not.toContain('placeholder:text-muted-foreground/70')
  })
})
