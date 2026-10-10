const OPEN_OVERLAY_SELECTOR = [
  '[data-radix-select-content][data-state="open"]',
  '[data-radix-dropdown-menu-content][data-state="open"]',
  '[data-radix-dialog-content][data-state="open"]',
  '[role="listbox"][data-state="open"]',
  '[role="menu"][data-state="open"]',
  '[role="dialog"][data-state="open"]',
].join(',')

function dispatchPointer(target: EventTarget, type: 'pointerdown' | 'pointerup') {
  target.dispatchEvent(
    new PointerEvent(type, {
      bubbles: true,
      cancelable: true,
      pointerType: 'mouse',
      button: 0,
      buttons: type === 'pointerdown' ? 1 : 0,
      clientX: 0,
      clientY: 0,
    }),
  )
}

/**
 * Dismiss open Radix overlays before a route unmounts their triggers.
 *
 * Prefer pointer/outside-dismiss (Radix listens for these) over synthetic Escape,
 * which is often ignored because `isTrusted === false`.
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

  // Dialog: overlay pointerdown is the trusted dismiss path in Radix.
  document.querySelectorAll('[data-radix-dialog-overlay]').forEach((overlay) => {
    dispatchPointer(overlay, 'pointerdown')
    dispatchPointer(overlay, 'pointerup')
  })

  // Select / DropdownMenu: outside pointerdown on body closes open content.
  dispatchPointer(document.body, 'pointerdown')
  dispatchPointer(document.body, 'pointerup')
}
