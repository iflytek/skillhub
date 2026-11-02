/** @vitest-environment jsdom */
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { parseAnalyticsConfig } from './config'
import { adapters } from './registry'
import { createAnalyticsController } from './controller'
import { installAnalyticsLoader } from './loader'
import { BROWSER_ID_KEY } from './adapters/datafinder/adapter'
import { currentPageType } from '../../app/analytics-page-type'
import { createMemoryHistory, createRootRoute, createRoute, createRouter } from '@tanstack/react-router'

let createDatafinderAdapter: typeof import('./adapters/datafinder/adapter').createDatafinderAdapter
beforeEach(async () => { vi.resetModules(); ({ createDatafinderAdapter } = await import('./adapters/datafinder/adapter')) })

const valid = { enabled: true, consent: 'granted' as const, adapter: 'datafinder', adapterConfig: { appId: 123456 } }
const intent = { id: 'skill.download.request', event: 'click' }
const uuid = '4398de8e-40df-4483-9eea-ed213643525f'
afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals(); localStorage.clear(); document.body.replaceChildren() })
it.each([{ appId: 1 }, { appId: 2147483647 }, { appId: 123456, product: 'my_product2' }])('accepts DataFinder public config %j', adapterConfig => {
  expect(parseAnalyticsConfig({ ...valid, adapterConfig }).config?.adapterConfig).toEqual(adapterConfig)
})
it.each([{}, { appId: 0 }, { appId: -1 }, { appId: 1.5 }, { appId: 2147483648 }, { appId: 'PRIVATE' }, { appId: NaN }, { appId: Infinity }])('rejects appId with field-only diagnostics %j', adapterConfig => {
  const result = parseAnalyticsConfig({ ...valid, adapterConfig })
  expect(result.config).toBeNull()
  expect(result.diagnostics).toEqual([{ field: 'adapterConfig.appId', reason: 'expected positive 32-bit integer' }])
})
it.each(['', 'PRIVATE', 'a-b', '1product', 'a'.repeat(33), null, 7])('rejects product %j without values', product => {
  const result = parseAnalyticsConfig({ ...valid, adapterConfig: { appId: 1, product } })
  expect(result.config).toBeNull()
  expect(result.diagnostics).toEqual([{ field: 'adapterConfig.product', reason: 'expected lowercase product name' }])
})
it.each(['appKey', 'X-MCS-AppKey', 'upstream', 'websiteId', 'PRIVATE'])('rejects extra setting %s without echoing its name or value', key => {
  const result = parseAnalyticsConfig({ ...valid, adapterConfig: { appId: 1, [key]: 'PRIVATE' } })
  expect(result.config).toBeNull()
  expect(result.diagnostics).toEqual([{ field: 'adapterConfig', reason: 'unknown field' }])
})
function transport() {
  const fetch = vi.fn<typeof globalThis.fetch>().mockImplementation(async () => new Response(JSON.stringify({ e: 0, message: 'success', sc: 1, tc: 1 })))
  vi.stubGlobal('fetch', fetch)
  vi.spyOn(crypto, 'randomUUID').mockReturnValue(uuid)
  return fetch
}
it('sends exact minimal JSON without SDK, account, URL or content; reads generic context at send time', async () => {
  const fetch = transport(), now = vi.spyOn(Date, 'now').mockReturnValue(1234)
  let pageType = '/skills'
  const adapter = createDatafinderAdapter({ appId: 123456, product: 'portal' }, '/base/_skillhub-analytics', { currentPageType: () => pageType })
  pageType = '/space/$namespace/$slug'
  await adapter.send({ ...intent, secret: 'PRIVATE' } as typeof intent)
  expect(fetch).toHaveBeenCalledTimes(1)
  const [url, options] = fetch.mock.calls[0]
  expect(url).toBe('/base/_skillhub-analytics/list')
  expect(options).toMatchObject({ method: 'POST', headers: { 'Content-Type': 'application/json' }, keepalive: true, credentials: 'omit', referrerPolicy: 'no-referrer' })
  expect(options?.body).toBe(JSON.stringify([{ user: { user_unique_id: uuid, web_id: uuid }, header: { app_id: 123456, custom: { product: 'portal', module: '/space/$namespace/$slug' } }, events: [{ event: intent.id, params: '{"event":"click"}', local_time_ms: 1234 }] }]))
  expect(document.querySelector('script')).toBeNull()
  expect(now).toHaveBeenCalled()
  adapter.stop()
})
it.each(['click', 'submit', 'keydown'])('retains dotted IDs and native %s, defaults product/module', async event => {
  const fetch = transport(), adapter = createDatafinderAdapter({ appId: 1 }, '/_skillhub-analytics')
  await adapter.send({ ...intent, event })
  const batch = JSON.parse(fetch.mock.calls[0][1]!.body as string)[0]
  expect(batch.header.custom).toEqual({ product: 'skillhub', module: 'other' })
  expect(batch.events[0]).toMatchObject({ event: intent.id, params: JSON.stringify({ event }) })
  adapter.stop()
})
it('persists only an anonymous UUID and reuses it across adapter instances', async () => {
  const fetch = transport()
  const a = createDatafinderAdapter({ appId: 1 }, '/_skillhub-analytics'); await a.send(intent); a.stop()
  expect(localStorage.getItem(BROWSER_ID_KEY)).toBe(uuid)
  const b = createDatafinderAdapter({ appId: 1 }, '/_skillhub-analytics'); await b.send(intent); b.stop()
  expect(crypto.randomUUID).toHaveBeenCalledTimes(1)
  expect(JSON.parse(fetch.mock.calls[1][1]!.body as string)[0].user).toEqual({ user_unique_id: uuid, web_id: uuid })
})
it.each(['getItem', 'setItem'] as const)('uses one in-memory page UUID when storage %s throws', async method => {
  const fetch = transport()
  vi.spyOn(Storage.prototype, method).mockImplementation(() => { throw Error('PRIVATE') })
  const a = createDatafinderAdapter({ appId: 1 }, '/_skillhub-analytics'); await a.send(intent); a.stop()
  const b = createDatafinderAdapter({ appId: 1 }, '/_skillhub-analytics'); await b.send(intent); b.stop()
  expect(JSON.parse(fetch.mock.calls[0][1]!.body as string)[0].user).toEqual(JSON.parse(fetch.mock.calls[1][1]!.body as string)[0].user)
})
it.each([true, false])('falls back to a getRandomValues v4 UUID without randomUUID (plain HTTP); storage %s', async storageWorks => {
  const fetch = vi.fn<typeof globalThis.fetch>().mockImplementation(async () => new Response(JSON.stringify({ e: 0, message: 'success', sc: 1, tc: 1 })))
  vi.stubGlobal('fetch', fetch)
  const real = globalThis.crypto
  vi.stubGlobal('crypto', { getRandomValues: <T extends ArrayBufferView<ArrayBuffer>>(array: T) => real.getRandomValues(array) })
  if (!storageWorks) vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw Error('PRIVATE') })
  const a = createDatafinderAdapter({ appId: 1 }, '/_skillhub-analytics'); await a.send(intent); a.stop()
  expect(fetch).toHaveBeenCalledTimes(1)
  const { user } = JSON.parse(fetch.mock.calls[0][1]!.body as string)[0]
  expect(user.user_unique_id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  expect(user.web_id).toBe(user.user_unique_id)
  if (storageWorks) expect(localStorage.getItem(BROWSER_ID_KEY)).toBe(user.user_unique_id)
})
it('replaces non-UUID stored values instead of forwarding arbitrary storage data', async () => {
  localStorage.setItem(BROWSER_ID_KEY, 'PRIVATE')
  const fetch = transport(), a = createDatafinderAdapter({ appId: 1 }, '/_skillhub-analytics')
  await a.send(intent); a.stop()
  expect(fetch.mock.calls[0][1]?.body).not.toContain('PRIVATE')
})
it('denied does not import or touch storage/identity; late registered chunk never starts', async () => {
  const storage = vi.spyOn(Storage.prototype, 'getItem'), id = vi.spyOn(crypto, 'randomUUID'), load = vi.fn()
  const api = installAnalyticsLoader(() => ({ ...valid, consent: 'denied' }), load)
  expect(load).not.toHaveBeenCalled(); api.stop()
  const signal = new AbortController()
  const late = adapters.datafinder.create(valid.adapterConfig, '/_skillhub-analytics', signal.signal)
  signal.abort(); const a = await late; await a.send(intent)
  expect(storage).not.toHaveBeenCalled(); expect(id).not.toHaveBeenCalled()
})
it('passes optional generic context through loader and controller without altering helper intents', async () => {
  transport()
  const context = { currentPageType: () => '/search' }
  const factory = vi.fn().mockResolvedValue({ send: vi.fn().mockResolvedValue(undefined), stop() {} })
  const c = createAnalyticsController(valid, { factory, context }); await c.start()
  expect(factory.mock.calls[0][3]).toBe(context); c.stop()
  const create = vi.fn().mockReturnValue({ start: async () => {}, stop() {} })
  const api = installAnalyticsLoader(() => valid, async () => ({ createAnalyticsController: create }), context)
  await api.start(); expect(create.mock.calls[0][1]).toEqual({ context }); api.stop()
})
it('derives only matched route templates from real parameterized URLs and handles unmatched routes', async () => {
  const root = createRootRoute(), detail = createRoute({ getParentRoute: () => root, path: '/space/$namespace/$slug' })
  const router = createRouter({ routeTree: root.addChildren([detail]), history: createMemoryHistory({ initialEntries: ['/space/private-team/private-skill?token=PRIVATE#PRIVATE'] }) })
  await router.load()
  expect(currentPageType(router)).toBe('/space/$namespace/$slug')
  await router.navigate({ to: '/not-found' as string }); await router.load()
  expect(currentPageType(router)).toBe('other')
})
it('stop prevents all later dispatch including stop reentrancy from context', async () => {
  const fetch = transport()
  const a = createDatafinderAdapter({ appId: 1 }, '/_skillhub-analytics', { currentPageType: () => { a.stop(); return '/skills' } })
  await expect(a.send(intent)).resolves.toBeUndefined(); expect(fetch).not.toHaveBeenCalled()
  await a.send(intent); expect(fetch).not.toHaveBeenCalled()
})
it.each(['network', 'throw', 'timeout', 'non-json', 'body-timeout', 'rejected', 'empty'])('send never rejects or retries on %s; bound is 3 seconds', async mode => {
  vi.useFakeTimers()
  const fetch = transport(), error = vi.spyOn(console, 'error').mockImplementation(() => {})
  if (mode === 'network') fetch.mockRejectedValue(Error('PRIVATE'))
  if (mode === 'throw') fetch.mockImplementation(() => { throw Error('PRIVATE') })
  if (mode === 'timeout') fetch.mockImplementation(() => new Promise(() => {}))
  if (mode === 'body-timeout') fetch.mockResolvedValue({ ok: true, json: () => new Promise(() => {}) } as Response)
  if (mode === 'non-json') fetch.mockResolvedValue(new Response('PRIVATE'))
  if (mode === 'rejected') fetch.mockResolvedValue(new Response('{"e":1,"sc":1}'))
  if (mode === 'empty') fetch.mockResolvedValue(new Response('{"e":0,"sc":0}'))
  const a = createDatafinderAdapter({ appId: 1 }, '/_skillhub-analytics')
  const sent = a.send(intent)
  await vi.advanceTimersByTimeAsync(3000); await expect(sent).resolves.toBeUndefined()
  expect(fetch).toHaveBeenCalledTimes(1)
  if (['timeout', 'body-timeout'].includes(mode)) expect(fetch.mock.calls[0][1]?.signal?.aborted).toBe(true)
  expect(JSON.stringify(error.mock.calls)).not.toContain('PRIVATE')
  a.stop()
})
it('does not block another send behind an unfinished request and cannot recall dispatched requests', async () => {
  vi.useFakeTimers(); const fetch = transport()
  fetch.mockImplementationOnce(() => new Promise(() => {}))
  const a = createDatafinderAdapter({ appId: 1 }, '/_skillhub-analytics')
  const first = a.send(intent); await a.send(intent); expect(fetch).toHaveBeenCalledTimes(2)
  a.stop(); await a.send(intent); expect(fetch).toHaveBeenCalledTimes(2)
  await vi.advanceTimersByTimeAsync(3000); await first
})
it.each([['{"e":0,"sc":0}', 200], ['{"e":1,"sc":1}', 200], ['{"e":0,"sc":"1"}', 200], ['{"e":0,"sc":1}', 400]])('does not mistake HTTP or invalid acceptance counters for success: %s %s', async (reply, status) => {
  const fetch = transport(), error = vi.spyOn(console, 'error').mockImplementation(() => {})
  fetch.mockResolvedValue(new Response(reply as string, { status: status as number }))
  const a = createDatafinderAdapter({ appId: 1 }, '/_skillhub-analytics')
  await a.send(intent); a.stop()
  expect(error).toHaveBeenCalledExactlyOnceWith('[analytics] adapter: collector did not accept event')
})
it.each([{ id: 'bad', event: 'click' }, { ...intent, event: 'input' }])('drops invalid intents %j', async input => {
  const fetch = transport(), a = createDatafinderAdapter({ appId: 1 }, '/_skillhub-analytics')
  await a.send(input); expect(fetch).not.toHaveBeenCalled(); a.stop()
})
