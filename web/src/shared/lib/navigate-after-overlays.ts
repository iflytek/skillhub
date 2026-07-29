import { dismissOpenOverlays } from './dismiss-open-overlays'

/**
 * Close overlays, then navigate on the next paint frames so Dialog/Select
 * teardown does not race React 19 route unmount (removeChild / insertBefore).
 */
export function navigateAfterOverlays(navigate: () => void): void {
  dismissOpenOverlays()

  const raf = globalThis.requestAnimationFrame
  if (typeof raf !== 'function') {
    navigate()
    return
  }

  raf(() => {
    raf(() => {
      navigate()
    })
  })
}
