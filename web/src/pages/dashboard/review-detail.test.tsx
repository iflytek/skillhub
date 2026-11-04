/** @vitest-environment jsdom */

import { renderToStaticMarkup } from 'react-dom/server'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const navigateMock = vi.fn()
const route = vi.hoisted(() => ({ id: '13' }))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
  useParams: (options?: { from?: string }) => (
    options?.from === '/dashboard/namespaces/$slug/reviews/$id'
      ? { id: route.id, slug: 'team-alpha' }
      : { id: route.id }
  ),
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

vi.mock('@tanstack/react-query', () => ({
  useQuery: () => ({ data: undefined, isLoading: false, error: null }),
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}))

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

const useReviewDetailMock = vi.fn<() => unknown>(() => ({
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
}))

const useReviewSkillDetailMock = vi.fn<() => unknown>(() => ({
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
}))

const useReviewAttemptsMock = vi.fn<() => unknown>(() => ({
  data: [{
    id: 13,
    skillVersionId: 10,
    namespace: 'global',
    skillSlug: 'demo-skill',
    version: '1.2.0',
    status: 'PENDING',
    submittedBy: 'local-admin',
    submittedAt: '2026-03-19T00:00:00Z',
  }],
  isLoading: false,
  isError: false,
}))
const useSuiteDetailMock = vi.fn<() => unknown>(() => ({ data: undefined, isLoading: false, refetch: vi.fn() }))

vi.mock('@/shared/hooks/use-suite-queries', () => ({
  useSuiteDetail: () => useSuiteDetailMock(),
}))

vi.mock('@/shared/components/confirm-dialog', () => ({
  ConfirmDialog: ({ open, title }: { open: boolean; title: string }) =>
    open ? <div role="dialog">{title}</div> : null,
}))

vi.mock('@/features/review/use-review-detail', () => ({
  useReviewDetail: () => useReviewDetailMock(),
  useReviewSkillDetail: () => useReviewSkillDetailMock(),
  useReviewAttempts: () => useReviewAttemptsMock(),
  useGithubSyncReviewSource: () => ({ data: undefined, isLoading: false, isError: false }),
  useApproveReview: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
  useRejectReview: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}))

const userMock = { platformRoles: ['SKILL_ADMIN'] as string[] }
vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ user: userMock }),
}))

// Mock hooks used directly by the review-detail page for file browser sidebar
const reviewFileMock = vi.fn((_taskId?: number, _path?: string | null, _enabled?: boolean) => ({ data: null, isLoading: false, error: null }))
vi.mock('@/features/review/use-review-file', () => ({
  useReviewFile: (...args: [number?, (string | null)?, boolean?]) => reviewFileMock(...args),
}))

vi.mock('@/api/client', () => ({
  buildApiUrl: (path: string) => path,
  WEB_API_PREFIX: '/api/web',
}))

import { NamespaceReviewDetailPage, ReviewDetailPage } from './review-detail'

describe('ReviewDetailPage', () => {
  beforeEach(() => {
    route.id = '13'
    navigateMock.mockReset()
    reviewFileMock.mockClear()
    userMock.platformRoles = ['SKILL_ADMIN']
    useReviewDetailMock.mockReset()
    useReviewSkillDetailMock.mockReset()
    useReviewAttemptsMock.mockReset()
    useSuiteDetailMock.mockReset()
    useSuiteDetailMock.mockReturnValue({ data: undefined, isLoading: false, refetch: vi.fn() })
    useReviewAttemptsMock.mockReturnValue({
      data: [{
        id: 13,
        skillVersionId: 10,
        namespace: 'global',
        skillSlug: 'demo-skill',
        version: '1.2.0',
        status: 'PENDING',
        submittedBy: 'local-admin',
        submittedAt: '2026-03-19T00:00:00Z',
      }],
      isLoading: false,
      isError: false,
    })
    useReviewDetailMock.mockReturnValue({
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
    })
    useReviewSkillDetailMock.mockReturnValue({
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
    })
  })

  afterEach(cleanup)

  it('clears comment, reject form and confirmation when the route switches to another review', () => {
    const view = render(<ReviewDetailPage />)
    fireEvent.change(screen.getByLabelText('review.commentLabel'), { target: { value: 'previous review reason' } })
    fireEvent.click(screen.getByRole('button', { name: /^review\.reject$/ }))
    fireEvent.click(screen.getByRole('button', { name: 'review.confirmReject' }))
    expect(screen.getByRole('dialog').textContent).toContain('review.rejectTitle')

    route.id = '14'
    view.rerender(<ReviewDetailPage />)

    expect((screen.getByLabelText('review.commentLabel') as HTMLTextAreaElement).value).toBe('')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByRole('button', { name: /^review\.reject$/ })).not.toBeNull()
  })

  it('shows a failed safety summary after review metadata fails instead of a permanent spinner', () => {
    useReviewSkillDetailMock.mockReturnValue({ data: undefined, isLoading: false, error: new Error('offline') })
    const html = renderToStaticMarkup(<ReviewDetailPage />)
    expect(html).toContain('review.scanLoadFailed')
    expect(html).not.toContain('review.scanLoading')
  })

  it('requires a loaded Suite snapshot before approving and offers a retry on failure', () => {
    const review = (useReviewDetailMock() as { data: Record<string, unknown> }).data
    useReviewDetailMock.mockReturnValue({ data: { ...review, subjectType: 'SUITE_VERSION', subjectSlug: 'suite-demo' }, isLoading: false })
    const refetch = vi.fn()
    useSuiteDetailMock.mockReturnValue({ data: undefined, isLoading: false, refetch })

    const view = render(<ReviewDetailPage />)
    expect((screen.getByRole('button', { name: 'review.approve' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: 'suite.reviewSnapshotRetry' }))
    expect(refetch).toHaveBeenCalledOnce()

    useSuiteDetailMock.mockReturnValue({ data: { members: [] }, isLoading: false, refetch })
    view.rerender(<ReviewDetailPage />)
    expect((screen.getByRole('button', { name: 'review.approve' }) as HTMLButtonElement).disabled).toBe(false)
  })

  it('renders the shared review overview and content tab in a single-column flow', () => {
    const html = renderToStaticMarkup(<ReviewDetailPage />)

    expect(html).toContain('flex min-h-[calc(100dvh-5.25rem)] w-full flex-col')
    expect(html).not.toContain('max-w-[1440px]')
    expect(html).toContain('review.tabContent')
    expect(html).toContain('review.tabChanges')
    expect(html).toContain('review.tabHistory')
    expect(html).toContain('sticky bottom-0')
    expect(html).toContain('review.commentLabel')
    expect(html).toContain('review.approve')
    expect(html).not.toContain('<aside')
  })

  it('renders not-found state when the review record is missing', () => {
    useReviewDetailMock.mockReturnValue({
      data: null,
      isLoading: false,
    })

    const html = renderToStaticMarkup(<ReviewDetailPage />)

    expect(html).toContain('review.notFound')
  })

  it('renders namespace review detail through the namespace route wrapper', () => {
    useReviewDetailMock.mockReturnValue({
      data: {
        id: 13,
        namespace: 'team-alpha',
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
    })

    const html = renderToStaticMarkup(<NamespaceReviewDetailPage />)

    expect(html).toContain('review.detail')
    expect(html).toContain('demo-skill')
  })

  it('redirects namespace reviews opened through the global route for namespace operators', () => {
    userMock.platformRoles = []
    useReviewDetailMock.mockReturnValue({
      data: {
        id: 13,
        namespace: 'team-alpha',
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
    })

    const html = renderToStaticMarkup(<ReviewDetailPage />)

    expect(html).toBe('')
  })

  it('shows not-found state when the namespace route slug does not match the review namespace', () => {
    useReviewDetailMock.mockReturnValue({
      data: {
        id: 13,
        namespace: 'other-team',
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
    })

    const html = renderToStaticMarkup(<NamespaceReviewDetailPage />)

    expect(html).toContain('review.notFound')
    expect(html).toContain('review.backToList')
  })

  it('disables approval and shows a scanning hint while the active review version is scanning', () => {
    useReviewSkillDetailMock.mockReturnValue({
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
            status: 'SCANNING',
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
    })

    const html = renderToStaticMarkup(<ReviewDetailPage />)

    expect(html).toContain('review.approveDisabledScanning')
    expect(html).toContain('disabled=""')
  })

  function metadataDetail(readyForApproval = true, removal = false) {
    useReviewDetailMock.mockReturnValue({ data: {
      id: 13, namespace: 'global', skillSlug: 'demo-skill', subjectType: 'SKILL_METADATA',
      skillVersionId: null, version: null, status: 'PENDING', submittedBy: 'owner', submittedAt: '2026-03-19T00:00:00Z',
    }, isLoading: false })
    useReviewSkillDetailMock.mockReturnValue({ data: {
      skill: { id: 1, displayName: 'Demo Skill', slug: 'demo-skill', namespace: 'global' },
      versions: [], files: [], documentationPath: null, documentationContent: null, downloadUrl: null, activeVersion: null,
      avatarChange: { candidateId: removal ? null : 42, currentAvatarUrl: '/current', candidatePreviewUrl: removal ? null : '/candidate', machineStatus: readyForApproval ? 'PASSED' : 'PROCESSING', reviewStatus: 'PENDING', readyForApproval, removal },
    }, isLoading: false, error: null })
  }

  it('renders metadata in the same task detail without package, file or version actions', () => {
    metadataDetail()
    const html = renderToStaticMarkup(<ReviewDetailPage />)
    expect(html.match(/<h2[^>]*>review.avatarUpdate<\/h2>/g)).toHaveLength(1)
    expect(html).not.toMatch(/<p\b[^>]*>(?:(?!<\/p>)[\s\S])*<div\b/)
    expect(html).toContain('review.metadataChange')
    expect(html).toContain('review.currentAvatar')
    expect(html).toContain('src="/candidate"')
    expect(html).toContain('review.approve')
    expect(html).not.toContain('review.downloadSkillZip')
    expect(html).not.toContain('review.activeReviewVersion')
    expect(html).not.toContain('review.skillDetailTitle')
    expect(html).not.toContain('fileTree.title')
    expect(reviewFileMock).toHaveBeenCalledWith(13, null, false)
  })

  it('blocks the shared approval action while avatar moderation is not ready', () => {
    metadataDetail(false)
    const html = renderToStaticMarkup(<ReviewDetailPage />)
    expect(html).toContain('review.approveDisabledAvatar')
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>review.approve<\/button>/)
    expect(html).toContain('review.reject')
  })

  it('shows avatar removal as returning to the default avatar without a candidate preview', () => {
    metadataDetail(true, true)
    const html = renderToStaticMarkup(<ReviewDetailPage />)
    expect(html).toContain('review.avatarRemoval')
    expect(html).not.toContain('src="/candidate"')
    expect(html).not.toContain('review.downloadSkillZip')
  })

  it('requires an explicit comment to override a machine-blocked avatar', () => {
    metadataDetail()
    const detail = useReviewSkillDetailMock() as { data: { avatarChange: Record<string, unknown> }; isLoading: boolean; error: null }
    useReviewSkillDetailMock.mockReturnValue({ ...detail, data: { ...detail.data, avatarChange: { ...detail.data.avatarChange, machineStatus: 'BLOCKED' } } })
    const html = renderToStaticMarkup(<ReviewDetailPage />)
    expect(html).toContain('review.avatarOverrideReason')
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>review.approve<\/button>/)
  })

  it('keeps visibility changes and their approval gate inside the unified detail tabs', () => {
    metadataDetail()
    const review = useReviewDetailMock() as { data: Record<string, unknown> }
    const detail = useReviewSkillDetailMock() as { data: Record<string, unknown> }
    useReviewDetailMock.mockReturnValue({ ...review, data: { ...review.data, previousVisibility: 'PRIVATE', requestedVisibility: 'PUBLIC' } })
    useReviewSkillDetailMock.mockReturnValue({ ...detail, data: { ...detail.data, avatarChange: null,
      visibilityChange: { previousVisibility: 'PRIVATE', requestedVisibility: 'PUBLIC', scanStatus: 'PROCESSING', readyForApproval: false },
    } })
    render(<ReviewDetailPage />)
    expect(screen.getByText('skillVisibility.PRIVATE → skillVisibility.PUBLIC')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'review.approve' }).hasAttribute('disabled')).toBe(true)
    expect(screen.getByRole('tab', { name: 'review.tabSourceSecurity' })).toBeTruthy()
    expect(screen.getByLabelText('review.commentLabel')).toBeTruthy()
  })

  it('enables the shared avatar decision only after the machine override explanation is entered', () => {
    metadataDetail()
    const detail = useReviewSkillDetailMock() as { data: { avatarChange: Record<string, unknown> } }
    useReviewSkillDetailMock.mockReturnValue({ ...detail, data: { ...detail.data,
      avatarChange: { ...detail.data.avatarChange, machineStatus: 'BLOCKED' },
    } })
    render(<ReviewDetailPage />)
    const approve = screen.getByRole('button', { name: 'review.approve' })
    expect(approve.hasAttribute('disabled')).toBe(true)
    fireEvent.change(screen.getByLabelText('review.commentLabel'), { target: { value: 'Reviewed the candidate and accept the exception.' } })
    expect(approve.hasAttribute('disabled')).toBe(false)
    fireEvent.click(approve)
    expect(screen.getByRole('dialog').textContent).toBe('review.approveTitle')
  })

})
