/** @vitest-environment jsdom */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
vi.mock('@/i18n/config', () => ({ default: { resolvedLanguage: 'en' } }))
vi.mock('@/shared/lib/api-error', () => ({ ApiError: class ApiError extends Error { constructor(message: string, public status: number) { super(message) } }, handleApiError: vi.fn() }))
import { notificationEmailApi } from './notification-email-api'
const fetchMock = vi.fn()
function response(data: unknown, status = 200, code = 0) { return new Response(JSON.stringify({ code, msg: code ? 'rejected' : 'ok', data }), { status, headers: { 'Content-Type': 'application/json' } }) }
describe('typed notification email API boundary', () => {
  beforeEach(() => { vi.stubGlobal('fetch', fetchMock); fetchMock.mockResolvedValue(response(null)); document.cookie = 'XSRF-TOKEN=test-csrf' })
  afterEach(() => { vi.unstubAllGlobals(); fetchMock.mockReset(); document.cookie = 'XSRF-TOKEN=;max-age=0' })
  it('uses existing web API and envelope for current-user reads', async () => {
    const contact = { email: 'a***@example.test', verified: false, available: false, reason: 'UNVERIFIED', locale: 'en' }
    fetchMock.mockResolvedValue(response(contact))
    expect(await notificationEmailApi.contact()).toEqual(contact)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/web/notification-email-contact')
  })
  it('posts a purpose-specific code without arbitrary user or email fields and includes CSRF', async () => {
    await notificationEmailApi.requestCode('zh')
    const [path, init] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/web/notification-email-contact/verification-code')
    expect(JSON.parse(init.body)).toEqual({ locale: 'zh' })
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('test-csrf')
    expect(init.method).toBe('POST')
  })
  it('encodes the batch identifier and requests its independent page', async () => {
    await notificationEmailApi.batch('23/unsafe', '100')
    expect(fetchMock.mock.calls[0][0]).toBe('/api/web/notification-email-deliveries/23%2Funsafe?size=20&cursor=100')
  })
  it('honors the runtime API subpath exactly once', async () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: '/skillhub' }
    try { await notificationEmailApi.preferences(); expect(fetchMock.mock.calls[0][0]).toBe('/skillhub/api/web/notification-email-preferences') } finally { delete window.__SKILLHUB_RUNTIME_CONFIG__ }
  })
  it('preserves server errors and never returns their envelope as successful data', async () => {
    fetchMock.mockResolvedValue(response(null, 403, 403))
    await expect(notificationEmailApi.preferences()).rejects.toMatchObject({ status: 403 })
  })
  it('only retries the original batch with explicit risk acceptance', async () => {
    await notificationEmailApi.retry(11, true)
    const [path, init] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/web/admin/notification-email-deliveries/11/retry')
    expect(JSON.parse(init.body)).toEqual({ confirmDuplicateRisk: true })
  })
})
