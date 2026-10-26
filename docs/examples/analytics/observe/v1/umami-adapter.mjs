// Deployment-owned adapter for Umami v3.4.1 (MIT). No SDK in the product.
// Same grammar as web/scripts/analytics-manifest.mjs; manifest parity is tested.
const idShape = /^[a-z]+(?:\.[a-z]+){2,}$/
const websiteShape = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const events = ['click', 'submit', 'keydown']

// Umami's before-send hook returns the inner payload, not the { type, payload } envelope.
export function sanitizeUmamiPayload(type, payload, website) {
  if (type !== 'event' || !websiteShape.test(website) || payload?.website !== website
    || typeof payload?.name !== 'string' || !idShape.test(payload.name)
    || payload.name.length > 50 || !events.includes(payload.data?.event)) return null
  return { website, name: payload.name, data: { event: payload.data.event } }
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
  win.skillhubUmamiBeforeSend = (type, payload) => stopped ? null : sanitizeUmamiPayload(type, payload, website)
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
      // Keep a denying hook: a tracker already loaded cannot bypass consent withdrawal.
      // Requests already handed to fetch cannot be recalled.
    },
  }
}
