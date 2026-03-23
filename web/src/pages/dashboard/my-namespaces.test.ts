import { describe, expect, it, vi } from 'vitest'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ hasRole: () => false }),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/components/namespace-badge', () => ({
  NamespaceBadge: () => null,
}))

vi.mock('@/shared/components/empty-state', () => ({
  EmptyState: () => null,
}))

vi.mock('@/shared/components/confirm-dialog', () => ({
  ConfirmDialog: () => null,
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: () => null,
}))

vi.mock('@/features/namespace/create-namespace-dialog', () => ({
  CreateNamespaceDialog: () => null,
}))

vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useArchiveNamespace: () => ({ mutateAsync: vi.fn() }),
  useFreezeNamespace: () => ({ mutateAsync: vi.fn() }),
  useMyNamespaces: () => ({ data: [], isLoading: false }),
  useRestoreNamespace: () => ({ mutateAsync: vi.fn() }),
  useUnfreezeNamespace: () => ({ mutateAsync: vi.fn() }),
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

import { MyNamespacesPage } from './my-namespaces'

describe('MyNamespacesPage', () => {
  it('exports a named component function', () => {
    expect(typeof MyNamespacesPage).toBe('function')
  })
})
