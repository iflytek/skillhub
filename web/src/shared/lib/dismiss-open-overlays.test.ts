/**
 * @vitest-environment jsdom
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { dismissOpenOverlays } from './dismiss-open-overlays'

describe('dismissOpenOverlays', () => {
  afterEach(() => {
    document.body.innerHTML = ''
    vi.restoreAllMocks()
  })

  it('does nothing when no open overlay is present', () => {
    const dispatch = vi.spyOn(document, 'dispatchEvent')
    dismissOpenOverlays()
    expect(dispatch).not.toHaveBeenCalled()
  })

  it('dispatches Escape when an open dialog is present', () => {
    const dialog = document.createElement('div')
    dialog.setAttribute('role', 'dialog')
    dialog.setAttribute('data-state', 'open')
    document.body.appendChild(dialog)

    const dispatch = vi.spyOn(document, 'dispatchEvent')
    dismissOpenOverlays()

    expect(dispatch).toHaveBeenCalledOnce()
    const event = dispatch.mock.calls[0]?.[0] as KeyboardEvent
    expect(event).toBeInstanceOf(KeyboardEvent)
    expect(event.key).toBe('Escape')
  })
})
