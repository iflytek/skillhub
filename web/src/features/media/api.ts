/**
 * Minimal media API client used by detail pages and the upload UI.
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

export type MediaOwnerType = 'SKILL_VERSION' | 'SKILL_BUNDLE_VERSION' | 'PROMOTION_CAMPAIGN'
export type MediaAssetRole = 'COVER' | 'DEMO' | 'SCREENSHOT'
export type MediaType = 'IMAGE' | 'GIF'

export type MediaAsset = {
  id: number
  ownerType: MediaOwnerType
  ownerId: number
  mediaType: MediaType
  role: MediaAssetRole
  url: string
  contentType: string
  sizeBytes: number
  altText?: string | null
  createdAt: string
}

/**
 * URL helper for an asset id. Used by cards / details / promotion slots so they
 * don't hand-write the path (and so the path can be swapped later if we move to
 * pre-signed URLs).
 */
export function mediaUrl(id: number | null | undefined): string | null {
  if (!id || id <= 0) return null
  return `/api/v1/media/${id}`
}

export const mediaApi = {
  upload: async (params: {
    file: File
    ownerType: MediaOwnerType
    ownerId: number
    role: MediaAssetRole
    altText?: string
  }): Promise<MediaAsset> => {
    const form = new FormData()
    form.append('file', params.file)
    form.append('ownerType', params.ownerType)
    form.append('ownerId', String(params.ownerId))
    form.append('role', params.role)
    if (params.altText) form.append('altText', params.altText)
    const headers = new Headers()
    const csrf = getCsrfToken()
    if (csrf) headers.set('X-XSRF-TOKEN', csrf)
    const res = await fetch('/api/v1/media', {
      method: 'POST',
      body: form,
      headers,
      credentials: 'include',
    })
    if (!res.ok) throw new Error(`Request failed: ${res.status}`)
    const envelope = (await res.json()) as ApiEnvelope<MediaAsset>
    if (envelope.code !== 0) throw new Error(envelope.msg ?? `Request failed (code ${envelope.code})`)
    return envelope.data
  },
}
