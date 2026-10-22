/** @vitest-environment jsdom */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent } from '@testing-library/react'
import { observeSkillhubIntents } from '../../../docs/examples/skillhub-intents.mjs'

const stops: Array<() => void> = []
afterEach(() => { stops.splice(0).forEach(stop => stop()); document.body.replaceChildren(); vi.restoreAllMocks(); vi.unstubAllGlobals() })
function start(onIntent = vi.fn<(intent: { id: string; event: string }) => void>()) {
  const stop = observeSkillhubIntents({ root: document, onIntent })
  stops.push(stop)
  return { onIntent, stop }
}
function fixture(html: string) { document.body.innerHTML = html; return document.body.firstElementChild as HTMLElement }

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
    const storage = vi.spyOn(Storage.prototype, 'getItem')
    const { onIntent } = start()
    fireEvent.click(button)
    expect(onIntent).toHaveBeenCalledExactlyOnceWith({ id: 'skill.prompt.copy', event: 'click' })
    for (const spy of [fetch, xhr, beacon, cookie, storage]) expect(spy).not.toHaveBeenCalled()
  })
})
