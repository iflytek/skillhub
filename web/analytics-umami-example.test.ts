/** @vitest-environment jsdom */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createUmamiAdapter, sanitizeUmamiPayload } from '../docs/examples/analytics/observe/v1/umami-adapter.mjs'
import { resolveRules } from '../docs/examples/analytics/observe/v1/skillhub-intents.mjs'
import { readFileSync } from 'node:fs'

const manifest = JSON.parse(readFileSync('../docs/frontend-analytics-manifest.json', 'utf8')) as {
  counts: { uniqueIds: number }
  sites: Array<{ ids?: string[] }>
  definitions: Array<{ ids: string[] }>
}
const publishedIds = [...new Set([...manifest.sites.flatMap(site => site.ids ?? []), ...manifest.definitions.flatMap(site => site.ids)])]

afterEach(() => { document.head.replaceChildren(); vi.useRealTimers(); vi.restoreAllMocks() })
const website = '4398de8e-40df-4483-9eea-ed213643525f'
const intent = { id: 'skill.example.open', event: 'click' }

describe('optional edge deployment defaults', () => {
  const nginx = readFileSync('../docs/examples/analytics/nginx.conf', 'utf8')
  const compose = readFileSync('../docs/examples/analytics/compose.yaml', 'utf8')

  it('inherits the standard 100 MiB limit on all proxied application routes', () => {
    // Tokenize quoted strings as units so braces/semicolons in responses do not
    // affect scope. Track blocks instead of accepting a location-only limit.
    const tokens = nginx.replace(/#.*$/gm, '').match(/"[^"\n]*"|'[^'\n]*'|[{};]|[^\s{};]+/g) ?? []
    const blocks: string[] = [], statement: string[] = []
    const limits: Array<{ scope: string[]; value: string }> = []
    for (const token of tokens) {
      if (token === '{') { blocks.push(statement.join(' ')); statement.length = 0 }
      else if (token === '}') { blocks.pop(); statement.length = 0 }
      else if (token === ';') {
        if (statement[0] === 'client_max_body_size') limits.push({ scope: [...blocks], value: statement[1] })
        statement.length = 0
      } else statement.push(token)
    }
    expect(limits).toContainEqual({ scope: ['server'], value: '100M' })
    // A collector-only restriction is allowed; application locations must inherit.
    expect(limits.filter(limit => limit.scope.some(scope => scope.startsWith('location') && !scope.includes('/_skillhub-umami/')))).toEqual([])
    expect(limits.filter(limit => limit.scope.length === 1)).toEqual([{ scope: ['server'], value: '100M' }])
  })

  it('defaults to the release Web entry, not the development port', () => {
    const release = readFileSync('../compose.release.yml', 'utf8')
    expect(release).toContain('${WEB_PORT:-80}:80')
    expect(compose).toContain('SKILLHUB_WEB_UPSTREAM: ${SKILLHUB_WEB_UPSTREAM:-host.docker.internal:80}')
    expect(compose).not.toContain('host.docker.internal:8080')
  })

  it('maps the host gateway on native Linux for the edge service', () => {
    const edge = compose.split('\n  edge:\n')[1]
    expect(edge).toBeDefined()
    expect(edge).toContain('extra_hosts: ["host.docker.internal:host-gateway"]')
  })
})

describe('product ID grammar parity', () => {
  it.each([4, 5, 6])('accepts a published %i-segment ID in rules and the final payload', segments => {
    const id = publishedIds.find(id => id.split('.').length === segments)!
    expect(id).toBeTypeOf('string')
    expect(resolveRules({ [id]: 'click' })).toEqual({ active: { [id]: 'click' }, skipped: [] })
    expect(sanitizeUmamiPayload('event', { website, name: id, data: { event: 'click' } }, website))
      .toEqual({ website, name: id, data: { event: 'click' } })
  })
  it.each(['skill2.example.open', 'skill.example_open.click', 'Skill.example.open', 'skill.open', '.skill.example.open', 'skill.example.open.'])('skips non-product grammar %s', id => {
    expect(resolveRules({ [id]: 'click' })).toEqual({ active: {}, skipped: [{ id, reason: 'invalid id shape' }] })
    expect(sanitizeUmamiPayload('event', { website, name: id, data: { event: 'click' } }, website)).toBeNull()
  })
  it('accepts EVERY published ID through both boundaries (not a closed runtime enum)', () => {
    expect(publishedIds).toHaveLength(manifest.counts.uniqueIds)
    const rules = Object.fromEntries(publishedIds.map(id => [id, 'click']))
    expect(resolveRules(rules)).toEqual({ active: rules, skipped: [] })
    for (const id of publishedIds) {
      expect(sanitizeUmamiPayload('event', { website, name: id, data: { event: 'click' } }, website), id)
        .toEqual({ website, name: id, data: { event: 'click' } })
    }
  })
  it('keeps the Umami 50-character limit separate from the product grammar', () => {
    const id = `skill.example.${'a'.repeat(37)}`
    expect(id).toHaveLength(51)
    expect(resolveRules({ [id]: 'click' }).skipped).toEqual([])
    expect(sanitizeUmamiPayload('event', { website, name: id, data: { event: 'click' } }, website)).toBeNull()
    expect(sanitizeUmamiPayload('event', { website, name: id.slice(0, 50), data: { event: 'click' } }, website)).not.toBeNull()
  })
})

describe('Umami v3.4.1 dispatch-time consent boundary', () => {
  function platform(fetch = vi.fn().mockResolvedValue(undefined)) {
    let approved: unknown
    const win = {
      skillhubUmamiBeforeSend: undefined as unknown,
      umami: { track: async (name: string | object | (() => object), data?: { event: string }) => {
        const type = 'event', endpoint = '/_skillhub-umami/api/send'
        // Pinned track(object) shallow-copies; track(function) preserves its result's accessors.
        let payload: unknown = typeof name === 'string' ? { website, name, data, url: '/private', title: 'private' }
          : typeof name === 'function' ? name() : { ...name }
        const callback = (type: string, payload: unknown) => {
          approved = (win.skillhubUmamiBeforeSend as (type: string, payload: unknown) => unknown)(type, payload)
          return approved
        }
        // Mirror pinned v3.4.1 send(): await before-send, null check, then
        // serialize synchronously in the fetch expression inside its try/catch.
        payload = await Promise.resolve(callback(type, payload))
        if (!payload) return
        try {
          await fetch(endpoint, { body: JSON.stringify({ type, payload }) })
        } catch { /* SDK swallows serialization and transport failures. */ }
      } },
    }
    const adapter = createUmamiAdapter({ window: win, document, website })
    document.querySelector('script')!.dispatchEvent(new Event('load'))
    return { adapter, fetch, approved: () => approved, track: win.umami.track }
  }

  it.each([
    ['event', 'string'], ['event', 'toJSON'], ['name', 'string'], ['name', 'toJSON'],
  ])('snapshots a changing %s getter before its second %s value can leak or stop then dispatch', async (field, mode) => {
    let stopReturned = false, dispatchedAfterStop = false, reads = 0
    const fetch = vi.fn(async () => { if (stopReturned) dispatchedAfterStop = true })
    const { adapter, track } = platform(fetch)
    const lateValue = mode === 'string' ? 'PRIVATE VALUE' : {
      toJSON() { adapter.stop(); stopReturned = true; return { secret: 'PRIVATE VALUE' } },
    }
    const payload = { website, name: intent.id, data: { event: 'click' } }
    Object.defineProperty(field === 'name' ? payload : payload.data, field, {
      enumerable: true,
      get() { return ++reads === 1 ? (field === 'name' ? intent.id : 'click') : lateValue },
    })
    try {
      // Object path retains nested event accessors; function path retains name accessors.
      await expect(track(field === 'name' ? () => payload : payload)).resolves.toBeUndefined()
      expect(dispatchedAfterStop).toBe(false)
      expect(fetch.mock.calls.length).toBeLessThanOrEqual(1)
      for (const call of fetch.mock.calls as unknown as Array<[string, { body: string }]>) {
        expect(call[1].body).toBe(JSON.stringify({ type: 'event', payload: { website, name: intent.id, data: { event: 'click' } } }))
        expect(call[1].body).not.toContain('PRIVATE')
      }
      expect(reads).toBe(1)
      adapter.stop(); stopReturned = true
      await track(intent.id, { event: 'click' })
      expect(dispatchedAfterStop).toBe(false)
    } finally { adapter.stop() }
  })

  it.each(['website', 'name', 'data', 'event'])('drops a throwing %s getter without sending or unhandled rejection', async field => {
    const { adapter, fetch, track } = platform()
    const unhandled = vi.fn()
    window.addEventListener('unhandledrejection', unhandled)
    const payload = { website, name: intent.id, data: { event: 'click' } }
    Object.defineProperty(field === 'event' ? payload.data : payload, field, {
      get() { throw Error('unreadable payload') },
    })
    try {
      expect(sanitizeUmamiPayload('event', payload, website)).toBeNull()
      await expect(track(() => payload)).resolves.toBeUndefined()
      await new Promise(resolve => window.setTimeout(resolve, 0))
      expect(fetch).not.toHaveBeenCalled()
      expect(unhandled).not.toHaveBeenCalled()
    } finally {
      adapter.stop()
      window.removeEventListener('unhandledrejection', unhandled)
    }
  })

  it('reads website/name/data/event once and serializes only captured primitives despite returned-object mutation', async () => {
    const { adapter, fetch, track, approved } = platform()
    const getters = {
      website: vi.fn(() => website), name: vi.fn(() => intent.id), event: vi.fn(() => 'click'),
      data: vi.fn(() => Object.defineProperty({}, 'event', { get: getters.event })),
    }
    const payload = Object.defineProperties({}, {
      website: { get: getters.website }, name: { get: getters.name }, data: { get: getters.data },
    })
    try {
      const pending = track(() => payload)
      const guarded = approved() as object
      for (const field of ['website', 'name', 'data']) {
        Object.defineProperty(guarded, field, { get() { throw Error('must not re-read returned object') } })
      }
      await expect(pending).resolves.toBeUndefined()
      expect(fetch).toHaveBeenCalledExactlyOnceWith('/_skillhub-umami/api/send', {
        body: JSON.stringify({ type: 'event', payload: { website, name: intent.id, data: { event: 'click' } } }),
      })
      for (const getter of Object.values(getters)) expect(getter).toHaveBeenCalledTimes(1)
    } finally { adapter.stop() }
  })

  it('stop in the before-send/fetch microtask gap dispatches nothing and send resolves without unhandled rejection', async () => {
    const { adapter, fetch, approved } = platform()
    const unhandled = vi.fn()
    window.addEventListener('unhandledrejection', unhandled)
    try {
      const pending = adapter.send(intent)
      await Promise.resolve()
      expect(approved()).toEqual({ website, name: intent.id, data: { event: 'click' } })
      expect(fetch).not.toHaveBeenCalled()
      adapter.stop()
      await expect(pending).resolves.toBeUndefined()
      await new Promise(resolve => window.setTimeout(resolve, 0))
      expect(fetch).not.toHaveBeenCalled()
      expect(unhandled).not.toHaveBeenCalled()
    } finally {
      adapter.stop()
      window.removeEventListener('unhandledrejection', unhandled)
    }
  })

  it('stop in a later macrotask lets an already-started fetch complete but dispatches no more', async () => {
    let complete!: () => void
    const fetch = vi.fn(() => new Promise<void>(resolve => { complete = resolve }))
    const { adapter } = platform(fetch)
    try {
      const pending = adapter.send(intent)
      await new Promise(resolve => window.setTimeout(resolve, 0))
      expect(fetch).toHaveBeenCalledTimes(1)
      adapter.stop()
      complete()
      await expect(pending).resolves.toBeUndefined()
      await expect(adapter.send(intent)).resolves.toBeUndefined()
      expect(fetch).toHaveBeenCalledTimes(1)
    } finally { adapter.stop() }
  })

  it('serializes the exact allowlisted envelope and keeps toJSON non-enumerable', async () => {
    const { adapter, fetch, approved } = platform()
    try {
      await expect(adapter.send(intent)).resolves.toBeUndefined()
      expect(fetch).toHaveBeenCalledExactlyOnceWith('/_skillhub-umami/api/send', {
        body: `{"type":"event","payload":{"website":"${website}","name":"${intent.id}","data":{"event":"click"}}}`,
      })
      expect(Object.keys(approved() as object)).toEqual(['website', 'name', 'data'])
      expect(Object.getOwnPropertyDescriptor(approved(), 'toJSON')).toMatchObject({ enumerable: false, value: expect.any(Function) })
      const guarded = approved() as { toJSON: () => { data: object } }
      const first = guarded.toJSON(), second = guarded.toJSON()
      expect(first).toEqual({ website, name: intent.id, data: { event: 'click' } })
      expect(Object.getPrototypeOf(first)).toBe(Object.prototype)
      expect(first).not.toBe(second)
      expect(first.data).not.toBe(second.data)
      expect(Object.getOwnPropertyNames(first)).toEqual(['website', 'name', 'data'])
    } finally { adapter.stop() }
  })
})

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
  it.each(['bad', 'skill.bad-value.open', '', null, 42])('drops bad name %s', name => {
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
  it('sends two rapid ready intents before either track settles', async () => {
    const finish: Array<() => void> = []
    const track = vi.fn<(id: string, data: { event: string }) => Promise<void>>(() => new Promise<void>(resolve => finish.push(resolve)))
    const adapter = createUmamiAdapter({ window: { umami: { track } }, document, website })
    document.querySelector('script')!.dispatchEvent(new Event('load'))
    const first = adapter.send(intent), second = adapter.send({ ...intent, id: 'skill.command.copy' })
    await vi.waitFor(() => expect(track).toHaveBeenCalledTimes(2))
    expect(track.mock.calls.map(args => args[0])).toEqual([intent.id, 'skill.command.copy'])
    finish.forEach(resolve => resolve())
    await Promise.all([first, second])
    adapter.stop()
  })
  it('does not let a hung track block a later send', async () => {
    vi.useFakeTimers()
    const track = vi.fn().mockImplementationOnce(() => new Promise(() => {})).mockResolvedValue(undefined)
    const adapter = createUmamiAdapter({ window: { umami: { track } }, document, website, timeoutMs: 20 })
    document.querySelector('script')!.dispatchEvent(new Event('load'))
    const first = adapter.send(intent)
    await vi.advanceTimersByTimeAsync(0)
    await adapter.send({ ...intent, id: 'skill.command.copy' })
    expect(track).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(20)
    await expect(first).resolves.toBeUndefined()
    adapter.stop()
  })
  it('holds at most 20 intents only during loading and releases them in order', async () => {
    const track = vi.fn().mockResolvedValue(undefined)
    const adapter = createUmamiAdapter({ window: { umami: { track } }, document, website })
    // Invalid payloads must not consume pending capacity.
    await adapter.send({ id: 'bad', event: 'click' })
    const ids = Array.from({ length: 20 }, (_, i) => `skill.example.${String.fromCharCode(97 + i)}`)
    const pending = ids.map(id => adapter.send({ id, event: 'click' }))
    await expect(adapter.send(intent)).resolves.toBeUndefined()
    expect(track).not.toHaveBeenCalled()
    document.querySelector('script')!.dispatchEvent(new Event('load'))
    await Promise.all(pending)
    expect(track.mock.calls).toEqual(ids.map(id => [id, { event: 'click' }]))
    // The loading cap must not become a lifetime cap.
    await adapter.send(intent)
    expect(track).toHaveBeenCalledTimes(21)
    adapter.stop()
  })
  it.each(['error', 'timeout', 'stop'])('drops all pending intents on %s, including a late load', async mode => {
    vi.useFakeTimers()
    const track = vi.fn().mockResolvedValue(undefined)
    const adapter = createUmamiAdapter({ window: { umami: { track } }, document, website, timeoutMs: 20 })
    const script = document.querySelector('script')!
    const pending = Array.from({ length: 20 }, () => adapter.send(intent))
    if (mode === 'stop') adapter.stop()
    else if (mode === 'error') script.dispatchEvent(new Event('error'))
    else await vi.advanceTimersByTimeAsync(20)
    await Promise.all(pending)
    script.dispatchEvent(new Event('load'))
    await adapter.send(intent)
    expect(track).not.toHaveBeenCalled()
    adapter.stop()
  })
  it('stop denies payloads even with a previously ready tracker', async () => {
    const win = { umami: { track: vi.fn().mockResolvedValue(undefined) }, skillhubUmamiBeforeSend: undefined as unknown }
    const adapter = createUmamiAdapter({ window: win, document, website })
    document.querySelector('script')!.dispatchEvent(new Event('load'))
    adapter.stop()
    await adapter.send(intent)
    expect(win.umami.track).not.toHaveBeenCalled()
    const hook = win.skillhubUmamiBeforeSend as (type: string, payload: unknown) => unknown
    expect(hook('event', { website, name: intent.id, data: { event: 'click' } })).toBeNull()
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
