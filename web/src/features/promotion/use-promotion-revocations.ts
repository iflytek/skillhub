import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { promotionRevocationApi } from '@/api/client'

export function usePromotionRevocationHistory(sourceSkillId: number, enabled: boolean) {
  return useQuery({
    queryKey: ['promotion-revocations', 'source', sourceSkillId],
    queryFn: () => promotionRevocationApi.history(sourceSkillId),
    enabled,
  })
}

export function usePendingPromotionRevocations() {
  return useQuery({
    queryKey: ['promotion-revocations', 'pending'],
    queryFn: promotionRevocationApi.pending,
  })
}

export function useAdminPromotionRevocationHistory(page: number, size: number) {
  return useQuery({
    queryKey: ['promotion-revocations', 'history', page, size],
    queryFn: () => promotionRevocationApi.adminHistory(page, size),
  })
}

function useRevocationMutation<T>(mutationFn: (input: T) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotion-revocations'] })
      queryClient.invalidateQueries({ queryKey: ['promotion-source-state'] })
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
      queryClient.invalidateQueries({ queryKey: ['governance'] })
    },
  })
}

export function useSubmitPromotionRevocation() {
  return useRevocationMutation(({ sourceSkillId, reason }: { sourceSkillId: number; reason: string }) =>
    promotionRevocationApi.submit(sourceSkillId, reason))
}

export function useDirectPromotionRevocation() {
  return useRevocationMutation(({ sourceSkillId, reason }: { sourceSkillId: number; reason: string }) =>
    promotionRevocationApi.direct(sourceSkillId, reason))
}

export function useApprovePromotionRevocation() {
  return useRevocationMutation(({ id, comment }: { id: number; comment: string }) =>
    promotionRevocationApi.approve(id, comment))
}

export function useRejectPromotionRevocation() {
  return useRevocationMutation(({ id, comment }: { id: number; comment: string }) =>
    promotionRevocationApi.reject(id, comment))
}
