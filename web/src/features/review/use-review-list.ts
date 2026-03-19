import { useQuery } from '@tanstack/react-query'
import { reviewApi } from '@/api/client'
import type { ReviewTask } from '@/api/types'

/**
 * Loads review tasks filtered by status and optional namespace scope.
 */
async function getReviewList(status: string, namespaceId?: number): Promise<ReviewTask[]> {
  const page = await reviewApi.list({ status, namespaceId })
  return page.items
}

/**
 * Exposes the review list query used by dashboard moderation views.
 */
export function useReviewList(status: string, namespaceId?: number) {
  return useQuery({
    queryKey: ['reviews', status, namespaceId],
    queryFn: () => getReviewList(status, namespaceId),
    enabled: namespaceId === undefined || namespaceId > 0,
  })
}
