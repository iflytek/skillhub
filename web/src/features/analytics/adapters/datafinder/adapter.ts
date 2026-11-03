import type { AnalyticsAdapter, AnalyticsContext } from '../../types'
import { validateDatafinderConfig } from './config'

export const BROWSER_ID_KEY = 'skillhub.analytics.datafinder.browser-id'
const uuidShape = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
let pageId: string | undefined
// randomUUID exists only in secure contexts (HTTPS or localhost) and newer browsers.
// Plain-HTTP intranet deployments still have getRandomValues; build the same v4 UUID.
function randomId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
function anonymousId(): string {
  if (pageId) return pageId
  let stored: string | null
  try { stored = localStorage.getItem(BROWSER_ID_KEY) } catch { return pageId ??= randomId() }
  if (stored && uuidShape.test(stored)) return stored
  const id = randomId()
  try { localStorage.setItem(BROWSER_ID_KEY, id) }
  catch { return pageId ??= id }
  return id
}

// No remote SDK, batching, retry queue or event persistence. Each intent is bounded.
export function createDatafinderAdapter(config: Record<string, unknown>, prefix: string, context: AnalyticsContext = {}): AnalyticsAdapter {
  if (!validateDatafinderConfig(config)) return { send: async () => {}, stop() {} }
  const appId = config.appId as number, product = (config.product ?? 'skillhub') as string
  let stopped = false
  return {
    async send(intent) {
      let timer: ReturnType<typeof setTimeout> | undefined
      try {
        if (stopped) return
        const { id, event } = intent
        if (typeof id !== 'string' || !/^[a-z]+(?:\.[a-z]+){2,}$/.test(id) || !['click', 'submit', 'keydown'].includes(event)) return
        const module = context.currentPageType?.() ?? 'other'
        if (stopped) return
        const user = anonymousId()
        const body = JSON.stringify([{ user: { user_unique_id: user, web_id: user }, header: { app_id: appId, custom: { product, module } }, events: [{ event: id, params: JSON.stringify({ event }), local_time_ms: Date.now() }] }])
        const abort = new AbortController()
        // A context getter may synchronously stop collection. No async gap before fetch.
        if (stopped) return
        await Promise.race([
          fetch(`${prefix}/list`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body, keepalive: true, credentials: 'omit', referrerPolicy: 'no-referrer', signal: abort.signal }).then(async response => {
            if (!response.ok) {
              console.error('[analytics] adapter: collector did not accept event')
              return
            }
            const reply: unknown = await response.json()
            // HTTP 200 alone is not acceptance. Never log the collector's response.
            if (!reply || typeof reply !== 'object' || !('e' in reply) || reply.e !== 0 || !('sc' in reply) || reply.sc !== 1) {
              console.error('[analytics] adapter: collector did not accept event')
            }
          }),
          new Promise<void>(resolve => { timer = setTimeout(() => { abort.abort(); resolve() }, 3000) }),
        ])
      } catch { /* Analytics errors must never reject into the application. */ }
      finally { clearTimeout(timer) }
    },
    stop() { stopped = true },
  }
}
