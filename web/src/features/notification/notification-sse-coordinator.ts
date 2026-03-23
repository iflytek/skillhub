const SHARED_BROWSER_SSE_ENABLED = false
const INITIAL_RECONNECT_DELAY_MS = 1_000
const MAX_RECONNECT_DELAY_MS = 30_000

type NotificationListener = (event: MessageEvent) => void
type SourceEventListener = (event: Event) => void
type NotificationEventSource = {
  addEventListener: (type: string, listener: SourceEventListener) => void
  close: () => void
}
type EventSourceFactory = (url: string) => NotificationEventSource
type TimerApi = {
  setTimeout: typeof setTimeout
  clearTimeout: typeof clearTimeout
}

export type NotificationSseConnection = {
  addEventListener: (type: string, listener: NotificationListener) => void
  close: () => void
}

export function isSharedBrowserSseEnabled() {
  return SHARED_BROWSER_SSE_ENABLED
}

export function createNotificationSseConnection(
  url: string,
  eventSourceFactory: EventSourceFactory = (targetUrl) =>
    new EventSource(targetUrl, { withCredentials: true }),
  timerApi: TimerApi = { setTimeout, clearTimeout },
): NotificationSseConnection {
  return new ManagedNotificationSseConnection(url, eventSourceFactory, timerApi)
}

class ManagedNotificationSseConnection implements NotificationSseConnection {
  private readonly listeners = new Map<string, NotificationListener[]>()
  private currentSource: NotificationEventSource | null = null
  private reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private closed = false

  constructor(
    private readonly url: string,
    private readonly eventSourceFactory: EventSourceFactory,
    private readonly timerApi: TimerApi,
  ) {
    this.connect()
  }

  addEventListener(type: string, listener: NotificationListener) {
    const current = this.listeners.get(type) ?? []
    current.push(listener)
    this.listeners.set(type, current)
  }

  close() {
    this.closed = true
    if (this.reconnectTimer) {
      this.timerApi.clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.currentSource?.close()
    this.currentSource = null
  }

  private connect() {
    if (this.closed) {
      return
    }
    const source = this.eventSourceFactory(this.url)
    this.currentSource = source

    source.addEventListener('open', (event) => {
      this.reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
      this.emit('open', event as MessageEvent)
    })
    source.addEventListener('notification', (event) => {
      this.emit('notification', event as MessageEvent)
    })
    source.addEventListener('error', () => {
      source.close()
      if (this.closed || this.reconnectTimer) {
        return
      }
      const delay = this.reconnectDelayMs
      this.reconnectDelayMs = Math.min(this.reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS)
      this.reconnectTimer = this.timerApi.setTimeout(() => {
        this.reconnectTimer = null
        this.connect()
      }, delay)
    })
  }

  private emit(type: string, event: MessageEvent) {
    for (const listener of this.listeners.get(type) ?? []) {
      listener(event)
    }
  }
}
