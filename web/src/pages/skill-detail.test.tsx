/** @vitest-environment jsdom */

import { renderToStaticMarkup } from 'react-dom/server'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MouseEvent, ReactNode } from 'react'
import type { SkillFile } from '@/api/types'

const toastMocks = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
}))

const navigateMock = vi.fn()
const hasRoleMock = vi.fn<(role: string) => boolean>((role: string) => role === 'USER')
const useSkillDetailMock = vi.fn()
const useSkillLabelsMock = vi.fn()
const useSkillVersionsMock = vi.fn()
const useSkillFilesMock = vi.fn()
const useSkillReadmeMock = vi.fn()
const useSkillFileMock = vi.fn()
let authState: {
  user: { userId: string; platformRoles: string[] } | null
  hasRole: (role: string) => boolean
} = {
  user: { userId: 'owner-1', platformRoles: ['USER'] },
  hasRole: hasRoleMock,
}

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
  useParams: () => ({ namespace: 'global', slug: 'demo-skill' }),
  useRouterState: () => ({ pathname: '/space/global/demo-skill', searchStr: '', hash: '' }),
  useSearch: () => ({ returnTo: '/dashboard/skills' }),
  Link: ({
    to,
    search,
    children,
    className,
  }: {
    to: string
    search?: Record<string, unknown>
    children?: ReactNode
    className?: string
  }) => (
    <a href={to} data-search={JSON.stringify(search ?? {})} className={className}>
      {children}
    </a>
  ),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'zh' },
    }),
  }
})

vi.mock('@tanstack/react-query', () => ({
  useQuery: () => ({ data: null, isLoading: false, error: null }),
  useMutation: () => ({ mutate: vi.fn(), isPending: false }),
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}))

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => authState,
}))

vi.mock('@/features/report/use-skill-reports', () => ({
  useSubmitSkillReport: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: toastMocks.success, error: toastMocks.error },
}))

vi.mock('@/api/client', () => ({
  adminApi: {
    hideSkill: vi.fn(),
    unhideSkill: vi.fn(),
    yankVersion: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    serverMessageKey?: string
  },
  buildApiUrl: (value: string) => value,
  WEB_API_PREFIX: '/api/web',
}))

vi.mock('@/shared/lib/date-time', () => ({
  formatLocalDateTime: (value: string) => value,
}))

vi.mock('@/shared/lib/skill-download-cache', () => ({
  incrementSkillDownloadCount: vi.fn(),
}))

vi.mock('@/shared/lib/number-format', () => ({
  formatCompactCount: (value: number) => String(value),
}))

vi.mock('@/features/skill/markdown-renderer', () => ({
  MarkdownRenderer: ({
    content,
    onLinkClick,
  }: {
    content: string
    onLinkClick?: (href: string, event: MouseEvent<HTMLAnchorElement>) => void
  }) => (
    <div>
      <div>markdown:{content}</div>
      <a href="docs/usage.md" onClick={(event) => onLinkClick?.('docs/usage.md', event)}>
        Usage
      </a>
      <a href="docs/missing.md" onClick={(event) => onLinkClick?.('docs/missing.md', event)}>
        Missing
      </a>
      <a
        href="#"
        onClick={(event) => {
          event.preventDefault()
          onLinkClick?.('https://example.com', event)
        }}
      >
        External
      </a>
      <a
        href="#intro"
        onClick={(event) => {
          event.preventDefault()
          onLinkClick?.('#intro', event)
        }}
      >
        Anchor
      </a>
    </div>
  ),
}))

vi.mock('@/features/skill/file-preview-dialog', () => ({
  FilePreviewDialog: ({
    open,
    node,
    onLinkClick,
  }: {
    open: boolean
    node: { path: string } | null
    onLinkClick?: (href: string, event: MouseEvent<HTMLAnchorElement>) => void
  }) => (
    open && node
      ? (
          <div role="dialog">
            preview:{node.path}
            {onLinkClick && (
              <a href="nested.md" onClick={(event) => onLinkClick('nested.md', event)}>
                Nested
              </a>
            )}
          </div>
        )
      : null
  ),
}))

vi.mock('@/features/skill/file-tree', () => ({
  FileTree: () => <div>files</div>,
}))

vi.mock('@/features/skill/install-command', () => ({
  InstallCommand: () => <div>install</div>,
}))

vi.mock('@/features/social/rating-input', () => ({
  RatingInput: () => <div>__RATING_WIDGET__</div>,
}))

vi.mock('@/features/social/star-button', () => ({
  StarButton: () => <div>__STAR_WIDGET__</div>,
}))

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useSkillDetail: () => useSkillDetailMock(),
  useSkillLabels: () => useSkillLabelsMock(),
  useVisibleLabels: () => ({
    data: [{ slug: 'code-generation', type: 'RECOMMENDED', displayName: 'Code Generation' }],
    isLoading: false,
  }),
  useAdminLabelDefinitions: () => ({ data: [], isLoading: false }),
  useAttachSkillLabel: () => ({ mutate: vi.fn(), isPending: false }),
  useDetachSkillLabel: () => ({ mutate: vi.fn(), isPending: false }),
  useSkillVersions: (...args: unknown[]) => useSkillVersionsMock(...args),
  useSkillVersionDetail: () => ({ data: undefined }),
  useSkillFiles: (...args: unknown[]) => useSkillFilesMock(...args),
  useSkillReadme: (...args: unknown[]) => useSkillReadmeMock(...args),
  useSkillFile: (...args: unknown[]) => useSkillFileMock(...args),
  useArchiveSkill: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteSkill: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteSkillVersion: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useRereleaseSkillVersion: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUnarchiveSkill: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useWithdrawSkillReview: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useSubmitForReview: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useConfirmPublish: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('@/shared/hooks/use-label-queries', () => ({
  useSkillLabels: () => useSkillLabelsMock(),
  useVisibleLabels: () => ({
    data: [{ slug: 'code-generation', type: 'RECOMMENDED', displayName: 'Code Generation' }],
    isLoading: false,
  }),
  useAdminLabelDefinitions: () => ({ data: [], isLoading: false }),
  useAttachSkillLabel: () => ({ mutate: vi.fn(), isPending: false }),
  useDetachSkillLabel: () => ({ mutate: vi.fn(), isPending: false }),
}))

vi.mock('@/shared/hooks/use-user-queries', () => ({
  useSubmitPromotion: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

import { SkillDetailPage } from './skill-detail'

function createSkill(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    slug: 'demo-skill',
    displayName: 'Demo Skill',
    ownerId: 'owner-1',
    ownerDisplayName: 'Owner One',
    summary: 'summary',
    visibility: 'PUBLIC',
    status: 'ACTIVE',
    downloadCount: 12,
    starCount: 2,
    ratingAvg: 4.5,
    ratingCount: 2,
    hidden: false,
    namespace: 'global',
    canManageLifecycle: true,
    canSubmitPromotion: false,
    canInteract: true,
    canReport: true,
    headlineVersion: { id: 10, version: '1.0.0', status: 'PUBLISHED' },
    publishedVersion: { id: 10, version: '1.0.0', status: 'PUBLISHED' },
    ownerPreviewVersion: undefined,
    resolutionMode: 'PUBLISHED',
    ...overrides,
  }
}

function createSkillFile(filePath: string): SkillFile {
  return {
    id: filePath.length,
    filePath,
    fileSize: 128,
    contentType: 'text/markdown',
    sha256: `sha-${filePath}`,
  }
}

describe('SkillDetailPage', () => {
  afterEach(() => cleanup())

  beforeEach(() => {
    navigateMock.mockReset()
    useSkillFilesMock.mockReset()
    useSkillReadmeMock.mockReset()
    useSkillFileMock.mockReset()
    toastMocks.success.mockReset()
    toastMocks.error.mockReset()
    hasRoleMock.mockImplementation((role: string) => role === 'USER')
    authState = {
      user: { userId: 'owner-1', platformRoles: ['USER'] },
      hasRole: hasRoleMock,
    }
    useSkillDetailMock.mockReturnValue({
      data: createSkill(),
      isLoading: false,
      isFetching: false,
      error: null,
    })
    useSkillVersionsMock.mockReturnValue({
      data: [
        {
          id: 10,
          version: '1.0.0',
          status: 'PUBLISHED',
          changelog: '',
          fileCount: 1,
          totalSize: 12,
          publishedAt: '2026-03-20T00:00:00Z',
          downloadAvailable: true,
        },
      ],
    })
    useSkillLabelsMock.mockReturnValue({
      data: undefined,
    })
    useSkillFilesMock.mockReturnValue({ data: [] })
    useSkillReadmeMock.mockReturnValue({ data: '# Demo', error: null })
    useSkillFileMock.mockReturnValue({ data: null, isLoading: false, error: null })
  })

  it('shows hard delete action for the skill owner', () => {
    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).toContain('skillDetail.deleteSkill')
  })

  it('hides hard delete action when the viewer is not the owner or super admin', () => {
    useSkillDetailMock.mockReturnValue({
      data: createSkill({ ownerId: 'someone-else' }),
      isLoading: false,
      error: null,
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).not.toContain('skillDetail.deleteSkill')
  })

  it('renders public skill details for an anonymous viewer', () => {
    authState = {
      user: null,
      hasRole: vi.fn(() => false),
    }

    useSkillDetailMock.mockReturnValue({
      data: createSkill({
        canManageLifecycle: false,
        canInteract: true,
        visibility: 'PUBLIC',
      }),
      isLoading: false,
      isFetching: false,
      error: null,
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).toContain('Demo Skill')
    expect(html).toContain('install')
    expect(html).not.toContain('skillDetail.loginRequired')
    expect(html).not.toContain('skillDetail.deleteSkill')
  })

  it('wraps a long skill name instead of widening the mobile page', () => {
    useSkillDetailMock.mockReturnValue({
      data: createSkill({ displayName: 'review-runtime-1788284593-353294' }),
      isLoading: false,
      isFetching: false,
      error: null,
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).toContain('text-balance break-words text-4xl')
    expect(html).toContain('[overflow-wrap:anywhere]')
  })

  it('shows the label management panel for a user who can manage the skill lifecycle', () => {
    useSkillDetailMock.mockReturnValue({
      data: createSkill({
        labels: [{ slug: 'official', type: 'RECOMMENDED', displayName: 'Official' }],
      }),
      isLoading: false,
      error: null,
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).toContain('skillDetail.labelsSectionTitle')
    expect(html).toContain('skillDetail.removeLabel')
    expect(html).toContain('skillDetail.addLabel')
  })

  it('links skill label chips to the search page filtered by that label', () => {
    useSkillDetailMock.mockReturnValue({
      data: createSkill({
        ownerId: 'someone-else',
        canManageLifecycle: false,
        labels: [{ slug: 'code-generation', type: 'RECOMMENDED', displayName: 'Code Generation' }],
      }),
      isLoading: false,
      isFetching: false,
      error: null,
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).toContain('href="/search"')
    expect(html).toContain('&quot;label&quot;:&quot;code-generation&quot;')
    expect(html).toContain('Code Generation')
  })

  it('hides the label management panel when the viewer lacks label permissions', () => {
    useSkillDetailMock.mockReturnValue({
      data: createSkill({
        ownerId: 'someone-else',
        canManageLifecycle: false,
      }),
      isLoading: false,
      error: null,
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).not.toContain('skillDetail.labelsSectionTitle')
  })

  it('does not render dependent social controls while the detail query is still refetching', () => {
    useSkillDetailMock.mockReturnValue({
      data: createSkill(),
      isLoading: false,
      isFetching: true,
      error: null,
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(useSkillVersionsMock).toHaveBeenCalledWith('global', 'demo-skill', false)
    expect(html).not.toContain('__STAR_WIDGET__')
    expect(html).not.toContain('__RATING_WIDGET__')
  })

  it('renders rejected owner preview without pending-review copy', () => {
    useSkillDetailMock.mockReturnValue({
      data: createSkill({
        canInteract: false,
        headlineVersion: { id: 11, version: '1.1.0', status: 'REJECTED' },
        publishedVersion: undefined,
        ownerPreviewVersion: { id: 11, version: '1.1.0', status: 'REJECTED' },
        resolutionMode: 'OWNER_PREVIEW',
        ownerPreviewReviewComment: 'manifest validation failed',
      }),
      isLoading: false,
      isFetching: false,
      error: null,
    })
    useSkillVersionsMock.mockReturnValue({
      data: [
        {
          id: 11,
          version: '1.1.0',
          status: 'REJECTED',
          changelog: '',
          fileCount: 1,
          totalSize: 12,
          publishedAt: null,
          downloadAvailable: false,
        },
      ],
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).toContain('skillDetail.rejectedBadge')
    expect(html).toContain('skillDetail.rejectedFeedbackTitle')
    expect(html).toContain('manifest validation failed')
    expect(html).not.toContain('skillDetail.pendingPreviewBadge')
    expect(html).not.toContain('skillDetail.pendingPreviewTitle')
  })

  it('renders pending review status in the header for scan-failed owner preview versions', () => {
    useSkillDetailMock.mockReturnValue({
      data: createSkill({
        canInteract: false,
        headlineVersion: { id: 12, version: '1.2.0', status: 'SCAN_FAILED' },
        publishedVersion: undefined,
        ownerPreviewVersion: { id: 12, version: '1.2.0', status: 'SCAN_FAILED' },
        resolutionMode: 'OWNER_PREVIEW',
      }),
      isLoading: false,
      isFetching: false,
      error: null,
    })
    useSkillVersionsMock.mockReturnValue({
      data: [
        {
          id: 12,
          version: '1.2.0',
          status: 'SCAN_FAILED',
          changelog: '',
          fileCount: 1,
          totalSize: 12,
          publishedAt: null,
          downloadAvailable: false,
        },
      ],
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).toContain('skillDetail.versionStatusPendingReview')
    expect(html).not.toContain('skillDetail.versionStatusScanFailed')
  })

  it('allows long pending review versions to wrap inside the review card', () => {
    useSkillDetailMock.mockReturnValue({
      data: createSkill({
        headlineVersion: { id: 13, version: '20260326.055640-build-with-very-long-suffix', status: 'PENDING_REVIEW' },
        publishedVersion: { id: 10, version: '20260326.055538', status: 'PUBLISHED' },
        ownerPreviewVersion: { id: 13, version: '20260326.055640-build-with-very-long-suffix', status: 'PENDING_REVIEW' },
        resolutionMode: 'PUBLISHED',
      }),
      isLoading: false,
      isFetching: false,
      error: null,
    })
    useSkillVersionsMock.mockReturnValue({
      data: [
        {
          id: 13,
          version: '20260326.055640-build-with-very-long-suffix',
          status: 'PENDING_REVIEW',
          changelog: '',
          fileCount: 1,
          totalSize: 12,
          publishedAt: null,
          downloadAvailable: false,
        },
        {
          id: 10,
          version: '20260326.055538',
          status: 'PUBLISHED',
          changelog: '',
          fileCount: 1,
          totalSize: 12,
          publishedAt: '2026-03-20T00:00:00Z',
          downloadAvailable: true,
        },
      ],
    })

    const html = renderToStaticMarkup(<SkillDetailPage />)

    expect(html).toContain('break-all')
    expect(html).toContain('leading-snug')
  })

  it('opens a file preview when overview markdown relative link matches a package file', () => {
    useSkillFilesMock.mockReturnValue({
      data: [
        createSkillFile('README.md'),
        createSkillFile('docs/usage.md'),
      ],
    })

    render(<SkillDetailPage />)
    fireEvent.click(screen.getByRole('link', { name: 'Usage' }))

    expect(screen.getByRole('dialog').textContent).toContain('preview:docs/usage.md')
    expect(toastMocks.error).not.toHaveBeenCalled()
  })

  it('resolves links inside previewed markdown files against the previewed file path', () => {
    useSkillFilesMock.mockReturnValue({
      data: [
        createSkillFile('README.md'),
        createSkillFile('docs/usage.md'),
        createSkillFile('docs/nested.md'),
      ],
    })

    render(<SkillDetailPage />)
    fireEvent.click(screen.getByRole('link', { name: 'Usage' }))
    expect(screen.getByRole('dialog').textContent).toContain('preview:docs/usage.md')

    fireEvent.click(screen.getByRole('link', { name: 'Nested' }))

    expect(screen.getByRole('dialog').textContent).toContain('preview:docs/nested.md')
    expect(toastMocks.error).not.toHaveBeenCalled()
  })

  it('keeps the viewer on the detail page and shows a toast for missing package files', () => {
    useSkillFilesMock.mockReturnValue({
      data: [
        createSkillFile('README.md'),
        createSkillFile('docs/usage.md'),
      ],
    })

    render(<SkillDetailPage />)
    fireEvent.click(screen.getByRole('link', { name: 'Missing' }))

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(toastMocks.error).toHaveBeenCalledWith(
      'skillDetail.packageLinkMissingTitle',
      'skillDetail.packageLinkMissingDescription',
    )
  })

  it('leaves external links and same-document anchors alone', () => {
    useSkillFilesMock.mockReturnValue({
      data: [
        createSkillFile('README.md'),
        createSkillFile('docs/usage.md'),
      ],
    })

    render(<SkillDetailPage />)
    fireEvent.click(screen.getByRole('link', { name: 'External' }))
    fireEvent.click(screen.getByRole('link', { name: 'Anchor' }))

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(toastMocks.error).not.toHaveBeenCalled()
  })
})
