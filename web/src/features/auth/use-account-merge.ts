import { useMutation, useQueryClient } from '@tanstack/react-query'
import { accountApi } from '@/api/client'
import type { MergeConfirmRequest, MergeInitiateRequest, MergeVerifyRequest } from '@/api/types'

export function useInitiateAccountMerge() {
  return useMutation({
    mutationFn: (request: MergeInitiateRequest) => accountApi.initiateMerge(request),
  })
}

export function useInspectAccountMerge() {
  return useMutation({
    mutationFn: (mergeRequestId: number) => accountApi.getMergeApprovalDetails(mergeRequestId),
  })
}

export function useVerifyAccountMerge() {
  return useMutation({
    mutationFn: (request: MergeVerifyRequest) => accountApi.verifyMerge(request),
  })
}

export function useCancelAccountMerge() {
  return useMutation({
    mutationFn: (request: MergeConfirmRequest) => accountApi.cancelMerge(request),
  })
}

export function useConfirmAccountMerge() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: MergeConfirmRequest) => accountApi.confirmMerge(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
    },
  })
}
