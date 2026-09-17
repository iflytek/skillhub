import type { ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ReviewProgressPage } from './review-progress'

const mocks = vi.hoisted(() => ({ suite: false, progressHook: vi.fn() }))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'zh' },
  }),
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  useSearch: () => ({}),
  Link: ({ children, to, className }: { children: ReactNode; to: string; className?: string }) => (
    <a href={to} className={className}>{children}</a>
  ),
}))

vi.mock('@/features/review/use-my-review-progress', () => ({
  useMyReviewProgress: (params: unknown) => {
    mocks.progressHook(params)
    return {
      data: {
        items: [{
          latestReviewTaskId: 42,
          skillId: mocks.suite ? undefined : 7,
          namespace: 'team-a',
          skillSlug: mocks.suite ? undefined : 'demo-skill',
          subjectType: mocks.suite ? 'SUITE_VERSION' : 'SKILL_VERSION',
          subjectId: 7,
          subjectSlug: mocks.suite ? 'demo-suite' : 'demo-skill',
          skillVersion: '1.2.0',
          latestStatus: 'REJECTED',
          latestReviewComment: 'Please clarify the permission requirements.',
          latestSubmittedAt: '2026-09-01T01:00:00Z',
          latestReviewedAt: '2026-09-01T02:00:00Z',
          attemptCount: 2,
        }],
        total: 1,
        page: 0,
        size: 20,
        statusCounts: {
          pending: 0,
          approved: 0,
          rejected: 1,
        },
      },
      isLoading: false,
      isError: false,
    }
  },
  useMyReviewAttempts: () => ({ data: [], isLoading: false, isError: false }),
}))

describe('ReviewProgressPage', () => {
  afterEach(() => mocks.progressHook.mockClear())

  it('renders a Suite name and dashboard link, never routes it to Skill upload', () => {
    mocks.suite = true
    try {
      const html = renderToStaticMarkup(<ReviewProgressPage />)
      expect(html).toContain('@team-a/demo-suite')
      expect(html).toContain('/dashboard/suites/$namespace/$slug')
      expect(html).toContain('suite.workspace.resourceSuite')
      expect(html).not.toContain('/dashboard/publish')
    } finally { mocks.suite = false }
  })
  it('renders the author-facing latest result and keeps reviewer management out of the page', () => {
    const html = renderToStaticMarkup(<ReviewProgressPage />)

    expect(html).toContain('reviewProgress.title')
    expect(html).toContain('@team-a/demo-skill')
    expect(html).toContain('v1.2.0')
    expect(html).toContain('reviewProgress.statusRejected')
    expect(html).toContain('reviewProgress.resubmit')
    expect(html).toContain('reviewProgress.history')
    expect(html).toContain('reviewProgress.statusSummary')
    expect(html).toContain('reviewProgress.typeFilter')
    expect(html).toContain('reviewProgress.latestReviewed')
    expect(html).toContain('/dashboard/publish')
    expect(html).not.toContain('reviews.typeSkill')
    expect(mocks.progressHook).toHaveBeenCalledWith({
      subjectType: undefined,
      status: undefined,
      q: undefined,
      page: 0,
      size: 20,
    })
  })
})
