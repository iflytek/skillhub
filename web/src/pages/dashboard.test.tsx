import { describe, expect, it, vi } from 'vitest'

// DashboardPage is a component-only page that wires auth context, skill previews,
// and token list. No exported pure functions or constants beyond the component.

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: unknown }) => children,
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
  useAuth: () => ({
    user: { userId: 'u1', displayName: 'Test User', platformRoles: ['USER'] },
  }),
}))

vi.mock('@/shared/hooks/use-user-queries', () => ({
  useMySkills: () => ({
    data: { items: [], total: 0, page: 0, size: 5 },
    isLoading: false,
  }),
}))

vi.mock('@/shared/lib/governance-access', () => ({
  canViewGovernanceCenter: () => false,
}))

vi.mock('@/shared/lib/skill-lifecycle', () => ({
  getHeadlineVersion: () => null,
}))

vi.mock('@/features/token/token-list', () => ({
  TokenList: () => null,
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: unknown }) => children,
  CardContent: ({ children }: { children: unknown }) => children,
  CardDescription: ({ children }: { children: unknown }) => children,
  CardHeader: ({ children }: { children: unknown }) => children,
  CardTitle: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/app/page-shell-style', () => ({
  APP_SHELL_PAGE_CLASS_NAME: 'page-shell',
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { DashboardPage } from './dashboard'

describe('DashboardPage', () => {
  it('exports a named component function', () => {
    expect(typeof DashboardPage).toBe('function')
  })

  it('renders the dashboard title and user info section', () => {
    const html = renderToStaticMarkup(<DashboardPage />)

    expect(html).toContain('dashboard.title')
    expect(html).toContain('dashboard.userInfo')
  })

  it('shows the my-skills preview section', () => {
    const html = renderToStaticMarkup(<DashboardPage />)

    expect(html).toContain('mySkills.title')
  })
})
