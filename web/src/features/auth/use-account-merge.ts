import { useMutation } from '@tanstack/react-query'
import { accountApi } from '@/api/client'
import type { MergeConfirmRequest, MergeInitiateRequest, MergeVerifyRequest } from '@/api/types'

export function useInitiateAccountMerge() {
  return useMutation({
    mutationFn: (request: MergeInitiateRequest) => accountApi.initiateMerge(request),
  })
}

export function useVerifyAccountMerge() {
  return useMutation({
    mutationFn: (request: MergeVerifyRequest) => accountApi.verifyMerge(request),
  })
}

export function useConfirmAccountMerge() {
  return useMutation({
    mutationFn: (request: MergeConfirmRequest) => accountApi.confirmMerge(request),
  })
}
