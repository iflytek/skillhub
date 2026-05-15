import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  promotionCampaignApi,
  type CampaignStatus,
  type CreateCampaignPayload,
} from './api'

const PROMOTION_CAMPAIGN_KEY = ['promotion-campaigns'] as const

export function usePromotionCampaigns(status: CampaignStatus, page = 0, size = 20) {
  return useQuery({
    queryKey: [...PROMOTION_CAMPAIGN_KEY, status, page, size],
    queryFn: () => promotionCampaignApi.listByStatus(status, page, size),
  })
}

export function useCreatePromotionCampaign() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateCampaignPayload) => promotionCampaignApi.create(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: PROMOTION_CAMPAIGN_KEY }),
  })
}

export function useApprovePromotionCampaign() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) =>
      promotionCampaignApi.approve(id, comment),
    onSuccess: () => qc.invalidateQueries({ queryKey: PROMOTION_CAMPAIGN_KEY }),
  })
}

export function useRejectPromotionCampaign() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) =>
      promotionCampaignApi.reject(id, comment),
    onSuccess: () => qc.invalidateQueries({ queryKey: PROMOTION_CAMPAIGN_KEY }),
  })
}

export function usePromotionSlot(slotCode: string) {
  return useQuery({
    queryKey: ['promotion-slots', slotCode],
    queryFn: () => promotionCampaignApi.listSlotItems(slotCode),
    enabled: !!slotCode,
  })
}
