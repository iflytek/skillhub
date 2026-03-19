import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError, fetchJson, getCsrfHeaders, WEB_API_PREFIX } from '@/api/client'

interface UserRating {
  score: number
  rated: boolean
}

/**
 * Reads the current user's rating. Unauthenticated users are normalized to an
 * unrated state so the rating widget can stay renderable without a hard error.
 */
async function getUserRating(skillId: number): Promise<UserRating> {
  try {
    return await fetchJson<UserRating>(`${WEB_API_PREFIX}/skills/${skillId}/rating`)
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return { score: 0, rated: false }
    }
    throw error
  }
}

/**
 * Submits or updates the current user's score for a skill.
 */
async function rateSkill(skillId: number, rating: number): Promise<void> {
  await fetchJson<void>(`${WEB_API_PREFIX}/skills/${skillId}/rating`, {
    method: 'PUT',
    headers: getCsrfHeaders({
      'Content-Type': 'application/json',
    }),
    body: JSON.stringify({ score: rating }),
  })
}

/**
 * Exposes the user-specific rating query for one skill.
 */
export function useUserRating(skillId: number) {
  return useQuery({
    queryKey: ['skills', skillId, 'rating'],
    queryFn: () => getUserRating(skillId),
    enabled: !!skillId,
  })
}

/**
 * Updates the current user's rating and refreshes both the focused rating query
 * and broader skill queries that may embed aggregate rating values.
 */
export function useRate(skillId: number) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (rating: number) => rateSkill(skillId, rating),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skills', skillId, 'rating'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}
