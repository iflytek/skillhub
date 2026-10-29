/** @vitest-environment jsdom */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent } from '@testing-library/react'
import { observeSkillhubIntents, DEFAULT_RULES, resolveRules } from '../docs/examples/analytics/observe/v1/skillhub-intents.mjs'

const stops: Array<() => void> = []
afterEach(() => { stops.splice(0).forEach(stop => stop()); document.body.replaceChildren(); vi.restoreAllMocks(); vi.unstubAllGlobals() })
function start(onIntent = vi.fn<(intent: { id: string; event: string }) => void>()) {
  const stop = observeSkillhubIntents({ root: document, onIntent })
  stops.push(stop)
  return { onIntent, stop }
}
function fixture(html: string) { document.body.innerHTML = html; return document.body.firstElementChild as HTMLElement }

describe('configurable finite intent rules', () => {
  it('exports frozen defaults and resolves new categories without mutating config', () => {
    expect(Object.isFrozen(DEFAULT_RULES)).toBe(true)
    expect(Object.keys(DEFAULT_RULES)).toHaveLength(12)
    const rules = Object.freeze({ 'skill.example.open': 'click' })
    expect(resolveRules(rules)).toEqual({ active: rules, skipped: [] })
  })

  it('reports invalid IDs, unknown rules, and non-string rules without throwing', () => {
    expect(resolveRules({ 'bad-id': 'click', 'skill.example.open': 'drop', 'skill.other.open': 42 })).toEqual({
      active: {}, skipped: [
        { id: 'bad-id', reason: 'invalid id shape' },
        { id: 'skill.example.open', reason: 'unknown rule type' },
        { id: 'skill.other.open', reason: 'non-string rule type' },
      ],
    })
    for (const invalid of [null, [], 42, 'click', undefined]) expect(() => resolveRules(invalid)).not.toThrow()
    expect(resolveRules(null).active).toEqual({})
  })

  it('invalid config is inert at runtime and does not run throwing config getters', () => {
    const button = fixture('<button data-analytics-id="skill.example.open">new</button>')
    const getter = vi.fn(() => { throw Error('invalid config getter') })
    const rules = { get 'skill.example.open'() { return getter() }, 'not-an-id': 'click', 'skill.other.open': 'drop' }
    const onIntent = vi.fn(), app = vi.fn()
    button.addEventListener('click', app)
    stops.push(observeSkillhubIntents({ root: document, rules, onIntent }))
    fireEvent.click(button)
    expect(getter).not.toHaveBeenCalled()
    expect(onIntent).not.toHaveBeenCalled()
    expect(app).toHaveBeenCalledTimes(1)
  })

  it('does not fall back to a marked ancestor when the nearest category is unconfigured', () => {
    const card = fixture('<div role="link" data-analytics-id="skill.card.open"><span data-analytics-id="skill.example.open">new</span></div>')
    const { onIntent } = start()
    fireEvent.click(card.firstElementChild!)
    expect(onIntent).not.toHaveBeenCalled()
  })

  it('replaces defaults, and keeps the first config until stop and re-init', () => {
    const host = fixture('<div><button data-analytics-id="skill.example.open">new</button><button data-analytics-id="skill.download.request">old</button></div>')
    const first = vi.fn(), second = vi.fn()
    const stop = observeSkillhubIntents({ root: document, rules: { 'skill.example.open': 'click' }, onIntent: first })
    stops.push(stop)
    expect(observeSkillhubIntents({ root: document, rules: DEFAULT_RULES, onIntent: second })).toBe(stop)
    fireEvent.click(host.children[0]); fireEvent.click(host.children[1])
    expect(first).toHaveBeenCalledExactlyOnceWith({ id: 'skill.example.open', event: 'click' })
    expect(second).not.toHaveBeenCalled()
    stop()
    stops.push(observeSkillhubIntents({ root: document, rules: { ...DEFAULT_RULES, 'skill.example.open': 'click' }, onIntent: second }))
    stop() // an old disposer must not remove the new installation
    fireEvent.click(host.children[0]); fireEvent.click(host.children[1])
    expect(second).toHaveBeenCalledTimes(2)
  })

  it.each(['form', 'select', 'textarea', 'input', 'input type="file"', 'input type="search"', 'input type="number"'])('does not use click for unsupported host %s', tag => {
    const host = fixture(`<${tag} data-analytics-id="skill.example.open"></${tag.split(' ')[0]}>`)
    const onIntent = vi.fn()
    stops.push(observeSkillhubIntents({ root: document, rules: { 'skill.example.open': 'click' }, onIntent }))
    fireEvent.click(host)
    expect(onIntent).not.toHaveBeenCalled()
  })

  it('skips submit on non-FORM and card keydown on non-DIV role=link', () => {
    const host = fixture('<div><button data-analytics-id="search.query.submit">wrong submit host</button><button data-analytics-id="skill.card.open">native</button><div data-analytics-id="suite.card.open">no role</div></div>')
    const { onIntent } = start()
    fireEvent.submit(host.children[0]); fireEvent.keyDown(host.children[1], { key: 'Enter' }); fireEvent.keyDown(host.children[2], { key: ' ' })
    expect(onIntent).not.toHaveBeenCalled()
  })

  it('counts at most once across nested roots, using nearest marked host only', () => {
    const host = fixture('<div data-analytics-id="skill.card.open" role="link"><button data-analytics-id="skill.download.request">download</button></div>')
    const onIntent = vi.fn()
    stops.push(observeSkillhubIntents({ root: document, onIntent }))
    stops.push(observeSkillhubIntents({ root: host, onIntent }))
    fireEvent.click(host.firstElementChild!)
    expect(onIntent).toHaveBeenCalledExactlyOnceWith({ id: 'skill.download.request', event: 'click' })
  })

  it.each(['repeat', 'isComposing', 'altKey', 'ctrlKey', 'metaKey', 'shiftKey'])('ignores card keydown with %s', flag => {
    const card = fixture('<div role="link" data-analytics-id="skill.card.open"></div>')
    const { onIntent } = start()
    fireEvent.keyDown(card, { key: 'Enter', [flag]: true })
    expect(onIntent).not.toHaveBeenCalled()
  })

  it('swallows rejected callbacks without unhandled rejection or changing app behavior', async () => {
    const button = fixture('<button data-analytics-id="skill.download.request">download</button>')
    const unhandled = vi.fn(), app = vi.fn()
    window.addEventListener('unhandledrejection', unhandled)
    button.addEventListener('click', app)
    start(vi.fn(() => Promise.reject(new Error('synthetic send failure'))))
    const event = new MouseEvent('click', { bubbles: true, cancelable: true })
    button.dispatchEvent(event)
    await new Promise(resolve => window.setTimeout(resolve, 0))
    window.removeEventListener('unhandledrejection', unhandled)
    expect(unhandled).not.toHaveBeenCalled()
    expect(event.defaultPrevented).toBe(false)
    expect(app).toHaveBeenCalledTimes(1)
  })
})

describe('optional deployment example (never automatically loaded)', () => {
  it('has no listener or network defaults without an explicit callback', () => {
    const listener = vi.spyOn(document, 'addEventListener')
    observeSkillhubIntents({ root: document })()
    expect(listener).not.toHaveBeenCalled()
  })

  it('reads a nested click as one allowlisted intent, not content or identity', () => {
    const host = fixture('<button data-analytics-id="skill.command.copy" data-user="private-person"><span>private-command</span></button>')
    const { onIntent } = start()
    fireEvent.click(host.firstElementChild!)
    expect(onIntent).toHaveBeenCalledExactlyOnceWith({ id: 'skill.command.copy', event: 'click' })
  })

  it('ignores unknown IDs, structural anchors and nested unmarked controls', () => {
    const host = fixture('<div role="link" data-analytics-id="skill.card.open"><button>nested action</button></div>')
    const { onIntent } = start()
    fireEvent.click(host.firstElementChild!)
    host.setAttribute('data-analytics-id', 'not.allowlisted.action')
    fireEvent.click(host)
    host.setAttribute('data-analytics-id', 'skill.detail.render')
    fireEvent.click(host)
    expect(onIntent).not.toHaveBeenCalled()
  })

  it('counts submit once, not typing, clearing, Enter keydown or submit-button click', () => {
    const form = fixture('<form data-analytics-id="search.query.submit"><input value="private-query"><button type="submit">search</button></form>')
    // Model the existing application submit handler without allowing jsdom navigation.
    const submit = vi.fn((event: Event) => event.preventDefault())
    form.addEventListener('submit', submit)
    const { onIntent } = start()
    fireEvent.change(form.querySelector('input')!, { target: { value: 'another-private-query' } })
    fireEvent.keyDown(form.querySelector('input')!, { key: 'Enter' })
    fireEvent.click(form.querySelector('button')!)
    expect(onIntent).toHaveBeenCalledExactlyOnceWith({ id: 'search.query.submit', event: 'submit' })
    expect(submit).toHaveBeenCalledTimes(1)
    fireEvent.submit(form)
    expect(onIntent).toHaveBeenCalledTimes(2)
  })

  it('covers the existing custom-card Enter/Space behavior without synthesizing events', () => {
    const card = fixture('<div role="link" tabindex="0" data-analytics-id="skill.card.open">open</div>')
    const navigate = vi.fn()
    card.addEventListener('click', navigate)
    card.addEventListener('keydown', event => {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); navigate() }
    })
    const { onIntent } = start()
    fireEvent.click(card)
    fireEvent.keyDown(card, { key: 'Enter' })
    fireEvent.keyDown(card, { key: ' ' })
    expect(navigate).toHaveBeenCalledTimes(3)
    expect(onIntent.mock.calls.map(([event]) => event)).toEqual([
      { id: 'skill.card.open', event: 'click' },
      { id: 'skill.card.open', event: 'keydown' },
      { id: 'skill.card.open', event: 'keydown' },
    ])
    fireEvent.keyDown(card, { key: 'Enter', repeat: true })
    fireEvent.keyDown(card, { key: 'Enter', ctrlKey: true })
    fireEvent.keyDown(card, { key: 'Enter', isComposing: true })
    fireEvent.keyDown(card, { key: 'Escape' })
    expect(onIntent).toHaveBeenCalledTimes(3)
  })

  it('does not count native-button keydown in addition to its generated click', () => {
    const button = fixture('<button data-analytics-id="skill.download.request">download</button>')
    const { onIntent } = start()
    fireEvent.keyDown(button, { key: 'Enter' })
    fireEvent.click(button)
    expect(onIntent).toHaveBeenCalledExactlyOnceWith({ id: 'skill.download.request', event: 'click' })
  })

  it.each(['disabled', 'aria-disabled="true"', 'data-disabled'])('ignores disabled/busy activation (%s)', disabled => {
    const button = fixture(`<button data-analytics-id="skill.publish.confirm" ${disabled}>continue</button>`)
    const { onIntent } = start()
    fireEvent.click(button)
    expect(onIntent).not.toHaveBeenCalled()
  })

  it('is idempotent, removable, and safe to initialize again after disposal', () => {
    const button = fixture('<button data-analytics-id="skill.publish.cancel">cancel</button>')
    const { onIntent, stop } = start()
    expect(observeSkillhubIntents({ root: document, onIntent })).toBe(stop)
    fireEvent.click(button)
    expect(onIntent).toHaveBeenCalledTimes(1)
    stop()
    fireEvent.click(button)
    expect(onIntent).toHaveBeenCalledTimes(1)
    const second = start()
    stop()
    fireEvent.click(button)
    expect(second.onIntent).toHaveBeenCalledTimes(1)
  })

  it('does not modify application propagation/default behavior even if the callback throws', () => {
    const button = fixture('<button data-analytics-id="skill.delete.cancel">cancel</button>')
    const handler = vi.fn()
    button.addEventListener('click', handler)
    start(vi.fn(() => { throw new Error('synthetic operator failure') }))
    const event = new MouseEvent('click', { bubbles: true, cancelable: true })
    button.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(false)
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('never reads cookies/storage/location or sends network requests', () => {
    const button = fixture('<button data-analytics-id="skill.prompt.copy">private prompt</button>')
    const fetch = vi.fn()
    const xhr = vi.fn()
    const beacon = vi.fn()
    vi.stubGlobal('fetch', fetch)
    vi.stubGlobal('XMLHttpRequest', xhr)
    vi.stubGlobal('navigator', { sendBeacon: beacon })
    const cookie = vi.spyOn(document, 'cookie', 'get')
    const cookieWrite = vi.spyOn(document, 'cookie', 'set')
    const local = vi.spyOn(window, 'localStorage', 'get')
    const session = vi.spyOn(window, 'sessionStorage', 'get')
    const storage = (['getItem', 'setItem', 'removeItem', 'clear', 'key'] as const).map(method => vi.spyOn(Storage.prototype, method))
    const { onIntent } = start()
    fireEvent.click(button)
    expect(onIntent).toHaveBeenCalledExactlyOnceWith({ id: 'skill.prompt.copy', event: 'click' })
    for (const spy of [fetch, xhr, beacon, cookie, cookieWrite, local, session, ...storage]) expect(spy).not.toHaveBeenCalled()
  })
})
