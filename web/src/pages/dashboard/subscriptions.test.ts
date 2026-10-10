// @vitest-environment jsdom

import { createElement } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const navigate = vi.fn()
const { useMySubscriptionsPage } = vi.hoisted(() => ({
  useMySubscriptionsPage: vi.fn(() => ({
    data: {
      items: [{ id: 1, namespace: 'team-a', slug: 'demo-skill' }],
      total: 1,
      page: 0,
      size: 12,
    },
    isLoading: false,
  })),
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigate,
  useSearch: () => ({ page: 1 }),
  useLocation: () => ({
    pathname: '/dashboard/subscriptions',
    searchStr: '?page=1',
    hash: '',
  }),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  }
})

vi.mock('@/features/skill/skill-card', () => ({
  SkillCard: ({ onClick }: { onClick?: () => void }) => createElement('button', { onClick }, 'skill-card'),
}))

vi.mock('@/shared/components/pagination', () => ({ Pagination: () => null }))
vi.mock('@/shared/hooks/use-user-queries', () => ({
  useMySubscriptionsPage,
}))
vi.mock('@/shared/ui/card', () => ({ Card: ({ children }: { children: unknown }) => children }))
vi.mock('@/shared/components/dashboard-page-header', () => ({ DashboardPageHeader: () => null }))

import { MySubscriptionsPage } from './subscriptions'

describe('MySubscriptionsPage', () => {
  beforeEach(() => navigate.mockClear())

  it('preserves the subscriptions page when opening a skill', () => {
    render(createElement(MySubscriptionsPage))

    fireEvent.click(screen.getByRole('button', { name: 'skill-card' }))

    expect(navigate).toHaveBeenCalledWith({
      to: '/space/team-a/demo-skill',
      search: { returnTo: '/dashboard/subscriptions?page=1' },
    })
  })

  it('uses the URL page as the query source', () => {
    render(createElement(MySubscriptionsPage))

    expect(useMySubscriptionsPage).toHaveBeenCalledWith({ page: 1, size: 12 })
  })
})
