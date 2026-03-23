import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import type { SecurityAuditRecord } from './types'
import { SecurityAuditSummary } from './security-audit-summary'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, values?: Record<string, unknown>) =>
        values?.count !== undefined ? `${key}:${values.count}` : key,
      i18n: { language: 'en' },
    }),
  }
})

function createAudit(overrides: Partial<SecurityAuditRecord> = {}): SecurityAuditRecord {
  return {
    id: 1,
    scanId: 'scan-abc',
    scannerType: 'semgrep',
    verdict: 'SAFE',
    isSafe: true,
    maxSeverity: null,
    findingsCount: 0,
    findings: [],
    scanDurationSeconds: null,
    scannedAt: '2026-03-20T10:00:00Z',
    createdAt: '2026-03-20T10:00:00Z',
    ...overrides,
  }
}

let mockAudits: SecurityAuditRecord[] | undefined = undefined

vi.mock('./use-security-audit', () => ({
  useSecurityAudits: () => ({ data: mockAudits }),
}))

// Mock the Dialog components to avoid Radix UI portal / context issues in static render
vi.mock('@/shared/ui/dialog', () => ({
  Dialog: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  DialogContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DialogHeader: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DialogTitle: ({ children }: { children: React.ReactNode }) => <h2>{children}</h2>,
  DialogDescription: ({ children }: { children: React.ReactNode }) => <p>{children}</p>,
}))

// Mock the SecurityAuditSection to avoid nested hook dependencies
vi.mock('./security-audit-section', () => ({
  SecurityAuditSection: () => <div data-testid="audit-section" />,
}))

describe('SecurityAuditSummary', () => {
  it('returns null when audits is undefined', () => {
    mockAudits = undefined

    const html = renderToStaticMarkup(<SecurityAuditSummary skillId={1} versionId={10} />)

    expect(html).toBe('')
  })

  it('returns null when audits is an empty array', () => {
    mockAudits = []

    const html = renderToStaticMarkup(<SecurityAuditSummary skillId={1} versionId={10} />)

    expect(html).toBe('')
  })

  it('renders the security audit title when audits exist', () => {
    mockAudits = [createAudit()]

    const html = renderToStaticMarkup(<SecurityAuditSummary skillId={1} versionId={10} />)

    expect(html).toContain('securityAudit.title')
  })

  it('renders the scanner type for each audit', () => {
    mockAudits = [
      createAudit({ id: 1, scannerType: 'semgrep' }),
      createAudit({ id: 2, scannerType: 'trivy' }),
    ]

    const html = renderToStaticMarkup(<SecurityAuditSummary skillId={1} versionId={10} />)

    expect(html).toContain('semgrep')
    expect(html).toContain('trivy')
  })

  it('renders the verdict badge for each audit', () => {
    mockAudits = [createAudit({ verdict: 'BLOCKED' })]

    const html = renderToStaticMarkup(<SecurityAuditSummary skillId={1} versionId={10} />)

    expect(html).toContain('securityAudit.verdict.BLOCKED')
  })

  it('renders the total findings count across all audits', () => {
    mockAudits = [
      createAudit({ id: 1, findingsCount: 3 }),
      createAudit({ id: 2, findingsCount: 5 }),
    ]

    const html = renderToStaticMarkup(<SecurityAuditSummary skillId={1} versionId={10} />)

    expect(html).toContain('securityAudit.totalFindings:8')
  })

  it('renders zero total findings when all audits have zero findings', () => {
    mockAudits = [
      createAudit({ id: 1, findingsCount: 0 }),
      createAudit({ id: 2, findingsCount: 0 }),
    ]

    const html = renderToStaticMarkup(<SecurityAuditSummary skillId={1} versionId={10} />)

    expect(html).toContain('securityAudit.totalFindings:0')
  })

  it('renders the view details button', () => {
    mockAudits = [createAudit()]

    const html = renderToStaticMarkup(<SecurityAuditSummary skillId={1} versionId={10} />)

    expect(html).toContain('securityAudit.viewDetails')
  })

  it('renders the dialog with title and description', () => {
    mockAudits = [createAudit()]

    const html = renderToStaticMarkup(<SecurityAuditSummary skillId={1} versionId={10} />)

    expect(html).toContain('securityAudit.dialogDescription')
  })
})
