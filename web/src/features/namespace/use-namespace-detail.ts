import { useQuery } from '@tanstack/react-query'
import type { Namespace } from '@/api/types'

export function useNamespaceDetail(slug: string) {
  return useQuery({
    queryKey: ['namespace', slug],
    queryFn: async () => {
      const res = await fetch(`/api/v1/namespaces/${slug}`)
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json as Namespace
    },
    enabled: !!slug,
  })
}
