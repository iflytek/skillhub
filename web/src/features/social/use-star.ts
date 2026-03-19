import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError, fetchJson, getCsrfHeaders, WEB_API_PREFIX } from '@/api/client'

interface StarStatus {
  starred: boolean
}

/**
 * Star-state hooks for one skill.
 *
 * Anonymous users are treated as unstarred instead of surfacing authorization failures into the UI.
 */
async function getStarStatus(skillId: number): Promise<StarStatus> {
  try {
    const starred = await fetchJson<boolean>(`${WEB_API_PREFIX}/skills/${skillId}/star`)
    return { starred }
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return { starred: false }
    }
    throw error
  }
}

async function toggleStar(skillId: number, starred: boolean): Promise<void> {
  if (starred) {
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${skillId}/star`, {
      method: 'DELETE',
      headers: getCsrfHeaders(),
    })
  } else {
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${skillId}/star`, {
      method: 'PUT',
      headers: getCsrfHeaders(),
    })
  }
}

export function useStar(skillId: number, enabled = true) {
  return useQuery({
    queryKey: ['skills', skillId, 'star'],
    queryFn: () => getStarStatus(skillId),
    enabled: !!skillId && enabled,
  })
}

export function useToggleStar(skillId: number) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (starred: boolean) => toggleStar(skillId, starred),
    onSuccess: () => {
      // Star actions affect both the local button state and starred-skill collections elsewhere in
      // the app.
      queryClient.invalidateQueries({ queryKey: ['skills', skillId, 'star'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
      queryClient.invalidateQueries({ queryKey: ['skills', 'stars'] })
    },
  })
}
