import { useQuery } from '@tanstack/react-query'
import { authApi } from '@/api/client'
import type { AuthMethod } from '@/api/types'

/**
 * Loads the backend-advertised authentication methods for the current entry point.
 */
export function getAuthMethodsQueryOptions(returnTo?: string) {
  return {
    queryKey: ['auth', 'methods', returnTo ?? ''],
    queryFn: () => authApi.getMethods(returnTo),
    retry: false,
    meta: {
      skipGlobalErrorHandler: true,
    },
  }
}

export function useAuthMethods(returnTo?: string) {
  return useQuery<AuthMethod[]>(getAuthMethodsQueryOptions(returnTo))
}
