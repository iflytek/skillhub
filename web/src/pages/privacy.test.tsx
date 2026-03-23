import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { resolvedLanguage: 'en' },
    }),
  }
})

vi.mock('@/shared/components/legal-document', () => ({
  LegalDocument: (props: { title: string; summary: string }) => (
    <div>
      <h1>{props.title}</h1>
      <p>{props.summary}</p>
    </div>
  ),
}))

import { PrivacyPolicyPage } from './privacy'

describe('PrivacyPolicyPage', () => {
  it('renders the english privacy policy for non-chinese locales', () => {
    const html = renderToStaticMarkup(<PrivacyPolicyPage />)

    expect(html).toContain('Privacy Policy')
    expect(html).toContain('This policy explains')
  })
})
