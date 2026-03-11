import { useQuery } from '@tanstack/react-query'
import type { NamespaceMember } from '@/api/types'

export function useNamespaceMembers(slug: string) {
  return useQuery({
    queryKey: ['namespace-members', slug],
    queryFn: async () => {
      const res = await fetch(`/api/v1/namespaces/${slug}/members`)
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json as NamespaceMember[]
    },
    enabled: !!slug,
  })
}
