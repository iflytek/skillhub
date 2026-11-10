import { describe, expect, it } from 'vitest'
import { defaultSitePresentation, localizedPresentationText } from './use-site-presentation'

describe('site presentation defaults and locale fallback', () => {
  it('preserves landing sections and isolates mutable default maps', () => {
    const first = defaultSitePresentation()
    expect(first).toMatchObject({ siteName: null, logoUrl: null, faviconUrl: null, links: {} })
    expect(Object.keys(first.sections)).toEqual(['hero', 'capabilities', 'quickStart', 'popular', 'latest', 'enterprise', 'cta', 'footer'])
    expect(Object.values(first.sections).every(section => section.enabled)).toBe(true)
    first.sections.hero.texts.title = { en: 'Changed' }
    expect(defaultSitePresentation().sections.hero.texts).toEqual({})
  })
  it('uses current locale before English and falls back to product translation when both are empty', () => {
    expect(localizedPresentationText({ zh: ' 中文 ', en: 'English' }, 'zh-CN')).toBe('中文')
    expect(localizedPresentationText({ en: 'English', ru: 'Русский' }, 'ko')).toBe('English')
    expect(localizedPresentationText({ en: ' ', ko: '' }, 'ko')).toBeUndefined()
    expect(localizedPresentationText({}, undefined)).toBeUndefined()
  })
  it('keeps markup-looking text as text instead of interpreting it', () => {
    expect(localizedPresentationText({ en: '<script>alert(1)</script>' }, 'en')).toBe('<script>alert(1)</script>')
  })
})
