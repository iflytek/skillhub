import { describe, expect, it } from 'vitest'
import { getPermissionPanelPosition } from './token-permission-position'

describe('permission panel viewport containment', () => {
  it.each([
    { left: 0, top: 0, width: 1280, height: 800 },
    { left: 0, top: 0, width: 820, height: 360 },
    { left: 0, top: 0, width: 390, height: 360 },
    { left: 35, top: 120, width: 320, height: 280 },
  ])('contains the panel in $width × $height including shifted visual viewports', (viewport) => {
    for (const anchor of [{ right: 20, top: -400, bottom: -370 }, { right: 850, top: 760, bottom: 790 }]) {
      const panel = getPermissionPanelPosition(anchor, viewport, 700)
      expect(panel.left).toBeGreaterThanOrEqual(viewport.left)
      expect(panel.top).toBeGreaterThanOrEqual(viewport.top)
      expect(panel.left + panel.width).toBeLessThanOrEqual(viewport.left + viewport.width)
      expect(panel.top + panel.height).toBeLessThanOrEqual(viewport.top + viewport.height)
    }
  })

  it('caps a desktop panel at the approved compact dimensions even with long content', () => {
    const panel = getPermissionPanelPosition({ right: 1100, top: 100, bottom: 128 },
      { left: 0, top: 0, width: 1280, height: 900 }, 700)
    expect(panel.width).toBe(320)
    expect(panel.height).toBe(380)
  })

  it('keeps a short mobile panel at the visible bottom after the keyboard opens', () => {
    const panel = getPermissionPanelPosition({ right: 350, top: 200, bottom: 230 },
      { left: 0, top: 100, width: 390, height: 300 }, 120)
    expect(panel.top).toBe(280)
    expect(panel.width).toBe(390)
    expect(panel.mobile).toBe(true)
  })
})
