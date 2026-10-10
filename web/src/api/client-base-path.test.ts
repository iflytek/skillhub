import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/shared/lib/base-path', () => ({ BASE_PATH: '/skillhub/' }))
vi.mock('@/i18n/config', () => ({ default: { resolvedLanguage: 'en' } }))
vi.mock('@/shared/lib/api-error', () => ({
  ApiError: class ApiError extends Error {},
  handleApiError: vi.fn(),
}))

import { buildApiUrl } from './client'

describe('API base path fallback', () => {
  beforeEach(() => {
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      writable: true,
      value: { __SKILLHUB_RUNTIME_CONFIG__: undefined },
    })
  })

  it('derives the API prefix from the deployment base path when apiBaseUrl is unset', () => {
    expect(buildApiUrl('/api/v1/auth/me')).toBe('/skillhub/api/v1/auth/me')
  })

  it('lets an explicit apiBaseUrl override the base path', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com' }
    expect(buildApiUrl('/api/v1/auth/me')).toBe('https://api.example.com/api/v1/auth/me')
  })
})
