const OPEN_OVERLAY_SELECTOR = [
  '[data-radix-select-content][data-state="open"]',
  '[data-radix-dropdown-menu-content][data-state="open"]',
  '[data-radix-dialog-content][data-state="open"]',
  '[role="listbox"][data-state="open"]',
  '[role="menu"][data-state="open"]',
  '[role="dialog"][data-state="open"]',
].join(',')

/**
 * Dismiss open Radix overlays before a route unmounts their triggers.
 *
 * Call only on pathname changes (not search debounce) to avoid closing UI while
 * typing on /search.
 */
export function dismissOpenOverlays(): void {
  if (typeof document === 'undefined') {
    return
  }

  if (!document.querySelector(OPEN_OVERLAY_SELECTOR)) {
    return
  }

  if (document.activeElement instanceof HTMLElement) {
    document.activeElement.blur()
  }

  document.dispatchEvent(
    new KeyboardEvent('keydown', {
      key: 'Escape',
      code: 'Escape',
      bubbles: true,
      cancelable: true,
    }),
  )
}
