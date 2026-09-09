import { describe, expect, it } from 'vitest'
import { APP_HEADER_ELEVATED_CLASS_NAME, getAppHeaderClassName } from './layout-header-style'

describe('getAppHeaderClassName', () => {
  it('keeps the header flat before the page starts scrolling', () => {
    const className = getAppHeaderClassName(false)

    expect(className).not.toContain(APP_HEADER_ELEVATED_CLASS_NAME)
    expect(className).toContain('bg-background/70')
    expect(className).not.toContain('bg-white')
  })

  it('adds a subtle drop shadow after the header becomes sticky', () => {
    expect(getAppHeaderClassName(true)).toContain(APP_HEADER_ELEVATED_CLASS_NAME)
  })
})
