import { useQuery } from '@tanstack/react-query'
import type { SkillDetail } from '@/api/types'

export function useSkillDetail(namespace: string, slug: string) {
  return useQuery({
    queryKey: ['skill', namespace, slug],
    queryFn: async () => {
      const res = await fetch(`/api/v1/skills/${namespace}/${slug}`)
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json as SkillDetail
    },
    enabled: !!namespace && !!slug,
  })
}
