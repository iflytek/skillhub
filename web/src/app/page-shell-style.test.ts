import { describe, expect, it } from 'vitest'
import { APP_SHELL_PAGE_CLASS_NAME } from './page-shell-style'

describe('APP_SHELL_PAGE_CLASS_NAME', () => {
  it('keeps the upward float-in animation on stable app-shell pages', () => {
    expect(APP_SHELL_PAGE_CLASS_NAME).toContain('space-y-8')
    expect(APP_SHELL_PAGE_CLASS_NAME).toContain('animate-fade-up')
  })
})
