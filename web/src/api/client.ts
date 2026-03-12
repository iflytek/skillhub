import createClient from 'openapi-fetch'
import type { paths } from './generated/schema'
import type { ApiToken, CreateTokenRequest, CreateTokenResponse, OAuthProvider, User } from './types'

const client = createClient<paths>({ baseUrl: '' })

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

function isApiEnvelope<T>(value: unknown): value is ApiEnvelope<T> {
  return typeof value === 'object' && value !== null && 'code' in value && 'msg' in value && 'data' in value
}

function hasDataProperty<T>(value: unknown): value is { data: T } {
  return typeof value === 'object' && value !== null && 'data' in value
}

async function unwrap<T>(promise: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  const { data, error, response } = await promise
  if (response.status === 401) {
    throw new Error('HTTP 401')
  }
  if (error) {
    throw new Error(`HTTP ${response.status}`)
  }
  if (data === undefined) {
    throw new Error(`HTTP ${response.status}`)
  }
  if (isApiEnvelope<T>(data)) {
    if (data.code !== 0) {
      throw new Error(data.msg || `HTTP ${response.status}`)
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

type ApiEnvelope<T> = {
  code: number
  msg: string
  data: T
  timestamp: string
  requestId: string
}

export async function fetchJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  const response = await fetch(input, init)
  let json: ApiEnvelope<T> | null = null

  try {
    json = (await response.json()) as ApiEnvelope<T>
  } catch {
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    throw new Error('Invalid JSON response')
  }

  if (!response.ok || json.code !== 0) {
    throw new Error(json.msg || `HTTP ${response.status}`)
  }

  return json.data
}

export async function fetchText(input: RequestInfo | URL, init?: RequestInit): Promise<string> {
  const response = await fetch(input, init)
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return response.text()
}

export async function getCurrentUser(): Promise<User | null> {
  try {
    return await unwrap<User>(client.GET('/api/v1/auth/me') as never)
  } catch (error) {
    if (error instanceof Error && error.message === 'HTTP 401') {
      return null
    }
    throw error
  }
}

export const authApi = {
  getMe: getCurrentUser,

  async getProviders(): Promise<OAuthProvider[]> {
    return unwrap<OAuthProvider[]>(client.GET('/api/v1/auth/providers') as never)
  },

  async logout(): Promise<void> {
    const { response, error } = await client.POST('/api/v1/auth/logout', {
      headers: withCsrf(),
    })
    if (error || (response.status !== 200 && response.status !== 204)) {
      throw new Error(`HTTP ${response.status}`)
    }
  },
}

export const tokenApi = {
  async getTokens(): Promise<ApiToken[]> {
    return unwrap<ApiToken[]>(client.GET('/api/v1/tokens') as never)
  },

  async createToken(request: CreateTokenRequest): Promise<CreateTokenResponse> {
    return unwrap<CreateTokenResponse>(client.POST('/api/v1/tokens', {
      headers: withCsrf({
        'Content-Type': 'application/json',
      }),
      body: request,
    }) as never)
  },

  async deleteToken(tokenId: number): Promise<void> {
    const { response, error } = await client.DELETE('/api/v1/tokens/{id}', {
      params: {
        path: {
          id: tokenId,
        },
      },
      headers: withCsrf(),
    })
    if (error || response.status !== 204) {
      throw new Error(`HTTP ${response.status}`)
    }
  },
}
