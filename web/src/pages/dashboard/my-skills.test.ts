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

vi.mock('@/shared/components/empty-state', () => ({
  EmptyState: () => null,
}))

vi.mock('@/shared/components/confirm-dialog', () => ({
  ConfirmDialog: () => null,
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: () => null,
}))

vi.mock('@/shared/components/pagination', () => ({
  Pagination: () => null,
}))

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useArchiveSkill: () => ({ mutateAsync: vi.fn() }),
  useUnarchiveSkill: () => ({ mutateAsync: vi.fn() }),
  useWithdrawSkillReview: () => ({ mutateAsync: vi.fn() }),
}))

vi.mock('@/shared/hooks/use-user-queries', () => ({
  useMySkills: () => ({
    data: { items: [], total: 0, page: 0, size: 10 },
    isLoading: false,
  }),
  useSubmitPromotion: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('@/shared/lib/skill-lifecycle', () => ({
  getHeadlineVersion: () => null,
  getPublishedVersion: () => null,
  getOwnerPreviewVersion: () => null,
  hasPendingOwnerPreview: () => false,
}))

vi.mock('@/shared/lib/number-format', () => ({
  formatCompactCount: (v: number) => String(v),
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/api/client', () => ({
  ApiError: class ApiError extends Error {
    serverMessageKey?: string
  },
}))

import { MySkillsPage } from './my-skills'

describe('MySkillsPage', () => {
  it('exports a named component function', () => {
    expect(typeof MySkillsPage).toBe('function')
  })
})
