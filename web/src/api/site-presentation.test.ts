import { afterEach, describe, expect, it, vi } from 'vitest'
import { presentationAssetUrl, sitePresentationApi } from './site-presentation'
vi.mock('./client', () => ({ ApiError: class extends Error { constructor(message: string, public status: number) { super(message) } }, buildApiUrl: (path: string) => `/skillhub${path}`, fetchJson: vi.fn(), getCsrfHeaders: () => ({}) }))

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
