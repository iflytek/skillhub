const SHARED_BROWSER_SSE_ENABLED = false

export type NotificationSseConnection = {
  addEventListener: EventSource['addEventListener']
  close: EventSource['close']
}

export function isSharedBrowserSseEnabled() {
  return SHARED_BROWSER_SSE_ENABLED
}

export function createNotificationSseConnection(url: string): NotificationSseConnection {
  // Hidden extension seam: keep current per-page EventSource behavior until shared-browser
  // coordination is intentionally enabled in a future iteration.
  return new EventSource(url, { withCredentials: true })
}
