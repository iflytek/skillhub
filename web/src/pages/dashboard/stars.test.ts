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

vi.mock('@/features/skill/skill-card', () => ({
  SkillCard: () => null,
}))

vi.mock('@/shared/components/pagination', () => ({
  Pagination: () => null,
}))

vi.mock('@/shared/hooks/use-user-queries', () => ({
  useMyStarsPage: () => ({
    data: { items: [], total: 0, page: 0, size: 12 },
    isLoading: false,
  }),
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: () => null,
}))

import { MyStarsPage } from './stars'

describe('MyStarsPage', () => {
  it('exports a named component function', () => {
    expect(typeof MyStarsPage).toBe('function')
  })
})
