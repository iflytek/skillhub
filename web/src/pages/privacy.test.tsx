import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'

const mockI18n = vi.hoisted(() => ({ resolvedLanguage: 'en' }))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: mockI18n,
    }),
  }
})

import { PrivacyPolicyPage } from './privacy'

describe('PrivacyPolicyPage', () => {
  it('renders the english privacy policy for the English locale', () => {
    mockI18n.resolvedLanguage = 'en'
    const html = renderToStaticMarkup(<PrivacyPolicyPage />)

    expect(html).toContain('Privacy Policy')
    expect(html).toContain('This policy explains')
  })

  it.each(['ko', 'ko-KR'])('renders the complete Korean policy for %s', (language) => {
    mockI18n.resolvedLanguage = language
    const html = renderToStaticMarkup(<PrivacyPolicyPage />)
    expect(html).toContain('개인정보처리방침')
    expect(html).toContain('8. 방침 업데이트 및 문의')
    expect(html).toContain('2026년 3월 14일')
    mockI18n.resolvedLanguage = 'en'
    const english = renderToStaticMarkup(<PrivacyPolicyPage />)
    expect((html.match(/<li>/g) ?? []).length).toBe((english.match(/<li>/g) ?? []).length)
  })

  it('preserves the English fallback for other languages', () => {
    mockI18n.resolvedLanguage = 'ru'
    expect(renderToStaticMarkup(<PrivacyPolicyPage />)).toContain('Privacy Policy')
  })
})
