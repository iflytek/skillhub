import { useQuery } from '@tanstack/react-query'
import type { SkillVersion } from '@/api/types'

export function useSkillVersions(namespace: string, slug: string) {
  return useQuery({
    queryKey: ['skill-versions', namespace, slug],
    queryFn: async () => {
      const res = await fetch(`/api/v1/skills/${namespace}/${slug}/versions`)
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json as SkillVersion[]
    },
    enabled: !!namespace && !!slug,
  })
}
