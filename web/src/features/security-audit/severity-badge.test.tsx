import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { SeverityBadge } from './severity-badge'
import type { FindingSeverity } from './types'

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

describe('SeverityBadge', () => {
  const severities: FindingSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO']

  it.each(severities)('renders the translated label for %s severity', (severity) => {
    const html = renderToStaticMarkup(<SeverityBadge severity={severity} />)

    expect(html).toContain(`securityAudit.severity.${severity}`)
  })

  it('applies the red color classes for CRITICAL severity', () => {
    const html = renderToStaticMarkup(<SeverityBadge severity="CRITICAL" />)

    expect(html).toContain('text-red-700')
  })

  it('applies the orange color classes for HIGH severity', () => {
    const html = renderToStaticMarkup(<SeverityBadge severity="HIGH" />)

    expect(html).toContain('text-orange-700')
  })

  it('applies the amber color classes for MEDIUM severity', () => {
    const html = renderToStaticMarkup(<SeverityBadge severity="MEDIUM" />)

    expect(html).toContain('text-amber-700')
  })

  it('applies the blue color classes for LOW severity', () => {
    const html = renderToStaticMarkup(<SeverityBadge severity="LOW" />)

    expect(html).toContain('text-blue-700')
  })

  it('applies the gray color classes for INFO severity', () => {
    const html = renderToStaticMarkup(<SeverityBadge severity="INFO" />)

    expect(html).toContain('text-gray-700')
  })

  it('renders as a span with rounded-full pill styling', () => {
    const html = renderToStaticMarkup(<SeverityBadge severity="LOW" />)

    expect(html).toContain('rounded-full')
    expect(html).toContain('text-xs')
    expect(html).toContain('font-medium')
  })
})
