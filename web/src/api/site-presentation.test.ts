import { afterEach, describe, expect, it, vi } from 'vitest'
import { defaultSitePresentation } from '@/shared/hooks/use-site-presentation'
import { fetchJson } from './client'
import { presentationAssetUrl, sitePresentationApi } from './site-presentation'
vi.mock('./client', () => ({ ApiError: class extends Error { constructor(message: string, public status: number) { super(message) } }, buildApiUrl: (path: string) => `/skillhub${path}`, fetchJson: vi.fn(), getCsrfHeaders: (initial?: HeadersInit) => { const headers = new Headers(initial); headers.set('X-XSRF-TOKEN', 'local-csrf-regression'); return headers } }))

describe('branding resource URLs', () => {
  it('resolves owned assets through the API base for subpath deployments', () => {
    expect(presentationAssetUrl('/api/v1/site-presentation/assets/1234-abcd')).toBe('/skillhub/api/v1/site-presentation/assets/1234-abcd')
  })
  it('rejects external, executable, relative, traversal and non-branding locations', () => {
    for (const path of [null, undefined, '', 'https://external.example/logo.png', '//external.example/logo.png', 'data:image/svg+xml,test', '/api/v1/download/private', '/api/v1/site-presentation/assets/../private', '/api/v1/site-presentation/assets/id?path=secret']) expect(presentationAssetUrl(path)).toBeUndefined()
  })
})


afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })
describe('bounded public presentation loading', () => {
  it('times out and aborts when headers arrive but the body never completes', async () => {
    vi.useFakeTimers()
    let signal: AbortSignal | undefined
    vi.stubGlobal('fetch', vi.fn(async (_input: unknown, init?: RequestInit) => {
      signal = init?.signal ?? undefined
      return { ok: true, status: 200, json: () => new Promise(() => {}) }
    }))
    const expected = expect(sitePresentationApi.get()).rejects.toMatchObject({ status: 408 })
    await vi.advanceTimersByTimeAsync(5001)
    await expected
    expect(signal?.aborted).toBe(true)
    expect(vi.getTimerCount()).toBe(0)
  })
})


describe('site presentation update authentication headers', () => {
  it('retains the CSRF header from a Headers instance alongside JSON content type', async () => {
    const request = { settings: { ...defaultSitePresentation(), siteName: 'Team Library' }, version: 3 }
    await sitePresentationApi.update(request)
    const calls = vi.mocked(fetchJson).mock.calls
    const [url, init] = calls[calls.length - 1]
    expect(url).toBe('/api/v1/admin/system-config/presentation')
    expect(init?.method).toBe('PUT')
    const headers = new Headers(init?.headers)
    expect(headers.get('X-XSRF-TOKEN')).toBe('local-csrf-regression')
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(JSON.parse(String(init?.body))).toEqual(request)
  })
})
