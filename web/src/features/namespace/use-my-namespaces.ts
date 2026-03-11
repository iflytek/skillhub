import { useQuery } from '@tanstack/react-query'
import type { Namespace } from '@/api/types'

export function useMyNamespaces() {
  return useQuery({
    queryKey: ['my-namespaces'],
    queryFn: async () => {
      const res = await fetch('/api/v1/namespaces')
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }
      const json = await res.json()
      return json as Namespace[]
    },
  })
}
