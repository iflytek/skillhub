import type { TargetType } from './types'

/**
 * Minimal envelope-aware fetch helpers used by the operational promotion campaign feature.
 *
 * <p>The platform-wide {@code openapi-fetch} client is regenerated from the backend OpenAPI
 * spec. Until the new endpoints land in that schema, the page falls back to a hand-written
 * thin wrapper that mirrors the {@code ApiResponse} envelope used everywhere else.
 */
type ApiEnvelope<T> = {
  code: number
  msg: string
  data: T
  timestamp: string
  requestId: string
}

function getCsrfToken(): string | null {
  if (typeof document === 'undefined') return null
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  headers.set('Accept', 'application/json')
  if (init?.body) {
    headers.set('Content-Type', 'application/json')
  }
  const csrf = getCsrfToken()
  if (csrf) headers.set('X-XSRF-TOKEN', csrf)
  const res = await fetch(path, { ...init, headers, credentials: 'include' })
  if (!res.ok) {
    throw new Error(`Request failed: ${res.status}`)
  }
  const envelope = (await res.json()) as ApiEnvelope<T>
  if (envelope.code !== 0) {
    throw new Error(envelope.msg ?? `Request failed (code ${envelope.code})`)
  }
  return envelope.data
}

export type CampaignStatus = 'DRAFT' | 'PENDING_REVIEW' | 'SCHEDULED' | 'ACTIVE' | 'ENDED' | 'REJECTED'

export type PromotionCampaign = {
  id: number
  targetType: TargetType
  targetId: number
  targetVersionId?: number | null
  slotCode: string
  title: string
  subtitle?: string | null
  coverMediaId?: number | null
  demoMediaId?: number | null
  priority: number
  status: CampaignStatus
  startsAt: string
  endsAt: string
  submittedBy: string
  reviewedBy?: string | null
  reviewComment?: string | null
  reason?: string | null
  createdAt: string
  updatedAt: string
}

export type PromotionSlotItem = {
  campaignId: number
  slotCode: string
  targetType: TargetType
  targetId: number
  title: string
  subtitle?: string | null
  coverUrl?: string | null
  demoGifUrl?: string | null
  targetUrl?: string | null
}

export type CreateCampaignPayload = {
  targetType: TargetType
  targetId: number
  targetVersionId?: number | null
  slotCode: string
  title: string
  subtitle?: string | null
  coverMediaId?: number | null
  demoMediaId?: number | null
  priority: number
  startsAt: string
  endsAt: string
  reason?: string | null
}

type Page<T> = { items: T[]; total: number; page: number; size: number }

export const promotionCampaignApi = {
  listSlotItems: (slotCode: string): Promise<PromotionSlotItem[]> =>
    request(`/api/v1/promotion-slots/${encodeURIComponent(slotCode)}`),
  listByStatus: (status: CampaignStatus, page = 0, size = 20): Promise<Page<PromotionCampaign>> =>
    request(
      `/api/v1/admin/promotion-campaigns?status=${encodeURIComponent(status)}&page=${page}&size=${size}`,
    ),
  create: (payload: CreateCampaignPayload): Promise<PromotionCampaign> =>
    request('/api/v1/admin/promotion-campaigns', { method: 'POST', body: JSON.stringify(payload) }),
  approve: (id: number, comment?: string | null): Promise<PromotionCampaign> =>
    request(`/api/v1/admin/promotion-campaigns/${id}/approve`, {
      method: 'POST',
      body: JSON.stringify({ comment: comment ?? null }),
    }),
  reject: (id: number, comment?: string | null): Promise<PromotionCampaign> =>
    request(`/api/v1/admin/promotion-campaigns/${id}/reject`, {
      method: 'POST',
      body: JSON.stringify({ comment: comment ?? null }),
    }),
  recordEvent: (id: number, eventType: 'IMPRESSION' | 'CLICK' | 'DOWNLOAD' | 'INSTALL'): Promise<void> =>
    request(`/api/v1/admin/promotion-campaigns/${id}/events/${eventType}`, { method: 'POST' }),
}
