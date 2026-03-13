import { useMutation } from '@tanstack/react-query'
import { authApi } from '@/api/client'
import type { LocalLoginRequest, LocalRegisterRequest } from '@/api/types'

export function useLocalLogin() {
  return useMutation({
    mutationFn: (request: LocalLoginRequest) => authApi.localLogin(request),
  })
}

export function useLocalRegister() {
  return useMutation({
    mutationFn: (request: LocalRegisterRequest) => authApi.localRegister(request),
  })
}
