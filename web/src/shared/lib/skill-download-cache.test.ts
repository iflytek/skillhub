import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import type { PagedResponse, SkillDetail, SkillSummary } from '@/api/types'
import { incrementSkillDownloadCount } from './skill-download-cache'

function createSkillSummary(overrides: Partial<SkillSummary> = {}): SkillSummary {
  return {
    id: 1,
    slug: 'demo-skill',
    displayName: 'Demo Skill',
    summary: 'summary',
    status: 'PUBLISHED',
    downloadCount: 10,
    starCount: 2,
    ratingAvg: 5,
    ratingCount: 1,
    latestVersion: '1.0.0',
    latestVersionId: 100,
    latestVersionStatus: 'PUBLISHED',
    namespace: 'team',
    updatedAt: '2026-03-16T00:00:00Z',
    canSubmitPromotion: false,
    ...overrides,
  }
}

function createSkillDetail(overrides: Partial<SkillDetail> = {}): SkillDetail {
  return {
    id: 1,
    slug: 'demo-skill',
    displayName: 'Demo Skill',
    summary: 'summary',
    visibility: 'PUBLIC',
    status: 'ACTIVE',
    downloadCount: 10,
    starCount: 2,
    ratingAvg: 5,
    ratingCount: 1,
    hidden: false,
    latestVersion: '1.0.0',
    latestVersionId: 100,
    namespace: 'team',
    canManageLifecycle: false,
    canSubmitPromotion: false,
    viewingVersionStatus: 'PUBLISHED',
    canInteract: true,
    ...overrides,
  }
}

describe('incrementSkillDownloadCount', () => {
  it('increments the skill detail and cached list entries for the downloaded skill', () => {
    const queryClient = new QueryClient()
    const searchPage: PagedResponse<SkillSummary> = {
      items: [
        createSkillSummary(),
        createSkillSummary({ id: 2, slug: 'other-skill', displayName: 'Other Skill', downloadCount: 4 }),
      ],
      total: 2,
      page: 0,
      size: 12,
    }

    queryClient.setQueryData(['skills', '@team', 'demo-skill'], createSkillDetail({ namespace: 'team' }))
    queryClient.setQueryData(['skills', 'my'], searchPage.items)
    queryClient.setQueryData(['skills', 'stars'], searchPage.items)
    queryClient.setQueryData(['skills', 'search', { q: '', sort: 'downloads', page: 0, size: 12, starredOnly: false }], searchPage)

    incrementSkillDownloadCount(queryClient, { namespace: '@team', slug: 'demo-skill' })

    expect(queryClient.getQueryData<SkillDetail>(['skills', '@team', 'demo-skill'])?.downloadCount).toBe(11)
    expect(queryClient.getQueryData<SkillSummary[]>(['skills', 'my'])?.[0]?.downloadCount).toBe(11)
    expect(queryClient.getQueryData<SkillSummary[]>(['skills', 'stars'])?.[0]?.downloadCount).toBe(11)
    expect(
      queryClient.getQueryData<PagedResponse<SkillSummary>>(
        ['skills', 'search', { q: '', sort: 'downloads', page: 0, size: 12, starredOnly: false }],
      )?.items[0]?.downloadCount,
    ).toBe(11)
    expect(
      queryClient.getQueryData<PagedResponse<SkillSummary>>(
        ['skills', 'search', { q: '', sort: 'downloads', page: 0, size: 12, starredOnly: false }],
      )?.items[1]?.downloadCount,
    ).toBe(4)
  })
})
