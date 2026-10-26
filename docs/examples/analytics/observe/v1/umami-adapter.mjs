// Deployment-owned adapter for Umami v3.4.1 (MIT). No SDK in the product.
const idShape = /^[a-z][a-z0-9_]*\.[a-z][a-z0-9_]*\.[a-z][a-z0-9_]*$/
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
  let stopped = false, busy = false, finishLoad
  const script = doc.createElement('script')
  const ready = new Promise(resolve => { finishLoad = resolve })
  let loadTimer
  const loaded = ok => {
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
      if (stopped || busy || !sanitizeUmamiPayload('event', { website, name: id, data: { event } }, website)) return
      busy = true // At most one in-flight intent. No retry, backlog, or durable queue.
      let timer
      try {
        if (!await ready || stopped || typeof win.umami?.track !== 'function') return
        await Promise.race([
          Promise.resolve().then(() => { if (!stopped) return win.umami.track(id, { event }) }),
          new Promise(resolve => { timer = setTimeout(resolve, timeoutMs) }),
        ])
      } catch { /* Missing/blocked/offline analytics must never reject into the app. */ }
      finally { clearTimeout(timer); busy = false }
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
