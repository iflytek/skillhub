const SHARED_BROWSER_SSE_ENABLED = false

export type NotificationSseConnection = {
  addEventListener: (type: string, listener: (event: MessageEvent) => void) => void
  close: () => void
}

export function isSharedBrowserSseEnabled() {
  return SHARED_BROWSER_SSE_ENABLED
}

export function createNotificationSseConnection(url: string): NotificationSseConnection {
  // Hidden extension seam: keep current per-page EventSource behavior until shared-browser
  // coordination is intentionally enabled in a future iteration.
  return new EventSource(url, { withCredentials: true })
}
