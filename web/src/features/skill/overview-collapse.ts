export const OVERVIEW_COLLAPSE_DESKTOP_MAX_HEIGHT = 720
export const OVERVIEW_COLLAPSE_MOBILE_VIEWPORT_RATIO = 0.6
const OVERVIEW_COLLAPSE_MOBILE_BREAKPOINT = 768

export function getOverviewCollapseMaxHeight(viewportWidth: number, viewportHeight: number) {
  if (viewportWidth < OVERVIEW_COLLAPSE_MOBILE_BREAKPOINT) {
    return Math.round(viewportHeight * OVERVIEW_COLLAPSE_MOBILE_VIEWPORT_RATIO)
  }

  return OVERVIEW_COLLAPSE_DESKTOP_MAX_HEIGHT
}

export function shouldCollapseOverview(contentHeight: number, viewportWidth: number, viewportHeight: number) {
  return contentHeight > getOverviewCollapseMaxHeight(viewportWidth, viewportHeight)
}
