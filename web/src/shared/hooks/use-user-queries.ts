import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { SkillSummary, PagedResponse } from '@/api/types'
import { meApi, promotionApi, namespaceApi } from '@/api/client'

async function getMySkills(params: { page?: number; size?: number; filter?: string } = {}): Promise<PagedResponse<SkillSummary>> {
  return meApi.getSkills(params)
}

async function getMyStars(): Promise<SkillSummary[]> {
  return meApi.getStars()
}

async function getMyStarsPage(params: { page?: number; size?: number } = {}): Promise<PagedResponse<SkillSummary>> {
  return meApi.getStarsPage(params)
}

async function getMySubscriptions(): Promise<SkillSummary[]> {
  return meApi.getSubscriptions()
}

async function getMySubscriptionsPage(params: { page?: number; size?: number } = {}): Promise<PagedResponse<SkillSummary>> {
  return meApi.getSubscriptionsPage(params)
}

async function submitPromotion(params: { sourceSkillId: number; sourceVersionId: number }): Promise<void> {
  const globalNamespace = await namespaceApi.getDetail('global')
  await promotionApi.submit({
    sourceSkillId: params.sourceSkillId,
    sourceVersionId: params.sourceVersionId,
    targetNamespaceId: globalNamespace.id,
  })
}

export function useMySkills(params: { page?: number; size?: number; filter?: string } = {}) {
  return useQuery({
    queryKey: ['skills', 'my', params],
    queryFn: () => getMySkills(params),
  })
}

export function useMyStars(enabled = true) {
  return useQuery({
    queryKey: ['skills', 'stars'],
    queryFn: getMyStars,
    enabled,
  })
}

export function useMyStarsPage(params: { page?: number; size?: number } = {}, enabled = true) {
  return useQuery({
    queryKey: ['skills', 'stars', 'page', params],
    queryFn: () => getMyStarsPage(params),
    enabled,
  })
}

export function useMySubscriptions(enabled = true) {
  return useQuery({
    queryKey: ['skills', 'subscriptions'],
    queryFn: getMySubscriptions,
    enabled,
  })
}

export function useMySubscriptionsPage(params: { page?: number; size?: number } = {}, enabled = true) {
  return useQuery({
    queryKey: ['skills', 'subscriptions', 'page', params],
    queryFn: () => getMySubscriptionsPage(params),
    enabled,
  })
}

export function useSubmitPromotion() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: submitPromotion,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
      queryClient.invalidateQueries({ queryKey: ['governance'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}
