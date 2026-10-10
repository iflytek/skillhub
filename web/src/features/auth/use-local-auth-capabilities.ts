import { useQuery } from '@tanstack/react-query'
import { authApi } from '@/api/client'

export function useLocalAuthCapabilities() {
  return useQuery({
    queryKey: ['auth', 'local-capabilities'],
    queryFn: authApi.getLocalCapabilities,
    staleTime: 0,
  })
}
