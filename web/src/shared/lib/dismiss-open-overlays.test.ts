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
    const dispatch = vi.spyOn(document.body, 'dispatchEvent')
    dismissOpenOverlays()
    expect(dispatch).not.toHaveBeenCalled()
  })

  it('pointer-dismisses dialog overlay when an open dialog is present', () => {
    const overlay = document.createElement('div')
    overlay.setAttribute('data-radix-dialog-overlay', '')
    document.body.appendChild(overlay)

    const dialog = document.createElement('div')
    dialog.setAttribute('role', 'dialog')
    dialog.setAttribute('data-state', 'open')
    document.body.appendChild(dialog)

    const overlayDispatch = vi.spyOn(overlay, 'dispatchEvent')
    const bodyDispatch = vi.spyOn(document.body, 'dispatchEvent')

    dismissOpenOverlays()

    expect(overlayDispatch.mock.calls.some(([event]) => (event as Event).type === 'pointerdown')).toBe(true)
    expect(bodyDispatch.mock.calls.some(([event]) => (event as Event).type === 'pointerdown')).toBe(true)
  })
})
