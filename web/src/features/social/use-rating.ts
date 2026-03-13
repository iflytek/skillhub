import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchJson, getCsrfHeaders } from '@/api/client'

interface UserRating {
  score: number
  rated: boolean
}

async function getUserRating(skillId: number): Promise<UserRating> {
  try {
    return await fetchJson<UserRating>(`/api/v1/skills/${skillId}/rating`)
  } catch (error) {
    if (error instanceof Error && error.message === 'HTTP 401') {
      return { score: 0, rated: false }
    }
    throw error
  }
}

async function rateSkill(skillId: number, rating: number): Promise<void> {
  await fetchJson<void>(`/api/v1/skills/${skillId}/rating`, {
    method: 'PUT',
    headers: getCsrfHeaders({
      'Content-Type': 'application/json',
    }),
    body: JSON.stringify({ score: rating }),
  })
}

export function useUserRating(skillId: number) {
  return useQuery({
    queryKey: ['skills', skillId, 'rating'],
    queryFn: () => getUserRating(skillId),
    enabled: !!skillId,
  })
}

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
