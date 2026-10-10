import { describe, expect, it } from 'vitest'
import type { ReviewSkillDetail, SkillVersion } from '@/api/types'
import { getReviewDownloadHref, getReviewSkillDocumentation, isActiveReviewVersion } from './review-skill-detail'

function createVersion(overrides: Partial<SkillVersion> = {}): SkillVersion {
  return {
    id: 1,
    version: '1.2.0',
    status: 'PENDING_REVIEW',
    changelog: '',
    fileCount: 1,
    totalSize: 100,
    publishedAt: '2026-03-19T00:00:00Z',
    downloadAvailable: true,
    ...overrides,
  }
}

function createDetail(overrides: Partial<ReviewSkillDetail> = {}): ReviewSkillDetail {
  return {
    skill: {
      id: 1,
      slug: 'demo',
      displayName: 'Demo',
      visibility: 'PUBLIC',
      status: 'ACTIVE',
      downloadCount: 1,
      starCount: 0,
      ratingCount: 0,
      hidden: false,
      namespace: 'team-a',
      canManageLifecycle: false,
      canSubmitPromotion: false,
      canInteract: false,
      canReport: false,
      resolutionMode: 'REVIEW_TASK',
    },
    versions: [createVersion()],
    files: [],
    documentationPath: 'README.md',
    documentationContent: '# Demo',
    downloadUrl: '/api/v1/reviews/1/download',
    activeVersion: '1.2.0',
    ...overrides,
  }
}

describe('review skill detail helpers', () => {
  it('returns documentation payload when both path and content are present', () => {
    expect(getReviewSkillDocumentation(createDetail())).toEqual({
      path: 'README.md',
      content: '# Demo',
    })
  })

  it('returns null when documentation content is missing', () => {
    expect(getReviewSkillDocumentation(createDetail({ documentationContent: undefined }))).toBeNull()
  })

  it('marks the active review version by exact version match', () => {
    expect(isActiveReviewVersion(createVersion({ version: '1.2.0' }), createDetail())).toBe(true)
    expect(isActiveReviewVersion(createVersion({ version: '1.1.0' }), createDetail())).toBe(false)
  })

  it('returns the review download url unchanged', () => {
    expect(getReviewDownloadHref(createDetail())).toBe('/api/v1/reviews/1/download')
  })
})
