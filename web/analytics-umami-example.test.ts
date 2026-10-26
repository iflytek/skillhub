/** @vitest-environment jsdom */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createUmamiAdapter, sanitizeUmamiPayload } from '../docs/examples/analytics/observe/v1/umami-adapter.mjs'

afterEach(() => { document.head.replaceChildren(); vi.useRealTimers(); vi.restoreAllMocks() })
const website = '4398de8e-40df-4483-9eea-ed213643525f'
const intent = { id: 'skill.example.open', event: 'click' }

describe('Umami v3.4.1 adapter', () => {
  it('allowlists the FINAL payload, never SDK context or extra data', () => {
    expect(sanitizeUmamiPayload('event', {
      website, name: intent.id, data: { event: 'click', token: 'private', value: 'private' },
      hostname: 'private', url: '/private?q=secret', title: 'private', referrer: 'private',
      screen: 'private', language: 'private', tag: 'private', id: 'private', extra: 'private',
    }, website)).toEqual({ website, name: intent.id, data: { event: 'click' } })
  })
  it.each(['pageview', 'identify', 'performance', 'unknown'])('drops %s', type => {
    expect(sanitizeUmamiPayload(type, { website, name: intent.id, data: { event: 'click' } }, website)).toBeNull()
  })
  it.each(['bad', 'skill.bad-value.open', 'skill.example.open.extra', '', null, 42])('drops bad name %s', name => {
    expect(sanitizeUmamiPayload('event', { website, name, data: { event: 'click' } }, website)).toBeNull()
  })
  it('drops pageviews encoded as events, wrong websites and unknown native event kinds', () => {
    for (const payload of [null, {}, { website }, { website: 'other', name: intent.id, data: { event: 'click' } }, { website, name: intent.id, data: { event: 'input' } }]) {
      expect(sanitizeUmamiPayload('event', payload, website)).toBeNull()
    }
  })
  it('loads only the manual tracker and passes static intent fields', async () => {
    const win = { umami: undefined as unknown, skillhubUmamiBeforeSend: undefined as unknown }
    const adapter = createUmamiAdapter({ window: win, document, website })
    const script = document.querySelector('script')!
    expect(script.src).toContain('/_skillhub-umami/script.js')
    expect(script.getAttribute('data-auto-track')).toBe('false')
    expect(script.getAttribute('data-host-url')).toBe('/_skillhub-umami')
    expect(script.getAttribute('data-before-send')).toBe('skillhubUmamiBeforeSend')
    const track = vi.fn().mockResolvedValue(undefined)
    const sent = adapter.send({ ...intent, token: 'private' })
    win.umami = { track }
    script.dispatchEvent(new Event('load'))
    await expect(sent).resolves.toBeUndefined()
    expect(track).toHaveBeenCalledExactlyOnceWith(intent.id, { event: 'click' })
    adapter.stop()
  })
  it('resolves when load fails, and never sends after stop', async () => {
    const win = { umami: undefined as unknown }
    const adapter = createUmamiAdapter({ window: win, document, website })
    const pending = adapter.send(intent)
    document.querySelector('script')!.dispatchEvent(new Event('error'))
    await expect(pending).resolves.toBeUndefined()
    win.umami = { track: vi.fn() }
    adapter.stop()
    await adapter.send(intent)
    expect((win.umami as { track: ReturnType<typeof vi.fn> }).track).not.toHaveBeenCalled()
  })
  it('bounds waiting and drops concurrent intents rather than building a queue', async () => {
    vi.useFakeTimers()
    const win = { umami: undefined as unknown }
    const adapter = createUmamiAdapter({ window: win, document, website, timeoutMs: 20 })
    const first = adapter.send(intent)
    await expect(adapter.send(intent)).resolves.toBeUndefined()
    await vi.advanceTimersByTimeAsync(20)
    await expect(first).resolves.toBeUndefined()
    adapter.stop()
  })
  it('resolves when tracker is absent after load, throws, rejects, or never settles', async () => {
    vi.useFakeTimers()
    for (const track of [undefined, vi.fn(() => { throw Error('blocked') }), vi.fn().mockRejectedValue(Error('offline')), vi.fn(() => new Promise(() => {}))]) {
      const win = { umami: track ? { track } : undefined }
      const adapter = createUmamiAdapter({ window: win, document, website, timeoutMs: 20 })
      document.querySelector('script')!.dispatchEvent(new Event('load'))
      const pending = adapter.send(intent)
      await vi.advanceTimersByTimeAsync(20)
      await expect(pending).resolves.toBeUndefined()
      adapter.stop()
      document.head.replaceChildren()
    }
  })
})
