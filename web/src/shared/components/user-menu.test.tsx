import type { ReactNode } from 'react'
import type { ManagedNamespace } from '@/api/types'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as mod from './user-menu'
import { UserMenu } from './user-menu'

const useMyNamespacesPageMock = vi.hoisted(() => vi.fn(() => ({
  data: { items: [] as ManagedNamespace[], total: 0, page: 0, size: 1 },
})))

vi.mock('react', async () => {
  const actual = await vi.importActual<typeof import('react')>('react')
  return {
    ...actual,
    useState: (initialValue: unknown) => [
      typeof initialValue === 'boolean' ? true : initialValue,
      vi.fn(),
    ],
  }
})

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    children,
    className,
    onClick,
    to,
  }: {
    children: ReactNode
    className?: string
    onClick?: () => void
    to: string
  }) => (
    <a
      href={to}
      className={className}
      onClick={(event) => {
        event.preventDefault()
        onClick?.()
      }}
    >
      {children}
    </a>
  ),
}))

vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({
    setQueryData: vi.fn(),
  }),
}))

vi.mock('@/api/client', () => ({
  authApi: {
    logout: vi.fn(),
  },
}))

vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useMyNamespacesPage: useMyNamespacesPageMock,
}))

/**
 * UserMenu is a React component that renders a hover/click dropdown menu with
 * role-based navigation links (dashboard, reviews, admin, etc.) and logout.
 */
describe('user-menu module exports', () => {
  it('exports the UserMenu component', () => {
    expect(mod.UserMenu).toBeTypeOf('function')
  })
})

describe('UserMenu security settings visibility', () => {
  beforeEach(() => {
    useMyNamespacesPageMock.mockClear()
  })

  it('shows security settings when password changes are allowed, independent of OAuth provider', () => {
    const html = renderToStaticMarkup(
      <UserMenu
        user={{
          displayName: 'OAuth Linked User',
          oauthProvider: 'github',
          platformRoles: ['USER'],
          canChangePassword: true,
        }}
      />,
    )

    expect(html).toContain('user.menu.security')
  })

  it('hides security settings when password changes are not allowed, even for a local-looking account', () => {
    const html = renderToStaticMarkup(
      <UserMenu
        user={{
          displayName: 'Local User',
          platformRoles: ['USER'],
          canChangePassword: false,
        }}
      />,
    )

    expect(html).not.toContain('user.menu.security')
  })

  it('shows reviews for namespace admins without platform review roles', () => {
    useMyNamespacesPageMock.mockReturnValue({
      data: { items: [
        {
          id: 10,
          slug: 'team-admin',
          displayName: 'Team Admin',
          type: 'TEAM',
          status: 'ACTIVE',
          immutable: false,
          canFreeze: false,
          canUnfreeze: false,
          canArchive: false,
          canRestore: false,
          canDelete: false,
          currentUserRole: 'ADMIN',
          createdAt: '',
        },
      ], total: 1, page: 0, size: 1 },
    })

    const html = renderToStaticMarkup(
      <UserMenu
        user={{
          displayName: 'Namespace Admin',
          platformRoles: ['USER'],
        }}
      />,
    )

    expect(useMyNamespacesPageMock).toHaveBeenCalledWith({
      page: 0,
      size: 1,
      status: 'ACTIVE',
      type: 'TEAM',
      roles: ['OWNER', 'ADMIN'],
    }, true)
    expect(useMyNamespacesPageMock).toHaveBeenCalledWith({
      page: 0,
      size: 1,
      type: 'TEAM',
      roles: ['OWNER', 'ADMIN'],
    }, false)
    expect(html).toContain('user.menu.reviews')
  })

  it('disables namespace membership loading while rendering the global menu for platform reviewers', () => {
    renderToStaticMarkup(
      <UserMenu
        user={{
          displayName: 'Super Admin',
          platformRoles: ['SUPER_ADMIN'],
        }}
      />,
    )

    expect(useMyNamespacesPageMock).toHaveBeenCalledWith({
      page: 0,
      size: 1,
      status: 'ACTIVE',
      type: 'TEAM',
      roles: ['OWNER', 'ADMIN'],
    }, false)
  })
})
