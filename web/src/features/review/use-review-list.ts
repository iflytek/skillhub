import { useQuery } from '@tanstack/react-query'
import { reviewApi } from '@/api/client'

export interface ReviewListFilters { q?: string; reviewKind?: string; originType?: string }

/**
 * Loads review tasks filtered by status and optional namespace scope.
 * Returns paginated response with totalElements / totalPages.
 */
async function getReviewList(
  status: string,
  namespaceId?: number,
  page = 0,
  size = 20,
  sortDirection: 'ASC' | 'DESC' = 'DESC',
  filters?: ReviewListFilters,
) {
  const response = await reviewApi.list({ status, namespaceId, page, size, sortDirection, ...filters })
  return {
    ...response,
    totalElements: response.total,
    totalPages: response.size > 0 ? Math.ceil(response.total / response.size) : 0,
  }
}

/**
 * Exposes the review list query used by dashboard moderation views.
 */
export function useReviewList(
  status: string,
  namespaceId?: number,
  page = 0,
  size = 20,
  sortDirection: 'ASC' | 'DESC' = 'DESC',
  enabled = true,
  filters?: ReviewListFilters,
) {
  return useQuery({
    queryKey: ['reviews', status, namespaceId, page, size, sortDirection, ...(filters ? [filters] : [])],
    queryFn: () => getReviewList(status, namespaceId, page, size, sortDirection, filters),
    staleTime: 0,
    enabled: enabled && (namespaceId === undefined || namespaceId > 0),
  })
}
