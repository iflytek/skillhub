import { useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError, fetchJson, getCsrfHeaders, WEB_API_PREFIX } from '@/api/client'
import { useMyStars } from '@/shared/hooks/use-user-queries'

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
    if (error instanceof ApiError && (error.status === 401 || error.status === 403 || error.status === 404)) {
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
    // List cards / shells must not toast+flushSync on transient star failures.
    meta: { skipGlobalErrorHandler: true },
  })
}

/**
 * Shared starred-id set for list highlight (search/home/landing).
 *
 * One `['skills', 'stars']` query is reused across every SkillCard mount instead of
 * N× per-skill `/star` requests that re-render the grid as each response arrives.
 */
export function useStarredIdSet(enabled = true) {
  const query = useMyStars(enabled)
  const starredIds = useMemo(
    () => new Set((query.data ?? []).map((skill) => skill.id)),
    [query.data],
  )

  return { ...query, starredIds }
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
