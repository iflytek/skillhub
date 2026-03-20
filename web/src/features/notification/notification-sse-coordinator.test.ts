import { describe, expect, it, vi } from 'vitest'
import { createNotificationSseConnection, isSharedBrowserSseEnabled } from './notification-sse-coordinator'

describe('notification-sse-coordinator', () => {
  it('keeps shared browser coordination disabled by default', () => {
    expect(isSharedBrowserSseEnabled()).toBe(false)
  })

  it('creates a direct EventSource connection in the default mode', () => {
    const close = vi.fn()
    const addEventListener = vi.fn()
    const eventSourceMock = vi.fn(() => ({ close, addEventListener }))

    vi.stubGlobal('EventSource', eventSourceMock)

    const connection = createNotificationSseConnection('/api/web/notifications/sse')

    expect(eventSourceMock).toHaveBeenCalledWith('/api/web/notifications/sse', { withCredentials: true })
    expect(connection.close).toBe(close)
    expect(connection.addEventListener).toBe(addEventListener)

    vi.unstubAllGlobals()
  })
})
