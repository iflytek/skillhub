import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api/client'
import type { User } from '@/api/types'
import { clearSessionScopedQueries } from '@/features/notification/notification-session'

/**
 * Session-bootstrap mutation used when the backend can mint a browser session from an upstream
 * platform identity.
 */
export function useSessionBootstrap() {
  const queryClient = useQueryClient()

  return useMutation<User, Error, string>({
    mutationFn: (provider) => authApi.bootstrapSession(provider),
    onSuccess: (user) => {
      clearSessionScopedQueries(queryClient)
      queryClient.setQueryData(['auth', 'me'], user)
    },
  })
}
