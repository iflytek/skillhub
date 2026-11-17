// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearMarketScroll, saveMarketScroll, useMarketScroll } from './market-scroll'

afterEach(() => { cleanup(); clearMarketScroll('viewer-a:filters'); vi.restoreAllMocks(); vi.unstubAllGlobals() })

function frames() {
  const queued: FrameRequestCallback[] = []
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => { queued.push(callback); return queued.length })
  vi.stubGlobal('cancelAnimationFrame', vi.fn())
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  return () => act(() => { const callback = queued.shift(); callback?.(0) })
}

describe('market detail-return position', () => {
  it('waits for cached batches to render and restores the matching position', () => {
    const nextFrame = frames()
    vi.spyOn(window, 'scrollY', 'get').mockReturnValue(920)
    saveMarketScroll('viewer-a:filters', 24)
    const hook = renderHook(({ ready }) => useMarketScroll('viewer-a:filters', ready), { initialProps: { ready: false } })
    expect(hook.result.current).toBe(true)
    expect(window.scrollTo).not.toHaveBeenCalled()
    hook.rerender({ ready: true })
    nextFrame(); nextFrame()
    expect(window.scrollTo).toHaveBeenLastCalledWith({ top: 920, behavior: 'auto' })
    expect(hook.result.current).toBe(false)
  })

  it('does not reuse another viewer/filter position and clears explicit filter resets', () => {
    frames()
    saveMarketScroll('viewer-a:filters', 24)
    const other = renderHook(() => useMarketScroll('viewer-b:filters', true))
    expect(other.result.current).toBe(false)
    expect(window.scrollTo).not.toHaveBeenCalled()
    clearMarketScroll('viewer-a:filters')
    const reset = renderHook(() => useMarketScroll('viewer-a:filters', true))
    expect(reset.result.current).toBe(false)
  })
})
