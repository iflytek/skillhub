import { useQuery } from '@tanstack/react-query'
import type { SkillFile } from '@/api/types'

export function useSkillFiles(namespace: string, slug: string, version: string) {
  return useQuery({
    queryKey: ['skill-files', namespace, slug, version],
    queryFn: async () => {
      const res = await fetch(`/api/v1/skills/${namespace}/${slug}/versions/${version}/files`)
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json as SkillFile[]
    },
    enabled: !!namespace && !!slug && !!version,
  })
}
