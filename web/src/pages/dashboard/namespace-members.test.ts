import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'

const useMyNamespacesPageMock = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useParams: () => ({ slug: 'test-ns' }),
}))

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
  formatLocalDateTime: (v: string) => v,
}))

vi.mock('@/features/namespace/add-namespace-member-dialog', () => ({
  AddNamespaceMemberDialog: () => null,
}))

vi.mock('@/features/namespace/namespace-header', () => ({
  NamespaceHeader: () => null,
}))

vi.mock('@/shared/components/confirm-dialog', () => ({
  ConfirmDialog: () => null,
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: () => null,
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/select', () => ({
  Select: ({ children }: { children: unknown }) => children,
  SelectContent: ({ children }: { children: unknown }) => children,
  SelectItem: ({ children }: { children: unknown }) => children,
  SelectTrigger: ({ children }: { children: unknown }) => children,
  SelectValue: () => null,
}))

vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useMyNamespacesPage: (...args: unknown[]) => useMyNamespacesPageMock(...args),
  useNamespaceDetail: () => ({ data: null, isLoading: false }),
  useNamespaceMembers: () => ({ data: [], isLoading: false, error: null }),
  useRemoveNamespaceMember: () => ({ mutateAsync: vi.fn() }),
  useUpdateNamespaceMemberRole: () => ({ mutateAsync: vi.fn() }),
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

import { NamespaceMembersPage } from './namespace-members'

describe('NamespaceMembersPage', () => {
  it('exports a named component function', () => {
    expect(typeof NamespaceMembersPage).toBe('function')
  })

  it('loads only the current namespace membership entry', () => {
    useMyNamespacesPageMock.mockReturnValue({
      data: { items: [], total: 0, page: 0, size: 1 },
    })

    renderToStaticMarkup(createElement(NamespaceMembersPage))

    expect(useMyNamespacesPageMock).toHaveBeenCalledWith({ page: 0, size: 1, slug: 'test-ns' }, true)
  })
})
