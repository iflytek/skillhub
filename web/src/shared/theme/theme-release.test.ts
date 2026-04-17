import { describe, expect, it } from 'vitest'
import { THEME_TOGGLE_RELEASED } from './theme-release'

describe('THEME_TOGGLE_RELEASED', () => {
  it('keeps default production path disabled', () => {
    expect(THEME_TOGGLE_RELEASED).toBe(false)
  })
})
