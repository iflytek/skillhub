export const REVIEW_STATUSES = ['PENDING', 'APPROVED', 'REJECTED', 'ALL'] as const
export const REVIEW_KINDS = ['ALL', 'FIRST_PUBLISH', 'VERSION_UPDATE', 'SUITE_PUBLISH', 'VISIBILITY_CHANGE', 'AVATAR_CHANGE'] as const
export const REVIEW_ORIGINS = ['ALL', 'USER', 'GITHUB_SYNC'] as const

export type ReviewCenterStatus = typeof REVIEW_STATUSES[number]
export type ReviewCenterKind = typeof REVIEW_KINDS[number]
export type ReviewCenterOrigin = typeof REVIEW_ORIGINS[number]
export interface ReviewCenterFilters {
  q: string
  status: ReviewCenterStatus
  namespace: string
  reviewKind: ReviewCenterKind
  originType: ReviewCenterOrigin
  sortDirection: 'ASC' | 'DESC'
}

export const DEFAULT_REVIEW_FILTERS: ReviewCenterFilters = {
  q: '', status: 'PENDING', namespace: 'ALL', reviewKind: 'ALL', originType: 'ALL', sortDirection: 'DESC',
}

function choice<T extends string>(value: unknown, options: readonly T[], fallback: T): T {
  return options.find((option) => option === value) ?? fallback
}

export function parseReviewCenterFilters(search: Record<string, unknown>): ReviewCenterFilters {
  return {
    q: typeof search.q === 'string' ? search.q : '',
    status: choice(search.status, REVIEW_STATUSES, 'PENDING'),
    namespace: typeof search.namespace === 'string' && search.namespace.trim() ? search.namespace : 'ALL',
    reviewKind: choice(search.reviewKind, REVIEW_KINDS, 'ALL'),
    originType: choice(search.originType, REVIEW_ORIGINS, search.type === 'sync' ? 'GITHUB_SYNC' : 'ALL'),
    sortDirection: search.sortDirection === 'ASC' ? 'ASC' : 'DESC',
  }
}

export function reviewCenterSearch(filters: ReviewCenterFilters, page = 0) {
  return {
    q: filters.q.trim() || undefined,
    status: filters.status === 'PENDING' ? undefined : filters.status,
    namespace: filters.namespace === 'ALL' ? undefined : filters.namespace,
    reviewKind: filters.reviewKind === 'ALL' ? undefined : filters.reviewKind,
    originType: filters.originType === 'ALL' ? undefined : filters.originType,
    sortDirection: filters.sortDirection === 'DESC' ? undefined : filters.sortDirection,
    page: page > 0 ? page : undefined,
  }
}
