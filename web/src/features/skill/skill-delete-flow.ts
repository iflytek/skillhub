import type { QueryClient } from '@tanstack/react-query'
import { normalizeSkillDetailReturnTo } from '@/shared/lib/skill-navigation'

export function isDeleteSlugConfirmationValid(expectedSlug: string, typedSlug: string) {
  return typedSlug === expectedSlug
}

export function resolveDeletedSkillReturnTo(returnTo?: string) {
  return normalizeSkillDetailReturnTo(returnTo) ?? '/search'
}

export function clearDeletedSkillQueries(queryClient: QueryClient, namespace: string, slug: string) {
  const baseKey = ['skills', namespace, slug] as const

  void queryClient.cancelQueries({ queryKey: baseKey })
  queryClient.removeQueries({ queryKey: baseKey })

  void queryClient.invalidateQueries({ queryKey: ['skills', 'my'] })
  void queryClient.invalidateQueries({ queryKey: ['skills'] })
}
