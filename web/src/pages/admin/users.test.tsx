import { describe, expect, it, vi } from 'vitest'

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

vi.mock('@/shared/ui/dialog', () => ({
  Dialog: ({ children }: { children: unknown }) => children,
  DialogContent: ({ children }: { children: unknown }) => children,
  DialogDescription: ({ children }: { children: unknown }) => children,
  DialogFooter: ({ children }: { children: unknown }) => children,
  DialogHeader: ({ children }: { children: unknown }) => children,
  DialogTitle: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/label', () => ({
  Label: ({ children }: { children: unknown }) => children,
}))

const useAdminUsersMock = vi.fn()
vi.mock('@/features/admin/use-admin-users', () => ({
  useAdminUsers: () => useAdminUsersMock(),
  useApproveUser: () => ({ mutate: vi.fn(), isPending: false }),
  useDisableUser: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useEnableUser: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useTriggerUserPasswordReset: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateUserRole: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { AdminUsersPage } from './users'

describe('AdminUsersPage', () => {
  it('exports a named component function', () => {
    expect(typeof AdminUsersPage).toBe('function')
  })

  it('renders the empty state when no users are found', () => {
    useAdminUsersMock.mockReturnValue({
      data: { items: [], total: 0, page: 0, size: 20 },
      isLoading: false,
    })

    const html = renderToStaticMarkup(<AdminUsersPage />)
    expect(html).toContain('adminUsers.empty')
  })

  it('renders the page title and search UI', () => {
    useAdminUsersMock.mockReturnValue({
      data: null,
      isLoading: true,
    })

    const html = renderToStaticMarkup(<AdminUsersPage />)
    expect(html).toContain('adminUsers.title')
    expect(html).toContain('adminUsers.subtitle')
  })
})
