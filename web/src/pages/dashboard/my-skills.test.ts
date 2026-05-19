import { createElement, type ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const navigateMock = vi.fn()
const buttonRecords: Array<{ label: string; onClick?: ((event?: { stopPropagation: () => void }) => void) | undefined }> = []
const useMySkillsMock = vi.fn()

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
  useAuth: () => ({ hasRole: () => false }),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({
    children,
    onClick,
  }: {
    children?: ReactNode
    onClick?: (event?: { stopPropagation: () => void }) => void
  }) => {
    const label = Array.isArray(children) ? children.join('') : String(children ?? '')
    buttonRecords.push({ label, onClick })
    return createElement('button', null, children)
  },
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: ReactNode }) => createElement('div', null, children),
}))

vi.mock('@/shared/components/empty-state', () => ({
  EmptyState: () => createElement('div', null, 'empty-state'),
}))

vi.mock('@/shared/components/confirm-dialog', () => ({
  ConfirmDialog: () => null,
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: ({ actions }: { actions?: ReactNode }) => createElement('div', null, actions),
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
  useMySkills: () => useMySkillsMock(),
  useSubmitPromotion: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('@/shared/lib/skill-lifecycle', () => ({
  getHeadlineVersion: () => ({ id: 11, version: '1.0.0', status: 'PUBLISHED' }),
  getPublishedVersion: () => ({ id: 11, version: '1.0.0', status: 'PUBLISHED' }),
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

function findButton(label: string) {
  const record = buttonRecords.find((item) => item.label === label)
  if (!record) {
    throw new Error(`Missing button: ${label}`)
  }
  return record
}

describe('MySkillsPage', () => {
  beforeEach(() => {
    navigateMock.mockReset()
    buttonRecords.length = 0
    useMySkillsMock.mockReturnValue({
      data: {
        items: [
          {
            id: 1,
            displayName: 'Team Agent',
            summary: 'summary',
            namespace: 'team-ai',
            slug: 'team-agent',
            downloadCount: 42,
            status: 'PUBLISHED',
            visibility: 'PRIVATE',
            canSubmitPromotion: false,
          },
        ],
        total: 1,
        page: 0,
        size: 10,
      },
      isLoading: false,
    })
  })

  it('navigates to publish page with namespace and visibility when update is clicked', () => {
    renderToStaticMarkup(createElement(MySkillsPage))

    const stopPropagation = vi.fn()
    findButton('mySkills.update').onClick?.({ stopPropagation })

    expect(stopPropagation).toHaveBeenCalledTimes(1)
    expect(navigateMock).toHaveBeenCalledWith({
      to: '/dashboard/publish',
      search: {
        namespace: 'team-ai',
        visibility: 'PRIVATE',
      },
    })
  })

  it('does not render update action for archived skills', () => {
    useMySkillsMock.mockReturnValue({
      data: {
        items: [
          {
            id: 2,
            displayName: 'Archived Agent',
            summary: 'summary',
            namespace: 'team-ai',
            slug: 'archived-agent',
            downloadCount: 7,
            status: 'ARCHIVED',
            visibility: 'PUBLIC',
            canSubmitPromotion: false,
          },
        ],
        total: 1,
        page: 0,
        size: 10,
      },
      isLoading: false,
    })

    renderToStaticMarkup(createElement(MySkillsPage))

    expect(buttonRecords.some((button) => button.label === 'mySkills.update')).toBe(false)
  })

  it('falls back to public visibility when the skill card data has no visibility field', () => {
    useMySkillsMock.mockReturnValue({
      data: {
        items: [
          {
            id: 3,
            displayName: 'Default Visibility Agent',
            summary: 'summary',
            namespace: 'team-ai',
            slug: 'default-visibility-agent',
            downloadCount: 9,
            status: 'PUBLISHED',
            canSubmitPromotion: false,
          },
        ],
        total: 1,
        page: 0,
        size: 10,
      },
      isLoading: false,
    })

    renderToStaticMarkup(createElement(MySkillsPage))

    findButton('mySkills.update').onClick?.({ stopPropagation: vi.fn() })

    expect(navigateMock).toHaveBeenCalledWith({
      to: '/dashboard/publish',
      search: {
        namespace: 'team-ai',
        visibility: 'PUBLIC',
      },
    })
  })

  it('exports a named component function', () => {
    expect(typeof MySkillsPage).toBe('function')
  })
})
