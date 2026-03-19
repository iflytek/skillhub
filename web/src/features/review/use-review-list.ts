import { useQuery } from '@tanstack/react-query'
import { reviewApi } from '@/api/client'

/**
 * Loads review tasks filtered by status and optional namespace scope.
 * Returns paginated response with totalElements / totalPages.
 */
async function getReviewList(status: string, namespaceId?: number, page = 0, size = 20) {
  const response = await reviewApi.list({ status, namespaceId, page, size })
  return {
    ...response,
    totalElements: response.total,
    totalPages: response.size > 0 ? Math.ceil(response.total / response.size) : 0,
  }
}

/**
 * Exposes the review list query used by dashboard moderation views.
 */
export function useReviewList(status: string, namespaceId?: number, page = 0, size = 20, enabled = true) {
  return useQuery({
    queryKey: ['reviews', status, namespaceId, page, size],
    queryFn: () => getReviewList(status, namespaceId, page, size),
    enabled: enabled && (namespaceId === undefined || namespaceId > 0),
  })
}
