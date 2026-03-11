import { useQuery } from '@tanstack/react-query'
import type { SearchParams, PagedResponse, SkillSummary } from '@/api/types'

export function useSearchSkills(params: SearchParams) {
  return useQuery({
    queryKey: ['skills', params],
    queryFn: async () => {
      const queryParams = new URLSearchParams()
      if (params.q) queryParams.append('q', params.q)
      if (params.namespace) queryParams.append('namespace', params.namespace)
      if (params.sort) queryParams.append('sort', params.sort)
      if (params.page !== undefined) queryParams.append('page', String(params.page))
      if (params.size !== undefined) queryParams.append('size', String(params.size))

      const res = await fetch(`/api/v1/skills?${queryParams.toString()}`)
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json as PagedResponse<SkillSummary>
    },
  })
}
