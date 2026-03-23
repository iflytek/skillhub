import { describe, expect, it, vi } from 'vitest'
import { createNotificationSseConnection } from './notification-sse-coordinator'

class FakeEventSource {
  listeners = new Map<string, Array<(event: MessageEvent) => void>>()
  closed = false

  addEventListener(type: string, listener: (event: MessageEvent) => void) {
    const current = this.listeners.get(type) ?? []
    current.push(listener)
    this.listeners.set(type, current)
  }

  close() {
    this.closed = true
  }

  emit(type: string) {
    for (const listener of this.listeners.get(type) ?? []) {
      listener(new MessageEvent(type))
    }
  }
}

describe('createNotificationSseConnection', () => {
  it('backs off reconnect attempts after repeated errors', () => {
    vi.useFakeTimers()
    const sources: FakeEventSource[] = []
    const connection = createNotificationSseConnection(
      '/api/web/notifications/sse',
      () => {
        const source = new FakeEventSource()
        sources.push(source)
        return source
      },
      { setTimeout, clearTimeout },
    )

    expect(sources).toHaveLength(1)
    sources[0].emit('error')
    expect(sources[0].closed).toBe(true)
    expect(sources).toHaveLength(1)

    vi.advanceTimersByTime(999)
    expect(sources).toHaveLength(1)

    vi.advanceTimersByTime(1)
    expect(sources).toHaveLength(2)

    sources[1].emit('error')
    vi.advanceTimersByTime(1_999)
    expect(sources).toHaveLength(2)

    vi.advanceTimersByTime(1)
    expect(sources).toHaveLength(3)

    connection.close()
    vi.useRealTimers()
  })

  it('resets reconnect delay after a successful open event', () => {
    vi.useFakeTimers()
    const sources: FakeEventSource[] = []
    createNotificationSseConnection(
      '/api/web/notifications/sse',
      () => {
        const source = new FakeEventSource()
        sources.push(source)
        return source
      },
      { setTimeout, clearTimeout },
    )

    sources[0].emit('error')
    vi.advanceTimersByTime(1_000)
    expect(sources).toHaveLength(2)

    sources[1].emit('open')
    sources[1].emit('error')
    vi.advanceTimersByTime(1_000)
    expect(sources).toHaveLength(3)

    vi.useRealTimers()
  })
})
