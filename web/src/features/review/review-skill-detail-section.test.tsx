import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { ReviewSkillDetail } from '@/api/types'
import { ReviewSkillDetailSection } from './review-skill-detail-section'

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
    expect(html).toContain('/api/v1/reviews/1/download')
  })

  it('renders the detail card as an independently scrollable pane on large screens', () => {
    const html = renderToStaticMarkup(<ReviewSkillDetailSection detail={createDetail()} />)

    expect(html).toContain('xl:max-h-[calc(100vh-8rem)]')
    expect(html).toContain('xl:overflow-y-auto')
  })

  it('renders inline error state without requiring detail data', () => {
    const html = renderToStaticMarkup(<ReviewSkillDetailSection hasError />)

    expect(html).toContain('Failed to load the skill detail for this review.')
  })
})
