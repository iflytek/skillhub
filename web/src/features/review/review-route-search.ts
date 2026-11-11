import { parseReviewCenterFilters, reviewCenterSearch, type ReviewCenterStatus, type ReviewCenterKind, type ReviewCenterOrigin } from './review-center-filters'

export interface ReviewRouteSearch {
  q?: string
  namespace?: string
  status?: ReviewCenterStatus
  reviewKind?: ReviewCenterKind
  originType?: ReviewCenterOrigin
  sortDirection?: 'ASC' | 'DESC'
  page?: number
  type?: 'skill' | 'profile' | 'sync'
}

/** Preserve queue context while keeping detail returns on a fixed, trusted route. */
export function validateReviewSearch(search: Record<string, unknown>): ReviewRouteSearch {
  const candidate = typeof search.page === 'string' && search.page.trim() ? Number(search.page) : search.page
  const page = typeof candidate === 'number' && Number.isSafeInteger(candidate) && candidate > 0 ? candidate : 0
  return {
    ...reviewCenterSearch(parseReviewCenterFilters(search), page),
    type: search.type === 'skill' || search.type === 'profile' || search.type === 'sync' ? search.type : undefined,
  }
}

export function buildReviewReturnPath(search: Record<string, unknown>, namespaceSlug?: string): string {
  const normalized = validateReviewSearch({ ...search, namespace: search.namespace ?? namespaceSlug })
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(normalized)) {
    if (value !== undefined) params.set(key, String(value))
  }
  return `/dashboard/reviews${params.size ? `?${params.toString()}` : ''}`
}
