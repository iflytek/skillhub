import type { ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import * as mod from './user-menu'
import { UserMenu } from './user-menu'

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
  useMyNamespaces: () => ({ data: [] }),
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

describe('UserMenu navigation', () => {
  it('keeps dashboard-only personal links out of the compact avatar menu', () => {
    const html = renderToStaticMarkup(
      <UserMenu
        user={{
          displayName: 'Skill Author',
          platformRoles: ['USER'],
        }}
      />,
    )

    expect(html).toContain('user.menu.dashboard')
    expect(html).not.toContain('user.menu.reviewProgress')
    expect(html).not.toContain('user.menu.security')
  })

  it('keeps administrator links available to administrators', () => {
    const html = renderToStaticMarkup(
      <UserMenu
        user={{
          displayName: 'Administrator',
          platformRoles: ['SUPER_ADMIN'],
        }}
      />,
    )

    expect(html).toContain('user.menu.users')
    expect(html).toContain('user.menu.labels')
    expect(html).toContain('user.menu.namespacesAdmin')
    expect(html).toContain('user.menu.auditLog')
  })

  it.each(['SKILL_ADMIN', 'USER_ADMIN', 'SUPER_ADMIN'])(
    'keeps the review center available to %s users',
    (role) => {
      const html = renderToStaticMarkup(
        <UserMenu user={{ displayName: 'Reviewer', platformRoles: [role] }} />,
      )

      expect(html).toContain('href="/dashboard/reviews"')
      expect(html).toContain('user.menu.reviews')
    },
  )

  it('does not show the global review center to regular users', () => {
    const html = renderToStaticMarkup(
      <UserMenu user={{ displayName: 'Regular User', platformRoles: ['USER'] }} />,
    )

    expect(html).not.toContain('href="/dashboard/reviews"')
  })

  it('shows security settings only for accounts that can change a password', () => {
    const enabledHtml = renderToStaticMarkup(
      <UserMenu user={{ displayName: 'Local User', platformRoles: ['USER'], canChangePassword: true }} />,
    )
    const disabledHtml = renderToStaticMarkup(
      <UserMenu user={{ displayName: 'OAuth User', platformRoles: ['USER'], canChangePassword: false }} />,
    )

    expect(enabledHtml).toContain('user.menu.security')
    expect(disabledHtml).not.toContain('user.menu.security')
  })

  it('always keeps logout available', () => {
    const html = renderToStaticMarkup(
      <UserMenu
        user={{
          displayName: 'Local User',
          platformRoles: ['USER'],
        }}
      />,
    )

    expect(html).toContain('user.menu.logout')
  })
})
