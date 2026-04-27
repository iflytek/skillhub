import createClient from 'openapi-fetch'
import type { paths } from './generated/schema'
import type {
  ChangePasswordRequest,
  PasswordResetConfirmRequest,
  PasswordResetRequest,
  ApiToken,
  CreateTokenRequest,
  CreateTokenResponse,
  MergeConfirmRequest,
  LocalLoginRequest,
  LocalRegisterRequest,
  MergeInitiateRequest,
  MergeInitiateResponse,
  MergeVerifyRequest,
  ReviewSkillDetail,
  ReviewTask,
  PromotionTask,
  AuditLogItem,
  SkillSummary,
  SkillReport,
  GovernanceSummary,
  GovernanceInboxItem,
  GovernanceActivityItem,
  GovernanceNotification,
  PagedResponse,
  ReportDisposition,
  AuthMethod,
  OAuthProvider,
  User,
  ManagedNamespace,
  Namespace,
  CreateNamespaceRequest,
  NamespaceMember,
  NamespaceCandidateUser,
  NotificationItem,
  NotificationPreferenceItem,
  NotificationUnreadCount,
  SkillDeleteResult,
  AdminLabelInput,
  LabelDefinition,
  LabelItem,
  BatchMemberResponse,
} from './types'
import { ApiError } from '@/shared/lib/api-error'
import i18n from '@/i18n/config'

/**
 * Front-end API foundation for generated OpenAPI calls and hand-written convenience wrappers.
 *
 * This module centralizes runtime-config lookup, CSRF handling, localized request headers, envelope
 * unwrapping, and exported API groups used throughout feature hooks.
 */
export { ApiError }

export const WEB_API_PREFIX = '/api/web'

type RuntimeConfig = {
  apiBaseUrl?: string
  appBaseUrl?: string
  authDirectEnabled?: string
  authDirectProvider?: string
  authSessionBootstrapEnabled?: string
  authSessionBootstrapProvider?: string
  authSessionBootstrapAuto?: string
}

declare global {
  interface Window {
    __SKILLHUB_RUNTIME_CONFIG__?: RuntimeConfig
  }
}

function getRuntimeConfig(): RuntimeConfig {
  if (typeof window === 'undefined') {
    return {}
  }
  return window.__SKILLHUB_RUNTIME_CONFIG__ ?? {}
}

function getApiBaseUrl(): string {
  return getRuntimeConfig().apiBaseUrl ?? ''
}

function parseBooleanFlag(value: string | undefined): boolean {
  if (!value) {
    return false
  }
  return ['1', 'true', 'yes', 'on'].includes(value.trim().toLowerCase())
}

const client = createClient<paths>({ baseUrl: getApiBaseUrl() })

function getCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

function withRequestHeaders(headers?: HeadersInit): Headers {
  const merged = new Headers(headers)
  const language = i18n.resolvedLanguage?.trim()
  if (language) {
    merged.set('Accept-Language', language)
  }
  return merged
}

function withCsrf(headers?: HeadersInit): HeadersInit {
  const merged = withRequestHeaders(headers)
  const csrfToken = getCsrfToken()
  if (!csrfToken) {
    return merged
  }

  merged.set('X-XSRF-TOKEN', csrfToken)
  return merged
}

async function ensureCsrfHeaders(headers?: HeadersInit): Promise<HeadersInit> {
  if (!getCsrfToken()) {
    await client.GET('/api/v1/auth/providers', {
      headers: withRequestHeaders(),
    } as never)
  }
  return withCsrf(headers)
}

function isApiEnvelope<T>(value: unknown): value is ApiEnvelope<T> {
  return typeof value === 'object' && value !== null && 'code' in value && 'msg' in value && 'data' in value
}

export function getCsrfHeaders(headers?: HeadersInit): HeadersInit {
  return withCsrf(headers)
}

export type SessionBootstrapRuntimeConfig = {
  enabled: boolean
  provider?: string
  auto: boolean
}

export type DirectAuthRuntimeConfig = {
  enabled: boolean
  provider?: string
}

export function getDirectAuthRuntimeConfig(): DirectAuthRuntimeConfig {
  const config = getRuntimeConfig()
  const provider = config.authDirectProvider?.trim()
  return {
    enabled: parseBooleanFlag(config.authDirectEnabled) && !!provider,
    provider: provider || undefined,
  }
}

export function getSessionBootstrapRuntimeConfig(): SessionBootstrapRuntimeConfig {
  const config = getRuntimeConfig()
  const provider = config.authSessionBootstrapProvider?.trim()
  return {
    enabled: parseBooleanFlag(config.authSessionBootstrapEnabled) && !!provider,
    provider: provider || undefined,
    auto: parseBooleanFlag(config.authSessionBootstrapAuto),
  }
}

type ApiEnvelope<T> = {
  code: number
  msg: string
  data: T
  timestamp: string
  requestId: string
}

type RequestWithTimeout = RequestInit & {
  timeoutMs?: number
}

function createRequestSignal(init?: RequestWithTimeout): { signal?: AbortSignal, cleanup: () => void } {
  if (!init?.timeoutMs && !init?.signal) {
    return { signal: init?.signal ?? undefined, cleanup: () => {} }
  }

  const controller = new AbortController()
  const timeoutId = init?.timeoutMs ? window.setTimeout(() => controller.abort('timeout'), init.timeoutMs) : undefined
  const abortListener = () => controller.abort()

  if (init?.signal) {
    if (init.signal.aborted) {
      controller.abort()
    } else {
      init.signal.addEventListener('abort', abortListener, { once: true })
    }
  }

  return {
    signal: controller.signal,
    cleanup: () => {
      if (timeoutId !== undefined) {
        window.clearTimeout(timeoutId)
      }
      init?.signal?.removeEventListener('abort', abortListener)
    },
  }
}

export async function fetchJson<T>(input: RequestInfo | URL, init?: RequestWithTimeout): Promise<T> {
  const { signal, cleanup } = createRequestSignal(init)
  let response: Response
  try {
    response = await fetch(withBaseUrl(input), {
      ...init,
      signal,
      headers: withRequestHeaders(init?.headers),
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError('error.request.timeout', 408)
    }
    throw new ApiError('apiError.networkError', 0)
  } finally {
    cleanup()
  }

  let json: ApiEnvelope<T> | null = null

  try {
    json = (await response.json()) as ApiEnvelope<T>
  } catch {
    if (!response.ok) {
      throw new ApiError(`HTTP ${response.status}`, response.status)
    }
    throw new ApiError('Invalid JSON response', response.status)
  }

  if (!response.ok || json.code !== 0) {
    throw new ApiError(json.msg || `HTTP ${response.status}`, response.status, json.msg, json.msg)
  }

  return json.data
}

export async function fetchText(input: RequestInfo | URL, init?: RequestInit): Promise<string> {
  const response = await fetch(withBaseUrl(input), {
    ...init,
    headers: withRequestHeaders(init?.headers),
  })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return response.text()
}

function withBaseUrl(input: RequestInfo | URL): RequestInfo | URL {
  const baseUrl = getApiBaseUrl()
  if (!baseUrl || typeof input !== 'string' || !input.startsWith('/')) {
    return input
  }
  return prependApiBaseUrl(baseUrl, input)
}

export function buildApiUrl(path: string): string {
  const baseUrl = getApiBaseUrl()
  if (!baseUrl) {
    return path
  }
  return prependApiBaseUrl(baseUrl, path)
}

function prependApiBaseUrl(baseUrl: string, path: string): string {
  const normalizedBaseUrl = trimTrailingSlash(baseUrl)
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${normalizedBaseUrl}${normalizedPath}`
}

function trimTrailingSlash(value: string): string {
  if (value.length > 1 && value.endsWith('/')) {
    return value.slice(0, -1)
  }
  return value
}

export async function getCurrentUser(): Promise<User | null> {
  try {
    const user = await fetchJson<User>('/api/v1/auth/me')
    return {
      ...user,
      userId: user.userId ?? '',
      displayName: user.displayName ?? '',
      platformRoles: user.platformRoles ?? [],
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

export const authApi = {
  getMe: getCurrentUser,

  async getProviders(returnTo?: string): Promise<OAuthProvider[]> {
    const params = returnTo ? `?returnTo=${encodeURIComponent(returnTo)}` : ''
    const providers = await fetchJson<OAuthProvider[]>(`/api/v1/auth/providers${params}`)
    return providers
      .filter((provider) => provider.id && provider.name && provider.authorizationUrl)
      .map((provider) => ({
        ...provider,
        id: provider.id!,
        name: provider.name!,
        authorizationUrl: provider.authorizationUrl!,
      }))
  },

  async getMethods(returnTo?: string): Promise<AuthMethod[]> {
    const query = returnTo ? `?returnTo=${encodeURIComponent(returnTo)}` : ''
    const methods = await fetchJson<AuthMethod[]>(`/api/v1/auth/methods${query}`)
    return methods
      .filter((method) => method.id && method.methodType && method.provider && method.displayName && method.actionUrl)
      .map((method) => ({
        ...method,
        id: method.id,
        methodType: method.methodType,
        provider: method.provider,
        displayName: method.displayName,
        actionUrl: method.actionUrl,
      }))
  },

  async localLogin(request: LocalLoginRequest): Promise<User> {
    return fetchJson<User>('/api/v1/auth/local/login', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async localRegister(request: LocalRegisterRequest): Promise<User> {
    return fetchJson<User>('/api/v1/auth/local/register', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async changePassword(request: ChangePasswordRequest): Promise<void> {
    await fetchJson<void>('/api/v1/auth/local/change-password', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async requestPasswordReset(request: PasswordResetRequest): Promise<void> {
    await fetchJson<void>('/api/v1/auth/local/password-reset/request', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async confirmPasswordReset(request: PasswordResetConfirmRequest): Promise<void> {
    await fetchJson<void>('/api/v1/auth/local/password-reset/confirm', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async logout(): Promise<void> {
    const response = await fetch('/api/v1/auth/logout', {
      method: 'POST',
      headers: withCsrf(),
    })
    if (response.status !== 200 && response.status !== 204) {
      throw new Error(`HTTP ${response.status}`)
    }
  },

  async bootstrapSession(provider: string): Promise<User> {
    return fetchJson<User>('/api/v1/auth/session/bootstrap', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ provider }),
    })
  },

  async directLogin(provider: string, request: LocalLoginRequest): Promise<User> {
    return fetchJson<User>('/api/v1/auth/direct/login', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({
        provider,
        username: request.username,
        password: request.password,
      }),
    })
  },
}

export const accountApi = {
  async initiateMerge(request: MergeInitiateRequest): Promise<MergeInitiateResponse> {
    return fetchJson<MergeInitiateResponse>('/api/v1/account/merge/initiate', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async verifyMerge(request: MergeVerifyRequest): Promise<void> {
    await fetchJson<void>('/api/v1/account/merge/verify', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async confirmMerge(request: MergeConfirmRequest): Promise<void> {
    await fetchJson<void>('/api/v1/account/merge/confirm', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },
}

export const skillLifecycleApi = {
  async archiveSkill(namespace: string, slug: string, reason?: string): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/archive`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(reason?.trim() ? { reason: reason.trim() } : {}),
    })
  },

  async unarchiveSkill(namespace: string, slug: string): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/unarchive`, {
      method: 'POST',
      headers: await ensureCsrfHeaders(),
    })
  },

  async deleteSkill(namespace: string, slug: string, ownerId?: string): Promise<SkillDeleteResult> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    const params = ownerId ? `?ownerId=${encodeURIComponent(ownerId)}` : ''
    return fetchJson<SkillDeleteResult>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}${params}`, {
      method: 'DELETE',
      headers: await ensureCsrfHeaders(),
    })
  },

  async deleteVersion(namespace: string, slug: string, version: string): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}`, {
      method: 'DELETE',
      headers: await ensureCsrfHeaders(),
    })
  },

  async withdrawReview(namespace: string, slug: string, version: string): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}/withdraw-review`, {
      method: 'POST',
      headers: await ensureCsrfHeaders(),
    })
  },

  async rereleaseVersion(namespace: string, slug: string, version: string, targetVersion: string, confirmWarnings = false): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}/rerelease`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ targetVersion, confirmWarnings }),
    })
  },

  /**
   * Submit an UPLOADED version for review.
   * Transitions version status from UPLOADED to PENDING_REVIEW.
   */
  async submitForReview(namespace: string, slug: string, version: string, targetVisibility: 'PUBLIC' | 'NAMESPACE_ONLY'): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/submit-review`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ version, targetVisibility }),
    })
  },

  /**
   * Confirm publish for a PRIVATE skill version.
   * Transitions version status from UPLOADED to PUBLISHED without review.
   */
  async confirmPublish(namespace: string, slug: string, version: string): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/confirm-publish`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ version }),
    })
  },
}

function normalizeNamespaceSlug(namespace: string): string {
  return namespace.startsWith('@') ? namespace.slice(1) : namespace
}

export const labelApi = {
  async listVisible(): Promise<LabelItem[]> {
    return fetchJson<LabelItem[]>(`${WEB_API_PREFIX}/labels`)
  },

  async listSkillLabels(namespace: string, slug: string): Promise<LabelItem[]> {
    const cleanNamespace = normalizeNamespaceSlug(namespace)
    return fetchJson<LabelItem[]>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/labels`)
  },

  async attachSkillLabel(namespace: string, slug: string, labelSlug: string): Promise<LabelItem> {
    const cleanNamespace = normalizeNamespaceSlug(namespace)
    return fetchJson<LabelItem>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/labels/${encodeURIComponent(labelSlug)}`, {
      method: 'PUT',
      headers: await ensureCsrfHeaders(),
    })
  },

  async detachSkillLabel(namespace: string, slug: string, labelSlug: string): Promise<void> {
    const cleanNamespace = normalizeNamespaceSlug(namespace)
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/labels/${encodeURIComponent(labelSlug)}`, {
      method: 'DELETE',
      headers: await ensureCsrfHeaders(),
    })
  },

  async listAdminDefinitions(): Promise<LabelDefinition[]> {
    return fetchJson<LabelDefinition[]>('/api/v1/admin/labels')
  },

  async createAdminDefinition(request: AdminLabelInput): Promise<LabelDefinition> {
    return fetchJson<LabelDefinition>('/api/v1/admin/labels', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({
        slug: request.slug.trim(),
        type: request.type,
        visibleInFilter: request.visibleInFilter,
        sortOrder: request.sortOrder,
        translations: request.translations.map((translation) => ({
          locale: translation.locale.trim(),
          displayName: translation.displayName.trim(),
        })),
      }),
    })
  },

  async updateAdminDefinition(slug: string, request: Omit<AdminLabelInput, 'slug'>): Promise<LabelDefinition> {
    return fetchJson<LabelDefinition>(`/api/v1/admin/labels/${encodeURIComponent(slug)}`, {
      method: 'PUT',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({
        type: request.type,
        visibleInFilter: request.visibleInFilter,
        sortOrder: request.sortOrder,
        translations: request.translations.map((translation) => ({
          locale: translation.locale.trim(),
          displayName: translation.displayName.trim(),
        })),
      }),
    })
  },

  async deleteAdminDefinition(slug: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/labels/${encodeURIComponent(slug)}`, {
      method: 'DELETE',
      headers: await ensureCsrfHeaders(),
    })
  },

  async updateAdminSortOrder(items: Array<{ slug: string; sortOrder: number }>): Promise<LabelDefinition[]> {
    return fetchJson<LabelDefinition[]>('/api/v1/admin/labels/sort-order', {
      method: 'PUT',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ items }),
    })
  },
}

export const namespaceApi = {
  async create(request: CreateNamespaceRequest): Promise<Namespace> {
    const namespace = await fetchJson<Namespace>('/api/v1/namespaces', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({
        slug: normalizeNamespaceSlug(request.slug),
        displayName: request.displayName.trim(),
        description: request.description?.trim() || undefined,
      }),
    })
    return namespace
  },

  async listMine(): Promise<ManagedNamespace[]> {
    return fetchJson<ManagedNamespace[]>(`${WEB_API_PREFIX}/me/namespaces`)
  },

  async getDetail(slug: string): Promise<Namespace> {
    return fetchJson<Namespace>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}`)
  },

  async freeze(slug: string): Promise<Namespace> {
    return fetchJson<Namespace>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/freeze`, {
      method: 'POST',
      headers: await ensureCsrfHeaders(),
    })
  },

  async unfreeze(slug: string): Promise<Namespace> {
    return fetchJson<Namespace>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/unfreeze`, {
      method: 'POST',
      headers: await ensureCsrfHeaders(),
    })
  },

  async archive(slug: string, reason?: string): Promise<Namespace> {
    return fetchJson<Namespace>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/archive`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(reason?.trim() ? { reason: reason.trim() } : {}),
    })
  },

  async restore(slug: string): Promise<Namespace> {
    return fetchJson<Namespace>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/restore`, {
      method: 'POST',
      headers: await ensureCsrfHeaders(),
    })
  },

  async listMembers(slug: string): Promise<NamespaceMember[]> {
    const page = await fetchJson<{ items: NamespaceMember[] }>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/members`)
    return page.items
  },

  async searchMemberCandidates(slug: string, search: string, size = 10): Promise<NamespaceCandidateUser[]> {
    const query = new URLSearchParams({
      search: search.trim(),
      size: String(size),
    })
    return fetchJson<NamespaceCandidateUser[]>(
      `${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/member-candidates?${query.toString()}`,
    )
  },

  async addMember(slug: string, request: { userId: string; role: string }): Promise<NamespaceMember> {
    return fetchJson<NamespaceMember>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/members`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({
        userId: request.userId.trim(),
        role: request.role,
      }),
    })
  },

  async batchAddMembers(slug: string, members: Array<{ userId: string; role: string }>): Promise<BatchMemberResponse> {
    return fetchJson<BatchMemberResponse>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/members/batch`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ members }),
    })
  },

  async updateMemberRole(slug: string, userId: string, role: string): Promise<NamespaceMember> {
    return fetchJson<NamespaceMember>(
      `${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/members/${encodeURIComponent(userId)}/role`,
      {
        method: 'PUT',
        headers: await ensureCsrfHeaders({
          'Content-Type': 'application/json',
        }),
        body: JSON.stringify({ role }),
      },
    )
  },

  async removeMember(slug: string, userId: string): Promise<void> {
    await fetchJson<void>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/members/${encodeURIComponent(userId)}`, {
      method: 'DELETE',
      headers: await ensureCsrfHeaders(),
    })
  },
}

export const tokenApi = {
  async getTokens(params?: { page?: number, size?: number }): Promise<{ items: ApiToken[], total: number, page: number, size: number }> {
    const queryPage = params?.page ?? 0
    const querySize = params?.size ?? 10
    const page = await fetchJson<{ items: ApiToken[], total: number, page: number, size: number }>(
      `/api/v1/tokens?page=${queryPage}&size=${querySize}`,
    )
    return {
      ...page,
      items: page.items
        .filter((token) => token.id !== undefined && token.name && token.tokenPrefix && token.createdAt)
        .map((token) => ({
          ...token,
          id: token.id!,
          name: token.name!,
          tokenPrefix: token.tokenPrefix!,
          createdAt: token.createdAt!,
        })),
    }
  },

  async createToken(request: CreateTokenRequest): Promise<CreateTokenResponse> {
    const token = await fetchJson<CreateTokenResponse>('/api/v1/tokens', {
      method: 'POST',
      headers: withCsrf({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
    if (!token.token || token.id === undefined || !token.name || !token.tokenPrefix || !token.createdAt) {
      throw new Error('Invalid token creation response')
    }
    return {
      ...token,
      token: token.token,
      id: token.id,
      name: token.name,
      tokenPrefix: token.tokenPrefix,
      createdAt: token.createdAt,
    }
  },

  async updateTokenExpiration(tokenId: number, expiresAt?: string): Promise<ApiToken> {
    return fetchJson<ApiToken>(`/api/v1/tokens/${tokenId}/expiration`, {
      method: 'PUT',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ expiresAt: expiresAt ?? '' }),
    })
  },

  async deleteToken(tokenId: number): Promise<void> {
    const { error, response } = await client.DELETE('/api/v1/tokens/{id}', {
      params: {
        path: {
          id: tokenId,
        },
      },
      headers: withCsrf(),
    } as never)

    if (response.status === 204) {
      return
    }

    const envelope = (error && isApiEnvelope<void>(error) ? error : null) as { msg?: string } | null
    if (!response.ok || error) {
      throw new ApiError(envelope?.msg || `HTTP ${response.status}`, response.status, envelope?.msg, envelope?.msg)
    }
  },
}

export const reviewApi = {
  async list(params: { status: string; namespaceId?: number; page?: number; size?: number; sortDirection?: 'ASC' | 'DESC' }) {
    const searchParams = new URLSearchParams()
    searchParams.set('status', params.status)
    if (params.namespaceId !== undefined) {
      searchParams.set('namespaceId', String(params.namespaceId))
    }
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    searchParams.set('sortDirection', params.sortDirection ?? 'DESC')
    return fetchJson<{ items: ReviewTask[]; total: number; page: number; size: number }>(
      `${WEB_API_PREFIX}/reviews?${searchParams.toString()}`,
    )
  },

  async get(id: number): Promise<ReviewTask> {
    return fetchJson<ReviewTask>(`${WEB_API_PREFIX}/reviews/${id}`)
  },

  async getSkillDetail(id: number): Promise<ReviewSkillDetail> {
    return fetchJson<ReviewSkillDetail>(`${WEB_API_PREFIX}/reviews/${id}/skill-detail`)
  },

  async approve(id: number, comment?: string): Promise<void> {
    await fetchJson<void>(`${WEB_API_PREFIX}/reviews/${id}/approve`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment }),
    })
  },

  async reject(id: number, comment: string): Promise<void> {
    await fetchJson<void>(`${WEB_API_PREFIX}/reviews/${id}/reject`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment }),
    })
  },
}

export const promotionApi = {
  async submit(request: { sourceSkillId: number; sourceVersionId: number; targetNamespaceId: number }): Promise<void> {
    await fetchJson<void>(`${WEB_API_PREFIX}/promotions`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async list(params: { status?: string; page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    searchParams.set('status', params.status ?? 'PENDING')
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    return fetchJson<{ items: PromotionTask[]; total: number; page: number; size: number }>(
      `${WEB_API_PREFIX}/promotions?${searchParams.toString()}`,
    )
  },

  async get(id: number): Promise<PromotionTask> {
    return fetchJson<PromotionTask>(`${WEB_API_PREFIX}/promotions/${id}`)
  },

  async approve(id: number, comment?: string): Promise<void> {
    await fetchJson<void>(`${WEB_API_PREFIX}/promotions/${id}/approve`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment }),
    })
  },

  async reject(id: number, comment?: string): Promise<void> {
    await fetchJson<void>(`${WEB_API_PREFIX}/promotions/${id}/reject`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment }),
    })
  },
}

export const reportApi = {
  async submitSkillReport(namespace: string, slug: string, request: { reason: string; details?: string }): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/reports`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async listSkillReports(params: { status?: string; page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    searchParams.set('status', params.status ?? 'PENDING')
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    return fetchJson<{ items: SkillReport[]; total: number; page: number; size: number }>(
      `/api/v1/admin/skill-reports?${searchParams.toString()}`,
    )
  },

  async resolveSkillReport(id: number, comment?: string, disposition: ReportDisposition = 'RESOLVE_ONLY'): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/skill-reports/${id}/resolve`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment, disposition }),
    })
  },

  async dismissSkillReport(id: number, comment?: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/skill-reports/${id}/dismiss`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment }),
    })
  },
}

export const governanceApi = {
  async getSummary(): Promise<GovernanceSummary> {
    return fetchJson<GovernanceSummary>(`${WEB_API_PREFIX}/governance/summary`)
  },

  async getInbox(params: { type?: string; page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    if (params.type) searchParams.set('type', params.type)
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    return fetchJson<PagedResponse<GovernanceInboxItem>>(
      `${WEB_API_PREFIX}/governance/inbox?${searchParams.toString()}`,
    )
  },

  async getActivity(params: { page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    return fetchJson<PagedResponse<GovernanceActivityItem>>(
      `${WEB_API_PREFIX}/governance/activity?${searchParams.toString()}`,
    )
  },

  async getNotifications(params: { page?: number; size?: number }): Promise<PagedResponse<GovernanceNotification>> {
    const searchParams = new URLSearchParams()
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    return fetchJson<PagedResponse<GovernanceNotification>>(`${WEB_API_PREFIX}/governance/notifications?${searchParams.toString()}`)
  },

  async markNotificationRead(id: number): Promise<GovernanceNotification> {
    return fetchJson<GovernanceNotification>(`${WEB_API_PREFIX}/governance/notifications/${id}/read`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },

  async rebuildSearchIndex(): Promise<void> {
    await fetchJson<void>('/api/v1/admin/search/rebuild', {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },
}

export const meApi = {
  async getSkills(params?: { page?: number; size?: number; filter?: string }): Promise<{ items: SkillSummary[]; total: number; page: number; size: number }> {
    const searchParams = new URLSearchParams()
    searchParams.set('page', String(params?.page ?? 0))
    searchParams.set('size', String(params?.size ?? 10))
    if (params?.filter) {
      searchParams.set('filter', params.filter)
    }
    return fetchJson<{ items: SkillSummary[]; total: number; page: number; size: number }>(`${WEB_API_PREFIX}/me/skills?${searchParams.toString()}`)
  },

  async getStarsPage(params?: { page?: number; size?: number }): Promise<{ items: SkillSummary[]; total: number; page: number; size: number }> {
    const searchParams = new URLSearchParams()
    searchParams.set('page', String(params?.page ?? 0))
    searchParams.set('size', String(params?.size ?? 12))
    return fetchJson<{ items: SkillSummary[]; total: number; page: number; size: number }>(`${WEB_API_PREFIX}/me/stars?${searchParams.toString()}`)
  },

  async getStars(): Promise<SkillSummary[]> {
    const items: SkillSummary[] = []
    let page = 0
    const size = 100
    let hasMore = true

    while (hasMore) {
      const response = await meApi.getStarsPage({ page, size })
      items.push(...response.items)

      hasMore = (page + 1) * response.size < response.total && response.items.length > 0
      page += 1
    }

    return items
  },
}

export const profileApi = {
  async getProfile(): Promise<{
    displayName: string
    avatarUrl: string | null
    email: string | null
    pendingChanges: {
      status: string
      changes: Record<string, string>
      reviewComment: string | null
      createdAt: string
    } | null
    fieldPolicies: Record<string, { editable: boolean; requiresReview: boolean }>
  }> {
    return fetchJson('/api/v1/user/profile')
  },
  async updateProfile(request: Record<string, string>): Promise<{
    status: string
    appliedFields?: Record<string, string>
    pendingFields?: Record<string, string>
  }> {
    return fetchJson<{ status: string; appliedFields?: Record<string, string>; pendingFields?: Record<string, string> }>('/api/v1/user/profile', {
      method: 'PATCH',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },
}

export const adminApi = {
  async getUsers(params: { search?: string; status?: string; page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    if (params.search) searchParams.set('search', params.search)
    if (params.status) searchParams.set('status', params.status)
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    const response = await fetchJson<{
      items: Array<{
        id: string
        username: string
        email?: string
        platformRoles?: string[]
        status: string
        createdAt: string
      }>
      total: number
      page: number
      size: number
    }>(
      `/api/v1/admin/users?${searchParams.toString()}`,
    )
    return {
      ...response,
      items: response.items
        .filter((user) => user.id && user.username && user.status && user.createdAt)
        .map((user) => ({
          userId: user.id,
          username: user.username,
          email: user.email,
          platformRoles: user.platformRoles ?? [],
          status: user.status,
          createdAt: user.createdAt,
        })),
    }
  },

  async updateUserRole(userId: string, role: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/users/${userId}/role`, {
      method: 'PUT',
      headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ role }),
    })
  },

  async updateUserStatus(userId: string, status: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/users/${userId}/status`, {
      method: 'PUT',
      headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ status }),
    })
  },

  async approveUser(userId: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/users/${userId}/approve`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },

  async disableUser(userId: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/users/${userId}/disable`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },

  async enableUser(userId: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/users/${userId}/enable`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },

  async triggerPasswordReset(userId: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/users/${userId}/password-reset`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },

  async getAuditLogs(params: {
    action?: string
    userId?: string
    requestId?: string
    ipAddress?: string
    resourceType?: string
    resourceId?: string
    startTime?: string
    endTime?: string
    page?: number
    size?: number
  }) {
    const searchParams = new URLSearchParams()
    if (params.action) searchParams.set('action', params.action)
    if (params.userId) searchParams.set('userId', params.userId)
    if (params.requestId) searchParams.set('requestId', params.requestId)
    if (params.ipAddress) searchParams.set('ipAddress', params.ipAddress)
    if (params.resourceType) searchParams.set('resourceType', params.resourceType)
    if (params.resourceId) searchParams.set('resourceId', params.resourceId)
    if (params.startTime) searchParams.set('startTime', params.startTime)
    if (params.endTime) searchParams.set('endTime', params.endTime)
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    return fetchJson<{ items: AuditLogItem[]; total: number; page: number; size: number }>(
      `/api/v1/admin/audit-logs?${searchParams.toString()}`,
    )
  },

  async hideSkill(skillId: number, reason?: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/skills/${skillId}/hide`, {
      method: 'POST',
      headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ reason }),
    })
  },

  async unhideSkill(skillId: number): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/skills/${skillId}/unhide`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },

  async yankVersion(versionId: number, reason?: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/skills/versions/${versionId}/yank`, {
      method: 'POST',
      headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ reason }),
    })
  },

  async getProfileReviews(params: { status?: string; page?: number; size?: number; sortDirection?: 'ASC' | 'DESC' }) {
    const searchParams = new URLSearchParams()
    if (params.status) searchParams.set('status', params.status)
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    searchParams.set('sortDirection', params.sortDirection ?? 'DESC')
    const response = await fetchJson<{
      items: Array<{
        id: number
        userId: string
        username: string
        currentDisplayName: string | null
        requestedDisplayName: string | null
        status: string
        machineResult: string | null
        reviewerId: string | null
        reviewerName: string | null
        reviewComment: string | null
        createdAt: string
        reviewedAt: string | null
      }>
      total: number
      page: number
      size: number
    }>(`/api/v1/admin/profile-reviews?${searchParams}`)

    return {
      ...response,
      totalElements: response.total,
      totalPages: response.size > 0 ? Math.ceil(response.total / response.size) : 0,
    }
  },

  async approveProfileReview(id: number): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/profile-reviews/${id}/approve`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },

  async rejectProfileReview(id: number, comment: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/profile-reviews/${id}/reject`, {
      method: 'POST',
      headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ comment }),
    })
  },
}

export const notificationApi = {
  async list(params: { page?: number; size?: number; category?: string }) {
    const searchParams = new URLSearchParams()
    if (params.page !== undefined) searchParams.set('page', String(params.page))
    if (params.size !== undefined) searchParams.set('size', String(params.size))
    if (params.category) searchParams.set('category', params.category)
    return fetchJson<{ items: NotificationItem[]; total: number; page: number; size: number }>(
      `${WEB_API_PREFIX}/notifications?${searchParams.toString()}`,
    )
  },

  async getUnreadCount() {
    return fetchJson<NotificationUnreadCount>(`${WEB_API_PREFIX}/notifications/unread-count`)
  },

  async markRead(id: number) {
    await fetchJson<void>(`${WEB_API_PREFIX}/notifications/${id}/read`, {
      method: 'PUT',
      headers: getCsrfHeaders(),
    })
  },

  async markAllRead() {
    return fetchJson<{ count: number }>(`${WEB_API_PREFIX}/notifications/read-all`, {
      method: 'PUT',
      headers: getCsrfHeaders(),
    })
  },

  async deleteRead(id: number) {
    await fetchJson<void>(`${WEB_API_PREFIX}/notifications/${id}`, {
      method: 'DELETE',
      headers: getCsrfHeaders(),
    })
  },

  async getPreferences() {
    return fetchJson<NotificationPreferenceItem[]>(`${WEB_API_PREFIX}/notification-preferences`)
  },

  async updatePreferences(preferences: NotificationPreferenceItem[]) {
    await fetchJson<void>(`${WEB_API_PREFIX}/notification-preferences`, {
      method: 'PUT',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ preferences }),
    })
  },
}
