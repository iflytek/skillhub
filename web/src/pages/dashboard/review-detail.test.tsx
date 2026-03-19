import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const navigateMock = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
  useParams: () => ({ id: '13' }),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, values?: Record<string, string>) =>
        values?.skill ? `${key}:${values.skill}` : key,
      i18n: { language: 'zh' },
    }),
  }
})

vi.mock('@/shared/lib/date-time', () => ({
  formatLocalDateTime: (value: string) => value,
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

vi.mock('@/features/review/review-error', () => ({
  resolveReviewActionErrorDescription: () => 'error',
}))

vi.mock('@/features/review/use-review-detail', () => ({
  useReviewDetail: () => ({
    data: {
      id: 13,
      namespace: 'global',
      skillSlug: 'demo-skill',
      version: '1.2.0',
      status: 'PENDING',
      submittedBy: 'local-admin',
      submittedByName: 'Local Admin',
      submittedAt: '2026-03-19T00:00:00Z',
      reviewedBy: null,
      reviewedByName: null,
      reviewedAt: null,
      reviewComment: null,
    },
    isLoading: false,
  }),
  useReviewSkillDetail: () => ({
    data: {
      skill: {
        id: 1,
        slug: 'demo-skill',
        displayName: 'Demo Skill',
        visibility: 'PUBLIC',
        status: 'ACTIVE',
        downloadCount: 3,
        starCount: 1,
        ratingCount: 0,
        hidden: false,
        namespace: 'global',
        canManageLifecycle: false,
        canSubmitPromotion: false,
        canInteract: false,
        canReport: false,
        resolutionMode: 'REVIEW_TASK',
      },
      versions: [
        {
          id: 10,
          version: '1.2.0',
          status: 'PENDING_REVIEW',
          changelog: 'Pending update',
          fileCount: 2,
          totalSize: 120,
          publishedAt: '2026-03-19T00:00:00Z',
          downloadAvailable: true,
        },
      ],
      files: [],
      documentationPath: 'README.md',
      documentationContent: '# Demo Skill',
      downloadUrl: '/api/v1/reviews/13/download',
      activeVersion: '1.2.0',
    },
    isLoading: false,
    error: null,
  }),
  useApproveReview: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
  useRejectReview: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}))

import { ReviewDetailPage } from './review-detail'

describe('ReviewDetailPage', () => {
  beforeEach(() => {
    navigateMock.mockReset()
  })

  it('uses a two-column desktop layout that keeps moderation controls in a sticky sidebar', () => {
    const html = renderToStaticMarkup(<ReviewDetailPage />)

    expect(html).toContain('xl:grid xl:grid-cols-[minmax(0,24rem)_minmax(0,1fr)]')
    expect(html).toContain('xl:sticky xl:top-6')
  })
})
