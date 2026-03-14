import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api/client'
import type { User } from '@/api/types'

export function useSessionBootstrap() {
  const queryClient = useQueryClient()

  return useMutation<User, Error, string>({
    mutationFn: (provider) => authApi.bootstrapSession(provider),
    onSuccess: (user) => {
      queryClient.setQueryData(['auth', 'me'], user)
    },
  })
}
