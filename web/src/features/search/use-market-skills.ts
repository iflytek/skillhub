import { useInfiniteQuery } from '@tanstack/react-query'
import { fetchJson } from '@/api/client'
import type { PagedResponse, SearchParams, SkillSummary } from '@/api/types'
import { buildSkillSearchUrl } from '@/shared/hooks/skill-query-helpers'

export type MarketSearchParams = Omit<SearchParams, 'page' | 'starredOnly'>

export function getMarketSkillsKey(params: MarketSearchParams, viewer: string | null, language: string) {
  return ['skills', 'search', 'infinite', viewer, language, params] as const
}

export function useMarketSkills(params: MarketSearchParams, viewer: string | null, language: string, enabled: boolean) {
  return useInfiniteQuery({
    queryKey: getMarketSkillsKey(params, viewer, language),
    queryFn: ({ pageParam, signal }) => fetchJson<PagedResponse<SkillSummary>>(
      buildSkillSearchUrl({ ...params, page: pageParam }), { signal },
    ),
    initialPageParam: 0,
    getNextPageParam: (last, _pages, page) => {
      const size = last.size || params.size || 12
      return last.items.length > 0 && page * size + last.items.length < last.total ? page + 1 : undefined
    },
    enabled,
    // Restore cached batches immediately; stale batches still refresh using the
    // normal 30-second policy so access/metadata changes are not held indefinitely.
    staleTime: 30_000,
    meta: { skipGlobalErrorHandler: true },
  })
}

export function flattenMarketSkills(pages: PagedResponse<SkillSummary>[] | undefined) {
  const skills = new Map<number, SkillSummary>()
  for (const page of pages ?? []) {
    for (const skill of page.items) if (!skills.has(skill.id)) skills.set(skill.id, skill)
  }
  return [...skills.values()]
}
