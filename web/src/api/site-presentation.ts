import { ApiError, buildApiUrl, fetchJson, getCsrfHeaders } from './client'

import type { components } from './generated/schema'

export type SitePresentationSettings = components['schemas']['SitePresentationSettings']
export type SitePresentationResponse = components['schemas']['SitePresentationResponse']

/** Bound the entire startup read, including response body consumption. */
async function loadPublicPresentation(): Promise<SitePresentationResponse> {
  const controller = new AbortController()
  let timer: ReturnType<typeof setTimeout> | undefined
  const deadline = new Promise<never>((_resolve, reject) => {
    timer = setTimeout(() => { controller.abort(); reject(new ApiError('error.request.timeout', 408)) }, 5000)
  })
  const read = async () => {
    const response = await fetch(buildApiUrl('/api/v1/site-presentation'), { signal: controller.signal, headers: { Accept: 'application/json' } })
    const envelope = await response.json() as components['schemas']['ApiResponseSitePresentationResponse']
    if (!response.ok || envelope.code !== 0) throw new ApiError(envelope.msg || `HTTP ${response.status}`, response.status)
    if (!envelope.data?.settings || typeof envelope.data.version !== 'number') throw new ApiError('Invalid presentation response', 502)
    return envelope.data
  }
  try {
    return await Promise.race([read(), deadline])
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError('apiError.networkError', 0)
  } finally {
    clearTimeout(timer)
  }
}

export const sitePresentationApi = {
  get: loadPublicPresentation,
  getAdmin: () => fetchJson<SitePresentationResponse>('/api/v1/admin/system-config/presentation'),
  update: (value: components['schemas']['SitePresentationUpdateRequest']) => fetchJson<SitePresentationResponse>('/api/v1/admin/system-config/presentation', {
    method: 'PUT', headers: { ...getCsrfHeaders(), 'Content-Type': 'application/json' }, body: JSON.stringify(value),
  }),
  upload: (file: File) => {
    const body = new FormData()
    body.append('file', file)
    return fetchJson<components['schemas']['SitePresentationAssetResponse']>('/api/v1/admin/system-config/presentation/assets', { method: 'POST', headers: getCsrfHeaders(), body })
  },
}

/** Only the application's public branding resource route can be rendered as configured media. */
export function presentationAssetUrl(path: string | null | undefined): string | undefined {
  if (!path || !/^\/api\/v1\/site-presentation\/assets\/[a-zA-Z0-9-]+$/.test(path)) return undefined
  return buildApiUrl(path)
}
