/** @vitest-environment jsdom */
import type { ReactNode } from 'react'
import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

let pathname = '/dashboard'
let resolvedPathname = '/dashboard'
let roles: string[] = []
let canChangePassword = false

vi.mock('@tanstack/react-router', () => ({
  Outlet: () => <div data-testid="outlet">page-content</div>,
  Link: ({ children, to, ...props }: { children: ReactNode; to: string }) => <a href={to} {...props}>{children}</a>,
  useRouterState: () => ({ pathname, resolvedPathname }),
}))
vi.mock('react-i18next', async () => ({
  ...await vi.importActual<typeof import('react-i18next')>('react-i18next'),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en', resolvedLanguage: 'en' } }),
}))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: { userId: 'test-reader', displayName: 'Reader', platformRoles: roles, canChangePassword }, isLoading: false }) }))
vi.mock('@/shared/components/language-switcher', () => ({ LanguageSwitcher: () => null }))
vi.mock('@/shared/components/theme-toggle', () => ({ ThemeToggle: () => null }))
vi.mock('@/shared/components/user-menu', () => ({ UserMenu: () => null }))
vi.mock('@/features/notification/notification-bell', () => ({ NotificationBell: () => null }))

import { Layout } from './layout'

beforeEach(() => { pathname = '/dashboard'; resolvedPathname = pathname; roles = []; canChangePassword = false })
afterEach(cleanup)

describe('shared console layout composition', () => {
  it.each(['/dashboard', '/dashboard/skills', '/dashboard/tokens', '/settings/profile'])('renders exactly one sidebar beside the content on %s', (path) => {
    pathname = path
    resolvedPathname = path
    render(<Layout />)
    expect(screen.getAllByRole('complementary')).toHaveLength(1)
    expect(within(screen.getByRole('complementary')).queryByTestId('outlet')).toBeNull()
    expect(screen.getByRole('main').contains(screen.getByTestId('outlet'))).toBe(true)
  })

  it.each(['/dashboard/publish', '/search', '/login', '/space/team/skill'])('does not add console navigation to %s', (path) => {
    pathname = path
    resolvedPathname = path
    render(<Layout />)
    expect(screen.queryByRole('complementary')).toBeNull()
  })

  it('retains role and account-capability filtering in the centralized sidebar', () => {
    const view = render(<Layout />)
    const sidebar = screen.getByRole('complementary')
    expect(within(sidebar).queryByText('sidebar.governance')).toBeNull()
    expect(within(sidebar).queryByText('sidebar.githubSync')).toBeNull()
    expect(within(sidebar).queryByText('sidebar.security')).toBeNull()
    roles = ['SKILL_ADMIN']
    canChangePassword = true
    view.rerender(<Layout />)
    expect(within(sidebar).getByText('sidebar.governance')).toBeTruthy()
    expect(within(sidebar).queryByText('sidebar.githubSync')).toBeNull()
    expect(within(sidebar).getByText('sidebar.security')).toBeTruthy()
    roles = ['SUPER_ADMIN']
    view.rerender(<Layout />)
    expect(within(sidebar).getByText('sidebar.githubSync')).toBeTruthy()
  })

  it('keeps the previous console shell while navigation to standalone publish is pending', () => {
    pathname = '/dashboard/publish'
    resolvedPathname = '/dashboard'
    render(<Layout />)
    expect(screen.getAllByRole('complementary')).toHaveLength(1)
  })
})
