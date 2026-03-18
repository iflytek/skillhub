import { describe, expect, it } from 'vitest'
import {
  OVERVIEW_COLLAPSE_DESKTOP_MAX_HEIGHT,
  OVERVIEW_COLLAPSE_MOBILE_VIEWPORT_RATIO,
  getOverviewCollapseMaxHeight,
  shouldCollapseOverview,
} from './overview-collapse'

describe('overview collapse helpers', () => {
  it('uses a fixed max height on desktop viewports', () => {
    expect(getOverviewCollapseMaxHeight(1280, 900)).toBe(OVERVIEW_COLLAPSE_DESKTOP_MAX_HEIGHT)
  })

  it('uses a viewport-based max height on mobile viewports', () => {
    expect(getOverviewCollapseMaxHeight(375, 900)).toBe(Math.round(900 * OVERVIEW_COLLAPSE_MOBILE_VIEWPORT_RATIO))
  })

  it('marks overview content as collapsible only when content exceeds the threshold', () => {
    const maxHeight = getOverviewCollapseMaxHeight(1280, 900)

    expect(shouldCollapseOverview(maxHeight, 1280, 900)).toBe(false)
    expect(shouldCollapseOverview(maxHeight + 1, 1280, 900)).toBe(true)
  })
})
