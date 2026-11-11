// @vitest-environment jsdom
import { cleanup, render, screen, act } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useInView } from './use-in-view'

afterEach(() => { cleanup(); vi.unstubAllGlobals() })
describe('landing section intersection registration', () => {
  it('observes a section enabled after initial render and disconnects a removed node', () => {
    const observe = vi.fn(), disconnect = vi.fn(), unobserve = vi.fn()
    let notify: IntersectionObserverCallback = () => {}
    vi.stubGlobal('IntersectionObserver', class {
      constructor(callback: IntersectionObserverCallback) { notify = callback }
      observe = observe
      unobserve = unobserve
      disconnect = disconnect
    })
    function Section({ enabled }: { enabled: boolean }) {
      const view = useInView()
      return enabled ? <div ref={view.ref} data-testid="section" className={view.inView ? 'in-view' : ''} /> : null
    }
    const result = render(<Section enabled={false} />)
    expect(observe).not.toHaveBeenCalled()
    result.rerender(<Section enabled />)
    const node = screen.getByTestId('section')
    expect(observe).toHaveBeenCalledWith(node)
    act(() => notify([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver))
    expect(node.classList.contains('in-view')).toBe(true)
    result.rerender(<Section enabled={false} />)
    expect(disconnect).toHaveBeenCalled()
  })
})
