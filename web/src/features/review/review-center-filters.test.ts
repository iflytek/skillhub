import { describe, expect, it } from 'vitest'
import { DEFAULT_REVIEW_FILTERS, parseReviewCenterFilters, reviewCenterSearch } from './review-center-filters'

describe('review center URL state', () => {
  it('normalizes unsupported values and preserves the legacy sync entry', () => {
    expect(parseReviewCenterFilters({ status: 'unknown', originType: 'unsafe', reviewKind: 'x', type: 'sync' })).toEqual({ ...DEFAULT_REVIEW_FILTERS, originType: 'GITHUB_SYNC' })
  })
  it('round trips non-default conditions and leaves defaults out of the URL', () => {
    const filters = { ...DEFAULT_REVIEW_FILTERS, status: 'APPROVED' as const, namespace: 'design', q: '  notes  ', originType: 'USER' as const }
    const search = reviewCenterSearch(filters, 2)
    expect(search).toMatchObject({ q: 'notes', status: 'APPROVED', namespace: 'design', originType: 'USER', page: 2 })
    expect(parseReviewCenterFilters(search)).toEqual({ ...filters, q: 'notes' })
    expect(reviewCenterSearch(DEFAULT_REVIEW_FILTERS).status).toBeUndefined()
  })
})
