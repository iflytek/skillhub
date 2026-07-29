import { afterEach, describe, expect, it, vi } from 'vitest'
import { navigateAfterOverlays } from './navigate-after-overlays'

describe('navigateAfterOverlays', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('navigates after two animation frames', () => {
    const navigate = vi.fn()
    const frames: FrameRequestCallback[] = []
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => {
      frames.push(cb)
      return frames.length
    })

    navigateAfterOverlays(navigate)

    expect(navigate).not.toHaveBeenCalled()
    expect(frames).toHaveLength(1)
    frames[0]?.(0)
    expect(frames).toHaveLength(2)
    expect(navigate).not.toHaveBeenCalled()
    frames[1]?.(0)
    expect(navigate).toHaveBeenCalledOnce()
  })
})
