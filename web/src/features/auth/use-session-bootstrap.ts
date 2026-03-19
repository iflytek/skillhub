import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api/client'
import type { User } from '@/api/types'

/**
 * Session-bootstrap mutation used when the backend can mint a browser session from an upstream
 * platform identity.
 */
export function useSessionBootstrap() {
  const queryClient = useQueryClient()

  return useMutation<User, Error, string>({
    mutationFn: (provider) => authApi.bootstrapSession(provider),
    onSuccess: (user) => {
      queryClient.setQueryData(['auth', 'me'], user)
    },
  })
}
