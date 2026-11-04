import { describe, expect, it } from 'vitest'
import { APP_FRAME_CLASS_NAME } from '@/shared/lib/layout-frame'
import { APP_HEADER_CONTENT_CLASS_NAME, APP_HEADER_ELEVATED_CLASS_NAME, getAppHeaderClassName } from './layout-header-style'

describe('getAppHeaderClassName', () => {
  it('keeps the header flat before the page starts scrolling', () => {
    const className = getAppHeaderClassName(false)

    expect(className).not.toContain(APP_HEADER_ELEVATED_CLASS_NAME)
    expect(className).toContain('bg-background/70')
    expect(className).not.toContain('bg-white')
  })

  it('keeps the header background full bleed and its controls inside the common frame', () => {
    expect(APP_HEADER_CONTENT_CLASS_NAME).toContain(APP_FRAME_CLASS_NAME)
    expect(APP_HEADER_CONTENT_CLASS_NAME).toContain('min-h-[56px]')
    expect(getAppHeaderClassName(false)).not.toContain('max-w-')
    expect(getAppHeaderClassName(false)).not.toMatch(/\b(?:sm:|md:|lg:)?px-/)
  })

  it('adds a subtle drop shadow after the header becomes sticky', () => {
    expect(getAppHeaderClassName(true)).toContain(APP_HEADER_ELEVATED_CLASS_NAME)
  })
})
