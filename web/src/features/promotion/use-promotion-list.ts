import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { promotionApi } from '@/api/client'
import type { PromotionSortBy, PromotionSortDirection, PromotionStatus, PromotionTask } from '@/api/types'

export interface PromotionListParams {
  status?: PromotionStatus
  page?: number
  size?: number
  sortBy?: PromotionSortBy
  sortDirection?: PromotionSortDirection
}

/**
 * Returns a promotion page for a given status, preserving pagination metadata
 * so queue screens can navigate beyond the first backend page.
 */
export function usePromotionList(params: PromotionListParams = { status: 'PENDING' }) {
  const normalizedParams = {
    status: params.status ?? 'PENDING',
    page: params.page ?? 0,
    size: params.size ?? 20,
    sortBy: params.sortBy,
    sortDirection: params.sortDirection,
  }

  return useQuery({
    queryKey: ['promotions', normalizedParams],
    queryFn: () => promotionApi.list(normalizedParams),
    staleTime: 30_000,
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

export type { PromotionSortDirection, PromotionStatus, PromotionTask }
