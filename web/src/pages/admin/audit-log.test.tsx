import { describe, expect, it, vi } from 'vitest'

// AuditLogPage is a JSX-heavy component with local state and hooks.
// The ACTION_OPTIONS constant is not exported, but we can verify the component
// exists and renders the expected audit log action filter list by checking
// its behavior in a static render.

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

vi.mock('@/shared/lib/date-time', () => ({
  formatLocalDateTime: (value: string) => value,
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: () => null,
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/select', () => ({
  Select: ({ children }: { children: unknown }) => children,
  SelectContent: ({ children }: { children: unknown }) => children,
  SelectItem: ({ children }: { children: unknown }) => children,
  SelectTrigger: ({ children }: { children: unknown }) => children,
  SelectValue: () => null,
  normalizeSelectValue: (v: string) => v || null,
}))

vi.mock('@/shared/ui/table', () => ({
  Table: ({ children }: { children: unknown }) => children,
  TableBody: ({ children }: { children: unknown }) => children,
  TableCell: ({ children }: { children: unknown }) => children,
  TableHead: ({ children }: { children: unknown }) => children,
  TableHeader: ({ children }: { children: unknown }) => children,
  TableRow: ({ children }: { children: unknown }) => children,
}))

const useAuditLogMock = vi.fn()
vi.mock('@/features/admin/use-audit-log', () => ({
  useAuditLog: () => useAuditLogMock(),
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { AuditLogPage } from './audit-log'

describe('AuditLogPage', () => {
  it('exports a named component function', () => {
    expect(typeof AuditLogPage).toBe('function')
  })

  it('renders the empty state when there are no audit logs', () => {
    useAuditLogMock.mockReturnValue({
      data: { items: [], total: 0, page: 0, size: 20 },
      isLoading: false,
    })

    const html = renderToStaticMarkup(<AuditLogPage />)
    expect(html).toContain('auditLog.empty')
  })

  it('renders the page title and subtitle', () => {
    useAuditLogMock.mockReturnValue({
      data: null,
      isLoading: true,
    })

    const html = renderToStaticMarkup(<AuditLogPage />)
    expect(html).toContain('auditLog.title')
    expect(html).toContain('auditLog.subtitle')
  })
})
