import { useQuery } from '@tanstack/react-query'
import { fetchText, WEB_API_PREFIX } from '@/api/client'

/**
 * Fetches a single file's content from a review-bound skill version.
 * Used in the review detail page for file preview functionality.
 */
export function useReviewFile(
  reviewId: number | undefined,
  filePath: string | null,
  enabled: boolean = true
) {
  return useQuery({
    queryKey: ['reviews', reviewId, 'file', filePath],
    queryFn: async () => {
      if (!reviewId || !filePath) {
        throw new Error('Review ID and file path are required')
      }
      return fetchText(`${WEB_API_PREFIX}/reviews/${reviewId}/file?path=${encodeURIComponent(filePath)}`)
    },
    enabled: enabled && !!reviewId && !!filePath,
    staleTime: 5 * 60 * 1000, // Cache for 5 minutes
  })
}
