interface Bounds {
  left: number
  top: number
  width: number
  height: number
}

/** Positions against the visible viewport, including zoom and the soft keyboard. */
export function getPermissionPanelPosition(
  anchor: { right: number; top: number; bottom: number },
  viewport: Bounds,
  measuredHeight?: number,
) {
  const mobile = viewport.width < 640
  const padding = mobile ? 0 : 16
  const width = Math.max(0, Math.min(mobile ? viewport.width : 320, viewport.width - padding * 2))
  const bottom = viewport.top + viewport.height
  const below = Math.max(0, bottom - Math.max(viewport.top, anchor.bottom) - 24)
  const above = Math.max(0, Math.min(bottom, anchor.top) - viewport.top - 24)
  const centered = Math.max(below, above) < 280
  const height = Math.max(0, Math.min(
    380,
    mobile ? viewport.height * 0.85 : centered ? viewport.height - 32 : Math.max(below, above),
  ))
  const actualHeight = Math.min(measuredHeight ?? height, height)
  const left = mobile ? viewport.left : Math.max(viewport.left + padding,
    Math.min(anchor.right - width, viewport.left + viewport.width - width - padding))
  const preferredTop = mobile ? bottom - actualHeight : centered ? viewport.top + padding
    : below >= above ? anchor.bottom + 8 : anchor.top - actualHeight - 8
  const top = Math.max(viewport.top + padding,
    Math.min(preferredTop, bottom - actualHeight - padding))
  return { left: Math.round(left), top: Math.round(top), width: Math.round(width), height: Math.floor(height), mobile }
}
