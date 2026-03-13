import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi, getDirectAuthRuntimeConfig } from '@/api/client'
import type { LocalLoginRequest, User } from '@/api/types'

export function usePasswordLogin() {
  const queryClient = useQueryClient()
  const directAuthConfig = getDirectAuthRuntimeConfig()

  return useMutation({
    mutationFn: (request: LocalLoginRequest) => {
      if (directAuthConfig.enabled && directAuthConfig.provider) {
        return authApi.directLogin(directAuthConfig.provider, request)
      }
      return authApi.localLogin(request)
    },
    onSuccess: (user) => {
      queryClient.setQueryData<User | null>(['auth', 'me'], user)
    },
  })
}
