// Optional deployment example, NOT imported or started by SkillHub.
// Call only after consent/policy approval, with an explicit local callback.
const activeRoots = new WeakMap()
const rules = new Map([
  ['search.query.submit', 'submit'],
  ['skill.card.open', 'card'],
  ['suite.card.open', 'card'],
  ['skill.download.request', 'click'],
  ['skill.file.download', 'click'],
  ['skill.command.copy', 'click'],
  ['skill.prompt.copy', 'click'],
  ['skill.publish.request', 'click'],
  ['skill.publish.confirm', 'click'],
  ['skill.publish.cancel', 'click'],
  ['skill.delete.confirm', 'click'],
  ['skill.delete.cancel', 'click'],
])

export function observeSkillhubIntents({ root = document, onIntent } = {}) {
  if (typeof onIntent !== 'function') return () => {}
  if (activeRoots.has(root)) return activeRoots.get(root)
  const observe = event => {
    const target = event.target?.nodeType === 1 ? event.target : event.target?.parentElement
    const host = target?.closest('[data-analytics-id]')
    if (!host || !root.contains(host)) return
    const id = host.getAttribute('data-analytics-id')
    const rule = rules.get(id)
    if (!rule || host.closest(':disabled, [aria-disabled="true"], [data-disabled]')) return
    // Do not attribute a nested, unmarked interactive control to its containing card/form.
    const interactive = target.closest('button, a, input, select, textarea, [role="button"], [role="link"], [role="option"], [role="menuitem"]')
    if (event.type !== 'submit' && interactive && interactive !== host && host.contains(interactive)) return
    const keyboardCard = rule === 'card' && event.type === 'keydown'
      && host.tagName === 'DIV' && host.getAttribute('role') === 'link'
      && (event.key === 'Enter' || event.key === ' ')
      && !event.repeat && !event.isComposing && !event.altKey && !event.ctrlKey && !event.metaKey
    const accepted = (rule === 'submit' && event.type === 'submit' && host.tagName === 'FORM')
      || ((rule === 'click' || rule === 'card') && event.type === 'click')
      || keyboardCard
    if (!accepted) return
    // Only an allowlisted category and event kind leave this listener. No text, values,
    // resource/identity fields, hrefs, query strings, cookies, storage or network access.
    try { onIntent(Object.freeze({ id, event: event.type })) } catch { /* app stays usable */ }
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
