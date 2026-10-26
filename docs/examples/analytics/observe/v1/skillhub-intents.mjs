// Optional deployment example, NOT imported or started by SkillHub.
const activeRoots = new WeakMap()
const handledEvents = new WeakSet()
// Same grammar as web/scripts/analytics-manifest.mjs; manifest parity is tested.
const idShape = /^[a-z]+(?:\.[a-z]+){2,}$/
export const DEFAULT_RULES = Object.freeze({
  'search.query.submit': 'submit',
  'skill.card.open': 'card',
  'suite.card.open': 'card',
  'skill.download.request': 'click',
  'skill.file.download': 'click',
  'skill.command.copy': 'click',
  'skill.prompt.copy': 'click',
  'skill.publish.request': 'click',
  'skill.publish.confirm': 'click',
  'skill.publish.cancel': 'click',
  'skill.delete.confirm': 'click',
  'skill.delete.cancel': 'click',
})

// Pure validation; callers may inspect skipped without installing any listeners.
export function resolveRules(rules) {
  const active = Object.create(null)
  const skipped = []
  try {
    if (!rules || typeof rules !== 'object' || Array.isArray(rules)
      || ![Object.prototype, null].includes(Object.getPrototypeOf(rules))) {
      return { active, skipped: [{ id: null, reason: 'rules must be a plain object' }] }
    }
    for (const [id, descriptor] of Object.entries(Object.getOwnPropertyDescriptors(rules))) {
      const rule = descriptor.value // Do not run config getters.
      const reason = !idShape.test(id) ? 'invalid id shape'
        : typeof rule !== 'string' ? 'non-string rule type'
          : !['click', 'submit', 'card'].includes(rule) ? 'unknown rule type' : null
      if (reason) skipped.push({ id, reason })
      else active[id] = rule
    }
  } catch { skipped.push({ id: null, reason: 'unreadable rules object' }) }
  return { active, skipped }
}

export function observeSkillhubIntents({ root = document, rules = DEFAULT_RULES, onIntent } = {}) {
  if (activeRoots.has(root)) return activeRoots.get(root)
  if (typeof onIntent !== 'function') return () => {}
  const { active } = resolveRules(rules)
  const observe = event => {
    if (handledEvents.has(event)) return
    const target = event.target?.nodeType === 1 ? event.target : event.target?.parentElement
    const host = target?.closest('[data-analytics-id]')
    if (!host || !root.contains(host)) return
    const id = host.getAttribute('data-analytics-id')
    const rule = active[id]
    if (!rule || host.closest(':disabled, [aria-disabled="true"], [data-disabled]')) return
    const interactive = target.closest('button, a, input, select, textarea, [role="button"], [role="link"], [role="option"], [role="menuitem"]')
    if (event.type !== 'submit' && interactive && interactive !== host && host.contains(interactive)) return
    const clickHost = !host.matches('form, select, textarea, input:not([type="button"]):not([type="submit"]):not([type="reset"])')
    const cardHost = host.tagName === 'DIV' && host.getAttribute('role') === 'link'
    const keyboardCard = rule === 'card' && cardHost && event.type === 'keydown'
      && (event.key === 'Enter' || event.key === ' ')
      && !event.repeat && !event.isComposing && !event.altKey && !event.ctrlKey && !event.metaKey && !event.shiftKey
    const accepted = (rule === 'submit' && event.type === 'submit' && host.tagName === 'FORM')
      || (rule === 'click' && clickHost && event.type === 'click')
      || (rule === 'card' && cardHost && event.type === 'click') || keyboardCard
    if (!accepted) return
    handledEvents.add(event)
    // Never block app behavior; observe neither callback completion nor delivery success.
    try { Promise.resolve(onIntent(Object.freeze({ id, event: event.type }))).catch(() => {}) } catch { /* app stays usable */ }
  }
  for (const type of ['click', 'submit', 'keydown']) root.addEventListener(type, observe, true)
  const stop = () => {
    if (activeRoots.get(root) !== stop) return
    for (const type of ['click', 'submit', 'keydown']) root.removeEventListener(type, observe, true)
    activeRoots.delete(root)
  }
  activeRoots.set(root, stop)
  return stop
}
