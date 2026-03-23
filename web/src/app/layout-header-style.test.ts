import { describe, expect, it } from 'vitest'
import { APP_HEADER_ELEVATED_CLASS_NAME, getAppHeaderClassName } from './layout-header-style'

describe('getAppHeaderClassName', () => {
  it('keeps the header flat before the page starts scrolling', () => {
    expect(getAppHeaderClassName(false)).not.toContain(APP_HEADER_ELEVATED_CLASS_NAME)
  })

  it('adds a subtle drop shadow after the header becomes sticky', () => {
    expect(getAppHeaderClassName(true)).toContain(APP_HEADER_ELEVATED_CLASS_NAME)
  })
})
