import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { promotionApi } from '@/api/client'
import type { PromotionTask } from '@/api/types'

export function usePromotionList(status = 'PENDING') {
  return useQuery({
    queryKey: ['promotions', status],
    queryFn: async () => {
      const page = await promotionApi.list({ status })
      return page.items
    },
  })
}

export function usePromotionDetail(id: number) {
  return useQuery({
    queryKey: ['promotions', id],
    queryFn: () => promotionApi.get(id),
    enabled: !!id,
  })
}

export function useApprovePromotion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) => promotionApi.approve(id, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
    },
  })
}

export function useRejectPromotion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) => promotionApi.reject(id, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
    },
  })
}

export type { PromotionTask }
