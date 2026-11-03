// Optional adapter for Umami v3.4.1 (MIT). SDK loads only on explicit creation.
// Same grammar as web/scripts/analytics-manifest.mjs; manifest parity is tested.
const idShape = /^[a-z]+(?:\.[a-z]+){2,}$/
const websiteShape = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const events = ['click', 'submit', 'keydown']

// Umami's before-send hook returns the inner payload, not the { type, payload } envelope.
export function sanitizeUmamiPayload(type, payload, website) {
  let payloadWebsite, name, event
  try {
    payloadWebsite = payload?.website
    name = payload?.name
    event = payload?.data?.event
  } catch { return null }
  if (type !== 'event' || !websiteShape.test(website) || payloadWebsite !== website
    || typeof name !== 'string' || !idShape.test(name) || name.length > 50
    || typeof event !== 'string' || !events.includes(event)) return null
  return { website: payloadWebsite, name, data: { event } }
}

export function createUmamiAdapter({ window: win = window, document: doc = document,
  website, prefix = '/_skillhub-umami', timeoutMs = 3000 } = {}) {
  if (!websiteShape.test(website)) return { send: async () => {}, stop() {} }
  let stopped = false, loadState = 'loading', pending = 0, finishLoad
  const script = doc.createElement('script')
  const ready = new Promise(resolve => { finishLoad = resolve })
  let loadTimer
  const loaded = ok => {
    if (loadState !== 'loading') return // Failure/timeout cannot be revived by a late load.
    loadState = ok ? 'ready' : 'failed'
    clearTimeout(loadTimer)
    script.onload = script.onerror = null
    finishLoad(ok)
  }
  win.skillhubUmamiBeforeSend = (type, payload) => {
    if (stopped) return null
    const sanitized = sanitizeUmamiPayload(type, payload, website)
    if (!sanitized) return null
    const { website: validatedWebsite, name, data: { event } } = sanitized
    // Verified against pinned v3.4.1: serialization is synchronous at fetch dispatch,
    // inside the SDK's try/catch, after awaiting this hook. Re-verify on SDK upgrades.
    Object.defineProperty(sanitized, 'toJSON', {
      enumerable: false,
      value() {
        if (stopped) throw new Error('Analytics stopped before dispatch')
        return { website: validatedWebsite, name, data: { event } }
      },
    })
    return sanitized
  }
  script.src = `${prefix}/script.js`
  script.async = true
  script.setAttribute('data-website-id', website)
  script.setAttribute('data-auto-track', 'false')
  script.setAttribute('data-host-url', prefix)
  script.setAttribute('data-before-send', 'skillhubUmamiBeforeSend')
  script.onload = () => loaded(true)
  script.onerror = () => loaded(false)
  loadTimer = setTimeout(() => loaded(false), timeoutMs)
  doc.head.append(script)

  return {
    async send({ id, event } = {}) {
      if (stopped || loadState === 'failed'
        || !sanitizeUmamiPayload('event', { website, name: id, data: { event } }, website)) return
      if (loadState === 'loading') {
        if (pending >= 20) return // Bounded, memory-only wait for the script; no retry.
        pending++
        await ready
        pending--
      }
      let timer
      try {
        if (stopped || loadState !== 'ready' || typeof win.umami?.track !== 'function') return
        // Each ready intent is independent, even if another SDK fetch never settles.
        await Promise.race([
          Promise.resolve().then(() => { if (!stopped) return win.umami.track(id, { event }) }),
          new Promise(resolve => { timer = setTimeout(resolve, timeoutMs) }),
        ])
      } catch { /* Missing/blocked/offline analytics must never reject into the app. */ }
      finally { clearTimeout(timer) }
    },
    stop() {
      stopped = true
      loaded(false)
      script.remove()
      // Keep the denying hook and dispatch guard: no new request after stop returns.
      // Only a request whose fetch already started cannot be recalled.
    },
  }
}
