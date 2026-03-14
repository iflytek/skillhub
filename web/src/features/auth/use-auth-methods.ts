import { useQuery } from '@tanstack/react-query'
import { authApi } from '@/api/client'
import type { AuthMethod } from '@/api/types'

export function useAuthMethods(returnTo?: string) {
  return useQuery<AuthMethod[]>({
    queryKey: ['auth', 'methods', returnTo ?? ''],
    queryFn: () => authApi.getMethods(returnTo),
  })
}
