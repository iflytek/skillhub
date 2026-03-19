import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi, getDirectAuthRuntimeConfig } from '@/api/client'
import { ApiError } from '@/shared/lib/api-error'
import type { LocalLoginRequest, User } from '@/api/types'

/**
 * Password-login mutation that can switch between local auth and direct upstream auth based on
 * runtime configuration.
 */
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
    onError: (error) => {
      // Keep invalid credentials on the login page instead of falling back to the
      // global 401 redirect handler used for background API requests.
      if (error instanceof ApiError) {
        return
      }
    },
  })
}
