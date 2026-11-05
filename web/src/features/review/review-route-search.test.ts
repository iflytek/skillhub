import { describe, expect, it } from 'vitest'
import { buildReviewReturnPath, validateReviewSearch } from './review-route-search'

describe('review queue route context', () => {
  it('preserves applied scope, filters, sort and page on a detail return', () => {
    const url = new URL(buildReviewReturnPath({ namespace: 'product-design', q: '会议 & action', status: 'ALL', reviewKind: 'VERSION_UPDATE', originType: 'USER', sortDirection: 'ASC', page: '3' }), 'https://example.test')
    expect(url.pathname).toBe('/dashboard/reviews')
    expect(Object.fromEntries(url.searchParams)).toEqual({ namespace: 'product-design', q: '会议 & action', status: 'ALL', reviewKind: 'VERSION_UPDATE', originType: 'USER', sortDirection: 'ASC', page: '3' })
  })
  it('maps legacy namespace and sync links without arbitrary redirects', () => {
    const url = new URL(buildReviewReturnPath({ type: 'sync', returnTo: 'https://untrusted.test' }, 'engineering'), 'https://example.test')
    expect(url.pathname).toBe('/dashboard/reviews')
    expect(url.searchParams.get('namespace')).toBe('engineering')
    expect(url.searchParams.get('originType')).toBe('GITHUB_SYNC')
    expect(url.searchParams.has('returnTo')).toBe(false)
  })
  it.each([-1, 1.5, 'NaN', Infinity, ''])('rejects invalid page %s', (page) => {
    expect(validateReviewSearch({ page }).page).toBeUndefined()
  })
  it('does not pass unknown filters to consumers', () => {
    expect(validateReviewSearch({ status: 'WITHDRAWN', originType: 'foreign', extra: 'ignored' })).toEqual({ q: undefined, status: undefined, namespace: undefined, reviewKind: undefined, originType: undefined, sortDirection: undefined, page: undefined, type: undefined })
  })
})
