/**
 * Frontend integration for the skill bundle module: API client and react-query hooks.
 *
 * <p>Until the new endpoints land in the generated openapi schema, this module ships
 * a small envelope-aware wrapper so the dashboard pages can consume the new APIs.
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
  if (init?.body) headers.set('Content-Type', 'application/json')
  const csrf = getCsrfToken()
  if (csrf) headers.set('X-XSRF-TOKEN', csrf)
  const res = await fetch(path, { ...init, headers, credentials: 'include' })
  if (!res.ok) throw new Error(`Request failed: ${res.status}`)
  const envelope = (await res.json()) as ApiEnvelope<T>
  if (envelope.code !== 0) throw new Error(envelope.msg ?? `Request failed (code ${envelope.code})`)
  return envelope.data
}

export type BundleType = 'PROJECT' | 'ROLE' | 'SCENARIO' | 'CUSTOM'
export type BundleVersionStatus = 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'REJECTED' | 'YANKED'

export type DraftItemPayload = {
  skillId: number
  skillVersionId: number
  roleDescription: string
  required: boolean
  installOrder: number
}

export type BuildDraftPayload = {
  slug: string
  displayName: string
  version: string
  type: BundleType
  summary: string
  targetProjectTypes?: string[]
  roleTags?: string[]
  items: DraftItemPayload[]
  mediaIds?: number[]
}

export type SkillBundleDetail = {
  id: number
  namespaceId: number
  slug: string
  displayName: string
  type: BundleType
  summary: string
  downloadCount: number
  starCount: number
  ratingAvg?: number | null
  ratingCount: number
  commentCount: number
  latestVersionId?: number | null
  version?: { id: number; version: string; status: BundleVersionStatus; publishedAt?: string | null } | null
  items: Array<{
    skillId: number
    namespaceSlug: string
    skillSlug: string
    displayName: string
    version: string
    roleDescription: string
    required: boolean
    installOrder: number
    detailUrl: string
  }>
  updatedAt: string
}

export const skillBundleApi = {
  buildDraft: (namespace: string, payload: BuildDraftPayload) =>
    request<{ bundleId: number; bundleVersionId: number; status: BundleVersionStatus }>(
      `/api/v1/skill-bundles/${encodeURIComponent(namespace)}/drafts/build`,
      { method: 'POST', body: JSON.stringify(payload) },
    ),
  getDetail: (namespace: string, slug: string, version?: string) => {
    const query = version ? `?version=${encodeURIComponent(version)}` : ''
    return request<SkillBundleDetail>(
      `/api/v1/skill-bundles/${encodeURIComponent(namespace)}/${encodeURIComponent(slug)}${query}`,
    )
  },
  submitReview: (namespace: string, slug: string, bundleVersionId: number) =>
    request<number>(
      `/api/v1/skill-bundles/${encodeURIComponent(namespace)}/${encodeURIComponent(slug)}/versions/${bundleVersionId}/submit-review`,
      { method: 'POST' },
    ),
  approveReview: (reviewTaskId: number, comment?: string) =>
    request<number>(`/api/v1/skill-bundle-reviews/${reviewTaskId}/approve`, {
      method: 'POST',
      body: JSON.stringify({ comment: comment ?? null }),
    }),
  rejectReview: (reviewTaskId: number, comment?: string) =>
    request<number>(`/api/v1/skill-bundle-reviews/${reviewTaskId}/reject`, {
      method: 'POST',
      body: JSON.stringify({ comment: comment ?? null }),
    }),
  recordDownload: (namespace: string, slug: string) =>
    request<void>(
      `/api/v1/skill-bundles/${encodeURIComponent(namespace)}/${encodeURIComponent(slug)}/download`,
      { method: 'POST' },
    ),
}
