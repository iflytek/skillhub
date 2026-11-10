import en from '@/i18n/locales/en.json'
import zh from '@/i18n/locales/zh.json'
import ko from '@/i18n/locales/ko.json'
import ru from '@/i18n/locales/ru.json'
import { describe, expect, it, vi } from 'vitest'
import { defaultSitePresentation, presentationText } from '@/shared/hooks/use-site-presentation'
import { PRESENTATION_CONTENT, presentationItemVisible, presentationLinkUrl } from './presentation-content'
vi.mock('./base-path', () => ({ BASE_PATH: '/skillhub/', withBasePath: (value: string) => `/skillhub${value}` }))

describe('presentation allowlist and safe links', () => {
  it('contains the frozen 88 text slots and defaults absent items to visible', () => {
    expect(Object.values(PRESENTATION_CONTENT).reduce((sum, section) => sum + Object.keys(section).length, 0)).toBe(88)
    const value = defaultSitePresentation()
    expect(presentationItemVisible(value, 'hero', 'preview')).toBe(true)
    value.sections.hero.items = null
    expect(presentationItemVisible(value, 'hero', 'preview')).toBe(true)
    value.sections.hero.items = { preview: null }
    expect(presentationItemVisible(value, 'hero', 'preview')).toBe(true)
    value.sections.hero.items.preview = false
    expect(presentationItemVisible(value, 'hero', 'preview')).toBe(false)
  })
  it('has translated fallback copy and editor labels for all 88 slots in four languages', () => {
    for (const locale of [zh, en, ko, ru]) {
      const lookup = (key: string): string => {
        const value: unknown = key.split('.').reduce<unknown>((node, segment) => typeof node === 'object' && node !== null ? Reflect.get(node, segment) : undefined, locale)
        expect(typeof value, key).toBe('string')
        return String(value)
      }
      for (const [section, slots] of Object.entries(PRESENTATION_CONTENT)) {
        for (const [slot, fallback] of Object.entries(slots)) {
          if (!fallback.startsWith('=')) expect(lookup(fallback).length).toBeGreaterThan(0)
          const suffix = slot.endsWith('Description') ? 'Description' : slot.endsWith('Title') ? 'Title' : undefined
          const titleKey = suffix ? Reflect.get(slots, `${slot.slice(0, -suffix.length)}Title`) as string | undefined : undefined
          const label = suffix && titleKey && !titleKey.startsWith('=') ? suffix.toLowerCase() : slot
          expect(lookup(`sitePresentation.fields.${label}`).length, `${section}.${slot}`).toBeGreaterThan(0)
        }
      }
    }
  })
  it('uses locale, English, then default copy and replaces only a display brand name', () => {
    const value = defaultSitePresentation()
    value.sections.hero.texts.title = { zh: ' 中文 ', en: 'English' }
    expect(presentationText(value, 'hero', 'title', 'zh-CN', key => key)).toBe('中文')
    expect(presentationText(value, 'hero', 'title', 'ru', key => key)).toBe('English')
    value.siteName = 'Team Registry'
    expect(presentationText(value, 'enterprise', 'registryLabel', 'en', key => key)).toBe('Team Registry\nRegistry')
  })
  it('resolves root-relative frontend links and rejects executable or credentialed URLs', () => {
    const value = defaultSitePresentation()
    value.links.repository = '/source'
    expect(presentationLinkUrl(value, 'repository')).toBe('/skillhub/source')
    for (const unsafe of ['javascript:alert(1)', '//evil.test', '/\\evil.test', 'https://user:pass@host.test', 'https://team.test/\npath', 'data:text/html,test', '/../docs', '/%2e%2e/docs', '/%252e%252e/docs', '/a/../docs', '/%255c%255cevil.test']) {
      value.links.repository = unsafe
      expect(presentationLinkUrl(value, 'repository')).toBe('https://github.com/iflytek/skillhub')
    }
    value.links.repository = 'https://team.example/source'
    expect(presentationLinkUrl(value, 'repository')).toBe('https://team.example/source')
  })
})
