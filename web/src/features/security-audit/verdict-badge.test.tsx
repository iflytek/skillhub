import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { VerdictBadge } from './verdict-badge'
import type { SecurityVerdict } from './types'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'en' },
    }),
  }
})

describe('VerdictBadge', () => {
  const verdicts: SecurityVerdict[] = ['SAFE', 'SUSPICIOUS', 'DANGEROUS', 'BLOCKED']

  it.each(verdicts)('renders the translated label for %s verdict', (verdict) => {
    const html = renderToStaticMarkup(<VerdictBadge verdict={verdict} />)

    expect(html).toContain(`securityAudit.verdict.${verdict}`)
  })

  it('applies emerald color classes for SAFE verdict', () => {
    const html = renderToStaticMarkup(<VerdictBadge verdict="SAFE" />)

    expect(html).toContain('text-emerald-700')
  })

  it('applies amber color classes for SUSPICIOUS verdict', () => {
    const html = renderToStaticMarkup(<VerdictBadge verdict="SUSPICIOUS" />)

    expect(html).toContain('text-amber-700')
  })

  it('applies orange color classes for DANGEROUS verdict', () => {
    const html = renderToStaticMarkup(<VerdictBadge verdict="DANGEROUS" />)

    expect(html).toContain('text-orange-700')
  })

  it('applies red color classes for BLOCKED verdict', () => {
    const html = renderToStaticMarkup(<VerdictBadge verdict="BLOCKED" />)

    expect(html).toContain('text-red-700')
  })

  it('renders as a span with rounded-full pill styling', () => {
    const html = renderToStaticMarkup(<VerdictBadge verdict="SAFE" />)

    expect(html).toContain('rounded-full')
    expect(html).toContain('text-sm')
    expect(html).toContain('font-medium')
  })
})
