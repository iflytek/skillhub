import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
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
  it('renders the active review version and download link', () => {
    const html = renderToStaticMarkup(<ReviewSkillDetailSection detail={createDetail()} />)

    expect(html).toContain('1.2.0')
    expect(html).toContain('skillDetail.expandOverview')
  })

  it('renders the detail content inside a collapsed disclosure card by default', () => {
    const html = renderToStaticMarkup(<ReviewSkillDetailSection detail={createDetail()} />)

    expect(html).toContain('aria-expanded="false"')
    expect(html).not.toContain('data-review-skill-detail-panel')
    expect(html).not.toContain('/api/v1/reviews/1/download')
    expect(html).not.toContain('README.md')
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
