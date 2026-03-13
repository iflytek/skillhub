import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchJson, getCsrfHeaders } from '@/api/client'

interface StarStatus {
  starred: boolean
}

async function getStarStatus(skillId: number): Promise<StarStatus> {
  try {
    const starred = await fetchJson<boolean>(`/api/v1/skills/${skillId}/star`)
    return { starred }
  } catch (error) {
    if (error instanceof Error && error.message === 'HTTP 401') {
      return { starred: false }
    }
    throw error
  }
}

async function toggleStar(skillId: number, starred: boolean): Promise<void> {
  if (starred) {
    await fetchJson<void>(`/api/v1/skills/${skillId}/star`, {
      method: 'DELETE',
      headers: getCsrfHeaders(),
    })
  } else {
    await fetchJson<void>(`/api/v1/skills/${skillId}/star`, {
      method: 'PUT',
      headers: getCsrfHeaders(),
    })
  }
}

export function useStar(skillId: number) {
  return useQuery({
    queryKey: ['skills', skillId, 'star'],
    queryFn: () => getStarStatus(skillId),
    enabled: !!skillId,
  })
}

export function useToggleStar(skillId: number) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (starred: boolean) => toggleStar(skillId, starred),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skills', skillId, 'star'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}
