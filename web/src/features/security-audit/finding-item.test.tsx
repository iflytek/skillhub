import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import type { SecurityFinding } from './types'
import { FindingItem } from './finding-item'

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

function createFinding(overrides: Partial<SecurityFinding> = {}): SecurityFinding {
  return {
    ruleId: 'SEC-001',
    severity: 'HIGH',
    category: 'injection',
    title: 'SQL Injection detected',
    message: null,
    filePath: null,
    lineNumber: null,
    codeSnippet: null,
    remediation: null,
    analyzer: null,
    metadata: {},
    ...overrides,
  }
}

describe('FindingItem', () => {
  it('renders the rule ID and title', () => {
    const html = renderToStaticMarkup(<FindingItem finding={createFinding()} />)

    expect(html).toContain('SEC-001')
    expect(html).toContain('SQL Injection detected')
  })

  it('renders the severity badge', () => {
    const html = renderToStaticMarkup(<FindingItem finding={createFinding()} />)

    expect(html).toContain('securityAudit.severity.HIGH')
  })

  it('renders the location when both filePath and lineNumber are present', () => {
    const finding = createFinding({ filePath: 'src/main.py', lineNumber: 42 })
    const html = renderToStaticMarkup(<FindingItem finding={finding} />)

    expect(html).toContain('src/main.py:42')
  })

  it('renders only the filePath when lineNumber is null', () => {
    const finding = createFinding({ filePath: 'src/main.py', lineNumber: null })
    const html = renderToStaticMarkup(<FindingItem finding={finding} />)

    expect(html).toContain('src/main.py')
    expect(html).not.toContain('src/main.py:')
  })

  it('omits the location span when both filePath and lineNumber are null', () => {
    const finding = createFinding({ filePath: null, lineNumber: null })
    const html = renderToStaticMarkup(<FindingItem finding={finding} />)

    // The location span should not be rendered at all.
    // Only the severity badge and ruleId code element should appear in the header row.
    const locationMatches = html.match(/text-muted-foreground/g) ?? []
    // Without location, there should be fewer muted-foreground elements
    expect(locationMatches.length).toBeLessThan(
      (renderToStaticMarkup(
        <FindingItem finding={createFinding({ filePath: 'a.py', lineNumber: 1 })} />
      ).match(/text-muted-foreground/g) ?? []).length
    )
  })

  it('renders the message when present', () => {
    const finding = createFinding({ message: 'Use parameterized queries' })
    const html = renderToStaticMarkup(<FindingItem finding={finding} />)

    expect(html).toContain('Use parameterized queries')
  })

  it('omits the message paragraph when message is null', () => {
    const finding = createFinding({ message: null })
    const html = renderToStaticMarkup(<FindingItem finding={finding} />)

    // Should still render the title but not an extra paragraph
    expect(html).toContain('SQL Injection detected')
  })

  it('renders the code snippet in a pre element when present', () => {
    const finding = createFinding({ codeSnippet: 'SELECT * FROM users WHERE id = ${input}' })
    const html = renderToStaticMarkup(<FindingItem finding={finding} />)

    expect(html).toContain('<pre')
    expect(html).toContain('SELECT * FROM users WHERE id = ${input}')
  })

  it('omits the code snippet when codeSnippet is null', () => {
    const finding = createFinding({ codeSnippet: null })
    const html = renderToStaticMarkup(<FindingItem finding={finding} />)

    expect(html).not.toContain('<pre')
  })

  it('renders the remediation section when present', () => {
    const finding = createFinding({ remediation: 'Use prepared statements' })
    const html = renderToStaticMarkup(<FindingItem finding={finding} />)

    expect(html).toContain('securityAudit.remediation')
    expect(html).toContain('Use prepared statements')
  })

  it('omits the remediation section when remediation is null', () => {
    const finding = createFinding({ remediation: null })
    const html = renderToStaticMarkup(<FindingItem finding={finding} />)

    expect(html).not.toContain('securityAudit.remediation')
  })
})
