/** @vitest-environment jsdom */
import { afterEach, expect, it, vi } from 'vitest'
import { parseAnalyticsConfig } from './config'
import { installAnalyticsLoader, afterAppMount } from './loader'
import { createAnalyticsController } from './controller'
import { adapters } from './registry'

const valid = { enabled: true, consent: 'granted', adapter: 'umami', adapterConfig: { websiteId: '4398de8e-40df-4483-9eea-ed213643525f' } }
afterEach(() => { vi.restoreAllMocks(); document.body.replaceChildren() })
it.each([undefined, '', '{}', { enabled: false }, { ...valid, consent: 'denied' }])('never imports when closed: %j', async raw => {
  const load = vi.fn()
  const api = installAnalyticsLoader(() => raw, load)
  await Promise.resolve()
  expect(load).not.toHaveBeenCalled()
  api.stop()
})
it.each(['PRIVATE', [], null, { enabled: 'PRIVATE' }, { ...valid, consent: 'PRIVATE' }, { ...valid, adapter: 'PRIVATE' }, { ...valid, adapterConfig: { websiteId: 'PRIVATE' } }])('disables invalid config without echoing raw values', raw => {
  const result = parseAnalyticsConfig(raw)
  expect(result.config).toBeNull()
  expect(result.diagnostics.length).toBeGreaterThan(0)
  expect(JSON.stringify(result.diagnostics)).not.toContain('PRIVATE')
})
it('defaults closed, preserves rules replacement and delegates adapter validation', () => {
  expect(parseAnalyticsConfig({}).config).toMatchObject({ enabled: false, consent: 'denied' })
  expect(parseAnalyticsConfig({ ...valid, rules: {} }).config?.rules).toEqual({})
  expect(parseAnalyticsConfig(valid).config).not.toHaveProperty('websiteId')
  expect(Object.keys(adapters)).toEqual(['umami'])
  expect(parseAnalyticsConfig({ ...valid, adapter: '__proto__' }).config).toBeNull()
})
it('cancels a late module before controller initialization; start explicitly grants consent', async () => {
  let resolve!: (value: { createAnalyticsController: typeof createAnalyticsController }) => void
  const create = vi.fn()
  const load = vi.fn(() => new Promise<{ createAnalyticsController: typeof createAnalyticsController }>(r => { resolve = r }))
  const api = installAnalyticsLoader(() => valid, load)
  api.stop()
  resolve({ createAnalyticsController: create })
  await Promise.resolve(); await Promise.resolve()
  expect(create).not.toHaveBeenCalled()
  const controller = { start: vi.fn(), stop: vi.fn() }
  load.mockResolvedValue({ createAnalyticsController: vi.fn(() => controller) })
  await api.start()
  expect(controller.start).toHaveBeenCalledTimes(1)
  api.stop()
  expect(controller.stop).toHaveBeenCalledTimes(1)
})
it('denied is not imported until public start; repeated starts share one import', async () => {
  const controller = { start: vi.fn(), stop: vi.fn() }
  const load = vi.fn().mockResolvedValue({ createAnalyticsController: () => controller })
  const api = installAnalyticsLoader(() => ({ ...valid, consent: 'denied' }), load)
  expect(load).not.toHaveBeenCalled()
  await Promise.all([api.start(), api.start()])
  expect(load).toHaveBeenCalledTimes(1)
  expect(controller.start).toHaveBeenCalledTimes(1)
  api.stop()
})
it('isolates a throwing config reader without exposing exception values', async () => {
  const error = vi.spyOn(console, 'error').mockImplementation(() => {})
  const load = vi.fn()
  expect(() => installAnalyticsLoader(() => { throw Error('PRIVATE') }, load)).not.toThrow()
  expect(load).not.toHaveBeenCalled()
  expect(JSON.stringify(error.mock.calls)).not.toContain('PRIVATE')
})
it('isolates failed dynamic imports and does not log rejection values', async () => {
  const error = vi.spyOn(console, 'error').mockImplementation(() => {})
  const api = installAnalyticsLoader(() => valid, () => Promise.reject(Error('PRIVATE')))
  await Promise.resolve(); await Promise.resolve(); await Promise.resolve()
  expect(JSON.stringify(error.mock.calls)).not.toContain('PRIVATE')
  api.stop()
})
it('controller preserves first initialization, stops late adapters and isolates failures', async () => {
  let resolve!: (value: { send: ReturnType<typeof vi.fn<() => Promise<void>>>; stop: ReturnType<typeof vi.fn<() => void>> }) => void
  const factory = vi.fn(() => new Promise<{ send: ReturnType<typeof vi.fn<() => Promise<void>>>; stop: ReturnType<typeof vi.fn<() => void>> }>(r => { resolve = r }))
  const config = parseAnalyticsConfig(valid).config!
  const controller = createAnalyticsController(config, { factory, root: document })
  const first = controller.start()
  expect(controller.start()).toBe(first)
  controller.stop()
  const adapter = { send: vi.fn<() => Promise<void>>().mockResolvedValue(undefined), stop: vi.fn<() => void>() }
  resolve(adapter); await first
  expect(adapter.stop).toHaveBeenCalledTimes(1)
  document.body.innerHTML = '<button data-analytics-id="skill.download.request">download</button>'
  document.querySelector('button')!.click()
  expect(adapter.send).not.toHaveBeenCalled()
  factory.mockResolvedValue(adapter)
  await controller.start()
  document.querySelector('button')!.click()
  expect(adapter.send).toHaveBeenCalledTimes(1)
  controller.stop(); controller.stop()
  factory.mockRejectedValue(Error('PRIVATE'))
  await expect(controller.start()).resolves.toBeUndefined()
  controller.stop()
})
it('passes cancellation to an asynchronous factory before stop returns', async () => {
  let signal: AbortSignal | undefined
  const c = createAnalyticsController(parseAnalyticsConfig(valid).config!, { factory: async (_config, _prefix, cancellation) => { signal = cancellation; return { send: async () => {}, stop() {} } } })
  const pending = c.start()
  c.stop()
  expect(signal?.aborted).toBe(true)
  await pending
})
it('waits for a real app commit and schedules separately', async () => {
  const root = document.createElement('div'), run = vi.fn()
  afterAppMount(root, run)
  expect(run).not.toHaveBeenCalled()
  root.append(document.createElement('main'))
  await Promise.resolve()
  expect(run).not.toHaveBeenCalled()
  await vi.waitFor(() => expect(run).toHaveBeenCalledTimes(1))
  root.append(document.createElement('aside'))
  await Promise.resolve()
  expect(run).toHaveBeenCalledTimes(1)
})
it('controller uses resolveRules and reports fields only, not invalid rule IDs', async () => {
  const error = vi.spyOn(console, 'error').mockImplementation(() => {})
  const send = vi.fn().mockRejectedValue(Error('PRIVATE'))
  const c = createAnalyticsController({ ...parseAnalyticsConfig(valid).config!, rules: { PRIVATE: 'drop', 'skill.download.request': 'click' } }, { root: document, factory: async () => ({ send, stop() {} }) })
  await c.start()
  document.body.innerHTML = '<button data-analytics-id="skill.download.request">download</button>'
  document.querySelector('button')!.click(); await Promise.resolve()
  expect(send).toHaveBeenCalledTimes(1)
  expect(JSON.stringify(error.mock.calls)).not.toContain('PRIVATE')
  c.stop()
})
