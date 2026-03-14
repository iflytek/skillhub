import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api/client'
import type { LocalLoginRequest, LocalRegisterRequest, User } from '@/api/types'

export function useLocalLogin() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: LocalLoginRequest) => authApi.localLogin(request),
    onSuccess: (user) => {
      queryClient.setQueryData<User | null>(['auth', 'me'], user)
    },
  })
}

export function useLocalRegister() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: LocalRegisterRequest) => authApi.localRegister(request),
    onSuccess: (user) => {
      queryClient.setQueryData<User | null>(['auth', 'me'], user)
    },
  })
}
