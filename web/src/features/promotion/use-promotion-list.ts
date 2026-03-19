import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { promotionApi } from '@/api/client'
import type { PromotionTask } from '@/api/types'

/**
 * Returns the promotion queue for a given status. The hook unwraps the backend
 * page object because promotion screens currently consume the item list only.
 */
export function usePromotionList(status = 'PENDING') {
  return useQuery({
    queryKey: ['promotions', status],
    queryFn: async () => {
      const page = await promotionApi.list({ status })
      return page.items
    },
  })
}

/**
 * Loads a single promotion task used by governance detail screens.
 */
export function usePromotionDetail(id: number) {
  return useQuery({
    queryKey: ['promotions', id],
    queryFn: () => promotionApi.get(id),
    enabled: !!id,
  })
}

/**
 * Approves a promotion request and refreshes both the promotion list and the
 * governance dashboard, which also embeds promotion-derived widgets.
 */
export function useApprovePromotion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) => promotionApi.approve(id, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
      queryClient.invalidateQueries({ queryKey: ['governance'] })
    },
  })
}

/**
 * Rejects a promotion request and keeps dependent governance queries in sync.
 */
export function useRejectPromotion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) => promotionApi.reject(id, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
      queryClient.invalidateQueries({ queryKey: ['governance'] })
    },
  })
}

export type { PromotionTask }
