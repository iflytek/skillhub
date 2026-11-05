/** @vitest-environment jsdom */
import { renderToStaticMarkup } from 'react-dom/server'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import type { ReviewSkillDetail } from '@/api/types'
import { ReviewSkillDetailSection } from './review-skill-detail-section'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, values?: Record<string, string>) =>
        values?.skill ? `${key}:${values.skill}` : key,
      i18n: { language: 'en' },
    }),
  }
})

const mocks = vi.hoisted(() => ({ file: vi.fn(), refetch: vi.fn() }))
vi.mock('./use-review-file', () => ({ useReviewFile: (...args: unknown[]) => mocks.file(...args) }))
beforeEach(() => { vi.clearAllMocks(); mocks.file.mockReturnValue({ data: null, isLoading: false, error: null, refetch: mocks.refetch }) })
afterEach(cleanup)

function createDetail(): ReviewSkillDetail {
  return {
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
      namespace: 'team-a',
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
    files: [
      {
        id: 1,
        filePath: 'README.md',
        fileSize: 120,
        contentType: 'text/markdown',
        sha256: 'sha',
      },
    ],
    documentationPath: 'README.md',
    documentationContent: '# Demo Skill',
    downloadUrl: '/api/v1/reviews/1/download',
    activeVersion: '1.2.0',
  }
}

describe('ReviewSkillDetailSection', () => {
  it('shows the effective review presentation before the original package files', () => {
    const detail = createDetail()
    detail.skill.summary = 'Old public summary'
    detail.presentation = { displayName: 'Pending display name', summary: 'Custom pending summary', overview: '# Custom pending overview', overviewSource: 'WEB' }
    detail.documentationContent = '# Original README content'
    const html = renderToStaticMarkup(<ReviewSkillDetailSection detail={detail} />)
    expect(html).toContain('Custom pending summary')
    expect(html).toContain('Custom pending overview')
    expect(html).not.toContain('Old public summary')
    expect(html).not.toContain('Original README content')
    expect(html).toContain('reviewWorkbench.browseFiles')
    expect(html).toContain('aria-expanded="false"')
  })

  it('renders the download link, file tree, and review-bound documentation together', () => {
    const html = renderToStaticMarkup(<ReviewSkillDetailSection detail={createDetail()} />)

    expect(html).toContain('/api/v1/reviews/1/download')
    expect(html).toContain('reviewWorkbench.browseFiles')
    expect(html).toContain('Demo Skill')
    expect(html).not.toContain('fileTree.title')
  })

  it('opens original files near the summary before a long overview and closes them from the same toolbar', () => {
    const detail = createDetail()
    detail.presentation = { displayName: 'Pending name', summary: 'Summary for review', overview: '# Long custom overview\n\n' + 'Overview paragraph. '.repeat(300), overviewSource: 'WEB' }
    detail.documentationContent = '# Original README content'
    render(<ReviewSkillDetailSection detail={detail} reviewId={1} />)
    const browse = screen.getByRole('button', { name: 'reviewWorkbench.browseFiles' })
    const summary = screen.getByRole('region', { name: 'reviewWorkbench.summary' })
    const overview = screen.getByRole('region', { name: 'reviewWorkbench.overview' })
    expect(browse.compareDocumentPosition(summary) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(screen.getAllByRole('link', { name: 'review.downloadSkillZip' })).toHaveLength(1)
    expect(screen.queryByText('Original README content')).toBeNull()
    fireEvent.click(browse)
    expect(browse.getAttribute('aria-expanded')).toBe('true')
    const files = screen.getByRole('region', { name: 'reviewWorkbench.packageFiles' })
    expect(files.compareDocumentPosition(overview) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(within(files).getByText('Original README content')).toBeTruthy()
    fireEvent.click(browse)
    expect(browse.getAttribute('aria-expanded')).toBe('false')
    expect(screen.queryByRole('region', { name: 'reviewWorkbench.packageFiles' })).toBeNull()
  })

  it('requests missing original documentation only after browsing, and preserves the preview size limit', () => {
    const detail = createDetail()
    detail.documentationContent = null
    const view = render(<ReviewSkillDetailSection detail={detail} reviewId={1} />)
    expect(mocks.file).toHaveBeenCalledWith(1, 'README.md', false)
    fireEvent.click(screen.getByRole('button', { name: 'reviewWorkbench.browseFiles' }))
    expect(mocks.file).toHaveBeenCalledWith(1, 'README.md', true)
    mocks.file.mockClear()
    view.rerender(<ReviewSkillDetailSection detail={detail} reviewId={1} documentationTooLarge />)
    expect(mocks.file).toHaveBeenCalledWith(1, 'README.md', false)
    expect(screen.getByRole('link', { name: 'filePreview.downloadFile' })).toBeTruthy()
  })

  it('keeps original-document retry and download available without replacing a custom overview', () => {
    const detail = createDetail()
    detail.documentationContent = null
    detail.presentation = { displayName: 'Pending name', summary: 'Custom summary', overview: '# Custom overview', overviewSource: 'WEB' }
    mocks.file.mockReturnValue({ data: null, isLoading: false, error: new Error('read failed'), refetch: mocks.refetch })
    render(<ReviewSkillDetailSection detail={detail} reviewId={1} />)
    fireEvent.click(screen.getByRole('button', { name: 'reviewWorkbench.browseFiles' }))
    expect(screen.getByText('review.documentationLoadFailed')).toBeTruthy()
    expect(screen.getByText('Custom overview')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'reviewWorkbench.retry' }))
    expect(mocks.refetch).toHaveBeenCalledOnce()
    expect(screen.getByRole('link', { name: 'filePreview.downloadFile' })).toBeTruthy()
  })

  it('renders loading state while review detail is being fetched', () => {
    const html = renderToStaticMarkup(<ReviewSkillDetailSection isLoading />)

    expect(html).toContain('animate-shimmer')
  })

  it('returns no markup when there is no detail data and no error', () => {
    const html = renderToStaticMarkup(<ReviewSkillDetailSection />)

    expect(html).toBe('')
  })

  it('renders inline error state without requiring detail data', () => {
    const html = renderToStaticMarkup(<ReviewSkillDetailSection hasError />)

    expect(html).toContain('review.skillDetailError')
  })
})
