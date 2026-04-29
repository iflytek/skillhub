import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const originalWindow = globalThis.window

function setMockWindow(runtimeConfig?: Window['__SKILLHUB_RUNTIME_CONFIG__']) {
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    writable: true,
    value: {
      __SKILLHUB_RUNTIME_CONFIG__: runtimeConfig,
    } satisfies Pick<Window, '__SKILLHUB_RUNTIME_CONFIG__'>,
  })
}

// Mock i18n before importing client
vi.mock('@/i18n/config', () => ({
  default: { resolvedLanguage: 'en' },
}))

// Mock api-error before importing client
vi.mock('@/shared/lib/api-error', () => ({
  ApiError: class ApiError extends Error {
    status: number
    serverMessage?: string
    serverMessageKey?: string
    constructor(message: string, status: number, serverMessage?: string, serverMessageKey?: string) {
      super(message)
      this.status = status
      this.serverMessage = serverMessage
      this.serverMessageKey = serverMessageKey
    }
  },
  handleApiError: vi.fn(),
}))

import {
  WEB_API_PREFIX,
  authApi,
  buildAuthRedirectUrl,
  buildApiUrl,
  fetchText,
  getDirectAuthRuntimeConfig,
  getSessionBootstrapRuntimeConfig,
} from './client'

beforeEach(() => {
  setMockWindow()
})

afterEach(() => {
  vi.unstubAllGlobals()

  if (originalWindow) {
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      writable: true,
      value: originalWindow,
    })
    return
  }

  Reflect.deleteProperty(globalThis, 'window')
})

describe('WEB_API_PREFIX', () => {
  it('uses the /api/web prefix for web-facing endpoints', () => {
    expect(WEB_API_PREFIX).toBe('/api/web')
  })
})

describe('buildApiUrl', () => {
  it('returns the path as-is when no runtime base URL is configured', () => {
    expect(buildApiUrl('/api/v1/auth/me')).toBe('/api/v1/auth/me')
  })

  it('prepends the runtime base URL when one is set', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com' }
    const url = buildApiUrl('/api/v1/auth/me')
    expect(url).toBe('https://api.example.com/api/v1/auth/me')
  })

  it('handles a trailing slash on the base URL', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com/' }
    const url = buildApiUrl('/api/v1/auth/me')
    expect(url).toBe('https://api.example.com/api/v1/auth/me')
  })

  it('preserves base URL path prefixes', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com/skill_hub' }
    const url = buildApiUrl('/api/v1/auth/me')
    expect(url).toBe('https://api.example.com/skill_hub/api/v1/auth/me')
  })

  it('supports relative base URL path prefixes', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: '/skill_hub' }
    const url = buildApiUrl('/api/v1/auth/me')
    expect(url).toBe('/skill_hub/api/v1/auth/me')
  })
})

describe('buildAuthRedirectUrl', () => {
  it('leaves absolute URLs unchanged', () => {
    expect(buildAuthRedirectUrl('https://auth.example.com/login')).toBe('https://auth.example.com/login')
  })

  it('rewrites relative auth entry URLs through the runtime api base URL', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com' }
    expect(buildAuthRedirectUrl('/api/v1/auth/uass/redirect?returnTo=%2Fdashboard'))
      .toBe('https://api.example.com/api/v1/auth/uass/redirect?returnTo=%2Fdashboard')
  })
})

describe('fetchText', () => {
  it('applies base URL path prefixes for fetch requests', async () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com/skill_hub' }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: async () => 'ok',
    })
    vi.stubGlobal('fetch', fetchMock)

    await fetchText('/api/v1/auth/me')

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/skill_hub/api/v1/auth/me',
      expect.objectContaining({
        headers: expect.any(Headers),
      }),
    )
  })

  it('includes credentials when an absolute runtime api base URL is configured', async () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com' }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: async () => 'ok',
    })
    vi.stubGlobal('fetch', fetchMock)

    await fetchText('/api/v1/auth/me')

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/api/v1/auth/me',
      expect.objectContaining({
        credentials: 'include',
      }),
    )
  })
})

describe('authApi', () => {
  it('includes credentials for auth method discovery when using an absolute api base URL', async () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com' }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        code: 0,
        msg: 'ok',
        data: [],
        timestamp: '2026-04-30T00:00:00Z',
        requestId: 'req-1',
      }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await authApi.getMethods('/dashboard')

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/api/v1/auth/methods?returnTo=%2Fdashboard',
      expect.objectContaining({
        credentials: 'include',
      }),
    )
  })

  it('uses the UASS logout endpoint on the runtime API origin for enterprise sessions', async () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com' }
    Object.defineProperty(globalThis, 'document', {
      configurable: true,
      writable: true,
      value: { cookie: 'XSRF-TOKEN=test-token' } satisfies Pick<Document, 'cookie'>,
    })
    const fetchMock = vi.fn().mockResolvedValue({
      status: 204,
    })
    vi.stubGlobal('fetch', fetchMock)

    await authApi.logout('uass')

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/api/v1/auth/uass/logout',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
      }),
    )
  })

  it('keeps the legacy logout endpoint for non-UASS sessions', async () => {
    Object.defineProperty(globalThis, 'document', {
      configurable: true,
      writable: true,
      value: { cookie: '' } satisfies Pick<Document, 'cookie'>,
    })
    const fetchMock = vi.fn().mockResolvedValue({
      status: 204,
    })
    vi.stubGlobal('fetch', fetchMock)

    await authApi.logout()

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/auth/logout',
      expect.objectContaining({
        method: 'POST',
      }),
    )
  })
})

describe('getDirectAuthRuntimeConfig', () => {
  it('returns disabled when no runtime config is present', () => {
    const config = getDirectAuthRuntimeConfig()
    expect(config.enabled).toBe(false)
    expect(config.provider).toBeUndefined()
  })

  it('returns enabled with provider when both flag and provider are set', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = {
      authDirectEnabled: 'true',
      authDirectProvider: 'ldap',
    }
    const config = getDirectAuthRuntimeConfig()
    expect(config.enabled).toBe(true)
    expect(config.provider).toBe('ldap')
  })

  it('returns disabled when the flag is true but the provider is missing', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = {
      authDirectEnabled: 'true',
    }
    const config = getDirectAuthRuntimeConfig()
    expect(config.enabled).toBe(false)
  })

  it('returns disabled when the flag is false', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = {
      authDirectEnabled: 'false',
      authDirectProvider: 'ldap',
    }
    const config = getDirectAuthRuntimeConfig()
    expect(config.enabled).toBe(false)
  })

  it('treats various truthy flag values correctly', () => {
    for (const flag of ['1', 'yes', 'on', 'TRUE', ' True ']) {
      window.__SKILLHUB_RUNTIME_CONFIG__ = {
        authDirectEnabled: flag,
        authDirectProvider: 'ldap',
      }
      expect(getDirectAuthRuntimeConfig().enabled).toBe(true)
    }
  })
})

describe('getSessionBootstrapRuntimeConfig', () => {
  it('returns disabled when no runtime config is present', () => {
    const config = getSessionBootstrapRuntimeConfig()
    expect(config.enabled).toBe(false)
    expect(config.auto).toBe(false)
    expect(config.provider).toBeUndefined()
  })

  it('returns fully enabled config when all flags and provider are set', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = {
      authSessionBootstrapEnabled: '1',
      authSessionBootstrapProvider: 'sso',
      authSessionBootstrapAuto: 'true',
    }
    const config = getSessionBootstrapRuntimeConfig()
    expect(config.enabled).toBe(true)
    expect(config.provider).toBe('sso')
    expect(config.auto).toBe(true)
  })

  it('returns disabled when the provider is blank', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = {
      authSessionBootstrapEnabled: 'true',
      authSessionBootstrapProvider: '  ',
    }
    const config = getSessionBootstrapRuntimeConfig()
    expect(config.enabled).toBe(false)
  })
})
