import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { ManagedNamespace } from '@/api/types'

const navigateMock = vi.fn()
const freezeMutateAsync = vi.fn()
const unfreezeMutateAsync = vi.fn()
const archiveMutateAsync = vi.fn()
const restoreMutateAsync = vi.fn()
const deleteMutateAsync = vi.fn()

let mockNamespaces: ManagedNamespace[] = []
let mockNamespacePage = {
  items: [] as ManagedNamespace[],
  total: 0,
  page: 0,
  size: 20,
}
let mockPlatformRoles: string[] = []

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
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
  useAuth: () => ({ hasRole: (role: string) => mockPlatformRoles.includes(role) }),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children, ...props }: { children: ReactNode }) => createElement('button', props, children),
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children, ...props }: { children: ReactNode }) => createElement('div', props, children),
}))

vi.mock('@/shared/components/namespace-badge', () => ({
  NamespaceBadge: ({ name }: { name: string }) => createElement('span', null, name),
}))

vi.mock('@/shared/components/empty-state', () => ({
  EmptyState: ({ title, description, action }: { title: string; description: string; action?: ReactNode }) => createElement(
    'section',
    null,
    title,
    description,
    action,
  ),
}))

vi.mock('@/shared/components/confirm-dialog', () => ({
  ConfirmDialog: () => null,
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: ({ title, subtitle, actions }: { title: string; subtitle: string; actions?: ReactNode }) => createElement(
    'header',
    null,
    title,
    subtitle,
    actions,
  ),
}))

vi.mock('@/features/namespace/create-namespace-dialog', () => ({
  CreateNamespaceDialog: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useArchiveNamespace: () => ({ mutateAsync: archiveMutateAsync }),
  useDeleteNamespace: () => ({ mutateAsync: deleteMutateAsync }),
  useFreezeNamespace: () => ({ mutateAsync: freezeMutateAsync }),
  useMyNamespacesPage: () => ({
    data: mockNamespacePage.total > 0 || mockNamespacePage.items.length > 0
      ? mockNamespacePage
      : {
          items: mockNamespaces,
          total: mockNamespaces.length,
          page: 0,
          size: 20,
        },
    isLoading: false,
  }),
  useRestoreNamespace: () => ({ mutateAsync: restoreMutateAsync }),
  useUnfreezeNamespace: () => ({ mutateAsync: unfreezeMutateAsync }),
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

import { MyNamespacesPage } from './my-namespaces'
import { executeNamespaceAction, resolveNamespaceActionCopy, resolveValidNamespacePage } from './my-namespaces'

function buildNamespace(overrides: Partial<ManagedNamespace> = {}): ManagedNamespace {
  return {
    id: 1,
    slug: 'team-ml',
    displayName: 'Team ML',
    description: 'namespace',
    type: 'TEAM',
    status: 'ACTIVE',
    createdAt: '2026-05-07T00:00:00Z',
    immutable: false,
    canFreeze: false,
    canUnfreeze: false,
    canArchive: false,
    canRestore: false,
    canDelete: false,
    ...overrides,
  }
}

describe('MyNamespacesPage', () => {
  beforeEach(() => {
    navigateMock.mockReset()
    freezeMutateAsync.mockReset()
    unfreezeMutateAsync.mockReset()
    archiveMutateAsync.mockReset()
    restoreMutateAsync.mockReset()
    deleteMutateAsync.mockReset()
    mockNamespaces = []
    mockNamespacePage = {
      items: mockNamespaces,
      total: 0,
      page: 0,
      size: 20,
    }
    mockPlatformRoles = []
  })

  it('exports a named component function', () => {
    expect(typeof MyNamespacesPage).toBe('function')
  })

  it('renders the delete action when the namespace is deletable', () => {
    mockNamespaces = [buildNamespace({ canDelete: true })]

    const html = renderToStaticMarkup(createElement(MyNamespacesPage))

    expect(html).toContain('myNamespaces.delete')
  })

  it('hides the delete action when the namespace is not deletable', () => {
    mockNamespaces = [buildNamespace({ canDelete: false })]

    const html = renderToStaticMarkup(createElement(MyNamespacesPage))

    expect(html).not.toContain('myNamespaces.delete')
  })

  it('hides namespace-scoped actions for super admins without namespace membership', () => {
    mockPlatformRoles = ['SUPER_ADMIN']
    mockNamespaces = [buildNamespace({ currentUserRole: undefined })]

    const html = renderToStaticMarkup(createElement(MyNamespacesPage))

    expect(html).toContain('Team ML')
    expect(html).toContain('myNamespaces.roleUnknown')
    expect(html).not.toContain('myNamespaces.manageMembers')
    expect(html).not.toContain('myNamespaces.reviewTasks')
  })

  it('shows namespace-scoped actions for namespace members', () => {
    mockNamespaces = [buildNamespace({ currentUserRole: 'ADMIN' })]
    mockNamespacePage = {
      items: mockNamespaces,
      total: 1,
      page: 0,
      size: 20,
    }

    const html = renderToStaticMarkup(createElement(MyNamespacesPage))

    expect(html).toContain('myNamespaces.manageMembers')
    expect(html).toContain('myNamespaces.reviewTasks')
  })

  it('renders pagination when more namespaces exist than the current page contains', () => {
    mockNamespaces = [buildNamespace({ id: 1, slug: 'team-a', displayName: 'Team A' })]
    mockNamespacePage = {
      items: mockNamespaces,
      total: 41,
      page: 1,
      size: 20,
    }

    const html = renderToStaticMarkup(createElement(MyNamespacesPage))

    expect(html).toContain('Team A')
    expect(html).toContain('pagination.prev')
    expect(html).toContain('pagination.next')
  })

  it('backs up to the last valid page when a delete empties the current page', () => {
    expect(resolveValidNamespacePage(2, 40, 20)).toBe(1)
    expect(resolveValidNamespacePage(1, 40, 20)).toBe(1)
    expect(resolveValidNamespacePage(1, 0, 20)).toBe(0)
  })

  it('routes delete actions to the delete mutation and emits success feedback', async () => {
    const t = (key: string) => key
    const copy = resolveNamespaceActionCopy(t, 'delete', 'Team ML')
    deleteMutateAsync.mockResolvedValueOnce(undefined)
    const success = vi.fn()
    const error = vi.fn()

    await executeNamespaceAction(
      { action: 'delete', slug: 'team-ml', name: 'Team ML' },
      {
        freeze: { mutateAsync: freezeMutateAsync },
        unfreeze: { mutateAsync: unfreezeMutateAsync },
        archive: { mutateAsync: archiveMutateAsync },
        restore: { mutateAsync: restoreMutateAsync },
        delete: { mutateAsync: deleteMutateAsync },
      },
      copy,
      { success, error },
    )

    expect(deleteMutateAsync).toHaveBeenCalledWith({ slug: 'team-ml' })
    expect(success).toHaveBeenCalledWith('myNamespaces.deleteSuccessTitle', 'myNamespaces.deleteSuccessDescription')
    expect(error).not.toHaveBeenCalled()
  })

  it('surfaces delete failures through error feedback and rethrows', async () => {
    const t = (key: string) => key
    const copy = resolveNamespaceActionCopy(t, 'delete', 'Team ML')
    deleteMutateAsync.mockRejectedValueOnce(new Error('blocked'))
    const success = vi.fn()
    const error = vi.fn()

    await expect(executeNamespaceAction(
      { action: 'delete', slug: 'team-ml', name: 'Team ML' },
      {
        freeze: { mutateAsync: freezeMutateAsync },
        unfreeze: { mutateAsync: unfreezeMutateAsync },
        archive: { mutateAsync: archiveMutateAsync },
        restore: { mutateAsync: restoreMutateAsync },
        delete: { mutateAsync: deleteMutateAsync },
      },
      copy,
      { success, error },
    )).rejects.toThrow('blocked')

    expect(error).toHaveBeenCalledWith('myNamespaces.deleteErrorTitle', 'blocked')
    expect(success).not.toHaveBeenCalled()
  })
})
