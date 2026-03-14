import createClient from 'openapi-fetch'
import type { paths } from './generated/schema'
import type {
  ChangePasswordRequest,
  ApiToken,
  CreateTokenRequest,
  CreateTokenResponse,
  MergeConfirmRequest,
  LocalLoginRequest,
  LocalRegisterRequest,
  MergeInitiateRequest,
  MergeInitiateResponse,
  MergeVerifyRequest,
  ReviewTask,
  PromotionTask,
  AdminUser,
  AuditLogItem,
  SkillSummary,
  AuthMethod,
  OAuthProvider,
  User,
} from './types'
import { ApiError } from '@/shared/lib/api-error'

export { ApiError }

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

function withCsrf(headers?: HeadersInit): HeadersInit {
  const csrfToken = getCsrfToken()
  if (!csrfToken) {
    return headers ?? {}
  }

  return {
    ...headers,
    'X-XSRF-TOKEN': csrfToken,
  }
}

async function ensureCsrfHeaders(headers?: HeadersInit): Promise<HeadersInit> {
  if (!getCsrfToken()) {
    await client.GET('/api/v1/auth/providers')
  }
  return withCsrf(headers)
}

function isApiEnvelope<T>(value: unknown): value is ApiEnvelope<T> {
  return typeof value === 'object' && value !== null && 'code' in value && 'msg' in value && 'data' in value
}

function hasDataProperty<T>(value: unknown): value is { data: T } {
  return typeof value === 'object' && value !== null && 'data' in value
}

async function unwrap<T>(promise: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  const { data, error, response } = await promise
  if (response.status === 401) {
    throw new ApiError('HTTP 401', 401)
  }
  if (error) {
    throw new ApiError(`HTTP ${response.status}`, response.status)
  }
  if (data === undefined) {
    throw new ApiError(`HTTP ${response.status}`, response.status)
  }
  if (isApiEnvelope<T>(data)) {
    if (data.code !== 0) {
      throw new ApiError(data.msg || `HTTP ${response.status}`, response.status, data.msg)
    }
    return data.data
  }
  if (hasDataProperty<T>(data)) {
    return data.data
  }
  return data
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

export async function fetchJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(withBaseUrl(input), init)
  } catch {
    throw new ApiError('Network error', 0)
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
    throw new ApiError(json.msg || `HTTP ${response.status}`, response.status, json.msg)
  }

  return json.data
}

export async function fetchText(input: RequestInfo | URL, init?: RequestInit): Promise<string> {
  const response = await fetch(withBaseUrl(input), init)
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
  return new URL(input, ensureTrailingSlash(baseUrl))
}

function ensureTrailingSlash(value: string): string {
  return value.endsWith('/') ? value : `${value}/`
}

export async function getCurrentUser(): Promise<User | null> {
  try {
    const user = await unwrap<User>(client.GET('/api/v1/auth/me') as never)
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
    const params = returnTo
      ? { query: { returnTo } }
      : undefined
    const providers = await unwrap<OAuthProvider[]>(client.GET('/api/v1/auth/providers', params as never) as never)
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

export const tokenApi = {
  async getTokens(): Promise<ApiToken[]> {
    const tokens = await unwrap<ApiToken[]>(client.GET('/api/v1/tokens') as never)
    return tokens
      .filter((token) => token.id !== undefined && token.name && token.tokenPrefix && token.createdAt)
      .map((token) => ({
        ...token,
        id: token.id!,
        name: token.name!,
        tokenPrefix: token.tokenPrefix!,
        createdAt: token.createdAt!,
      }))
  },

  async createToken(request: CreateTokenRequest): Promise<CreateTokenResponse> {
    const token = await unwrap<CreateTokenResponse>(client.POST('/api/v1/tokens', {
      headers: withCsrf({
        'Content-Type': 'application/json',
      }),
      body: request,
    }) as never)
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

  async deleteToken(tokenId: number): Promise<void> {
    await unwrap(client.DELETE('/api/v1/tokens/{id}', {
      params: {
        path: {
          id: tokenId,
        },
      },
      headers: withCsrf(),
    }) as never)
  },
}

export const reviewApi = {
  async list(params: { status: string; namespaceId?: number; page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    searchParams.set('status', params.status)
    if (params.namespaceId !== undefined) {
      searchParams.set('namespaceId', String(params.namespaceId))
    }
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    return fetchJson<{ items: ReviewTask[]; total: number; page: number; size: number }>(
      `/api/v1/reviews?${searchParams.toString()}`,
    )
  },

  async get(id: number): Promise<ReviewTask> {
    return fetchJson<ReviewTask>(`/api/v1/reviews/${id}`)
  },

  async approve(id: number, comment?: string): Promise<void> {
    await fetchJson<void>(`/api/v1/reviews/${id}/approve`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment }),
    })
  },

  async reject(id: number, comment: string): Promise<void> {
    await fetchJson<void>(`/api/v1/reviews/${id}/reject`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment }),
    })
  },
}

export const promotionApi = {
  async list(params: { status?: string; page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    searchParams.set('status', params.status ?? 'PENDING')
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    return fetchJson<{ items: PromotionTask[]; total: number; page: number; size: number }>(
      `/api/v1/promotions?${searchParams.toString()}`,
    )
  },

  async get(id: number): Promise<PromotionTask> {
    return fetchJson<PromotionTask>(`/api/v1/promotions/${id}`)
  },

  async approve(id: number, comment?: string): Promise<void> {
    await fetchJson<void>(`/api/v1/promotions/${id}/approve`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment }),
    })
  },

  async reject(id: number, comment?: string): Promise<void> {
    await fetchJson<void>(`/api/v1/promotions/${id}/reject`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment }),
    })
  },
}

export const meApi = {
  async getStars(): Promise<SkillSummary[]> {
    return fetchJson<SkillSummary[]>('/api/v1/me/stars')
  },
}

export const adminApi = {
  async getUsers(params: { search?: string; status?: string; page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    if (params.search) searchParams.set('search', params.search)
    if (params.status) searchParams.set('status', params.status)
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    return fetchJson<{ items: AdminUser[]; total: number; page: number; size: number }>(
      `/api/v1/admin/users?${searchParams.toString()}`,
    )
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

  async getAuditLogs(params: { action?: string; userId?: string; page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    if (params.action) searchParams.set('action', params.action)
    if (params.userId) searchParams.set('userId', params.userId)
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
}
