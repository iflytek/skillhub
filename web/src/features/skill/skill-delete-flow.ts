import type { QueryClient } from '@tanstack/react-query'
import { normalizeSkillDetailReturnTo } from '@/shared/lib/skill-navigation'

export function isDeleteSlugConfirmationValid(expectedSlug: string, typedSlug: string) {
  return typedSlug === expectedSlug
}

export function resolveDeletedSkillReturnTo(returnTo?: string) {
  return normalizeSkillDetailReturnTo(returnTo) ?? '/search'
}

export function clearDeletedSkillQueries(queryClient: QueryClient, namespace: string, slug: string, skillId?: number) {
  const baseKey = ['skills', namespace, slug] as const

  void queryClient.cancelQueries({ queryKey: baseKey })
  queryClient.setQueriesData({ queryKey: baseKey }, undefined)
  queryClient.removeQueries({ queryKey: baseKey })
  if (skillId) {
    void queryClient.cancelQueries({ queryKey: ['skills', skillId, 'star'], exact: true })
    void queryClient.cancelQueries({ queryKey: ['skills', skillId, 'rating'], exact: true })
    queryClient.setQueryData(['skills', skillId, 'star'], undefined)
    queryClient.setQueryData(['skills', skillId, 'rating'], undefined)
    queryClient.removeQueries({ queryKey: ['skills', skillId, 'star'], exact: true })
    queryClient.removeQueries({ queryKey: ['skills', skillId, 'rating'], exact: true })
  }

  void queryClient.invalidateQueries({ queryKey: ['skills', 'my'] })
  void queryClient.invalidateQueries({ queryKey: ['skills'] })
}
