import { useQuery } from '@tanstack/react-query'
import { fetchJson } from '@/api/client'
import type { PagedResponse, SkillSummary } from '@/api/types'
import { buildSkillSearchUrl } from '@/shared/hooks/skill-query-helpers'

/** Source of the skill currently being viewed; the twin lives in the other one. */
export type CrossSourceTwin = {
  namespace: string
  slug: string
  displayName: string
  source: 'platform' | 'github'
}

/**
 * Picks a same-slug skill from another namespace. Search already applies visibility, so a twin the
 * reader cannot open never surfaces here.
 */
export function pickCrossSourceTwin(
  items: SkillSummary[],
  current: { slug: string; namespace: string },
): SkillSummary | undefined {
  return items.find(item => item.slug === current.slug && item.namespace !== current.namespace)
}

export function useCrossSourceTwin(
  skill: { slug: string; namespace: string } | undefined,
) {
  return useQuery({
    queryKey: ['skills', skill?.namespace, skill?.slug, 'cross-source-twin'],
    queryFn: async ({ signal }): Promise<CrossSourceTwin | null> => {
      if (!skill) return null
      // Search both sources so either copy finds its counterpart; visibility stays server-side.
      const page = await fetchJson<PagedResponse<SkillSummary>>(
        buildSkillSearchUrl({ q: skill.slug, source: 'all', size: 20 }),
        { signal },
      )
      const twin = pickCrossSourceTwin(page.items, skill)
      if (!twin) return null
      return {
        namespace: twin.namespace,
        slug: twin.slug,
        displayName: twin.displayName,
        source: twin.source === 'github' ? 'github' : 'platform',
      }
    },
    enabled: Boolean(skill?.slug),
    staleTime: 5 * 60_000,
    meta: { skipGlobalErrorHandler: true },
  })
}
