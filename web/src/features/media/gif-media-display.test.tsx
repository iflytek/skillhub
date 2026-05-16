/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { GifMediaDisplay } from './gif-media-display'

/**
 * Tests for {@link GifMediaDisplay}: lazy-loading via IntersectionObserver, fallback
 * to cover on error, and accessible alt text. Lazy mode hides the GIF until
 * intersection; eager mode shows it immediately.
 */
describe('GifMediaDisplay', () => {
  let observerCallback: IntersectionObserverCallback = () => {}

  beforeEach(() => {
    class MockIntersectionObserver implements IntersectionObserver {
      readonly root: Element | Document | null = null
      readonly rootMargin: string = ''
      readonly thresholds: ReadonlyArray<number> = []
      constructor(callback: IntersectionObserverCallback) {
        observerCallback = callback
      }
      disconnect(): void {}
      observe(_target: Element): void {}
      takeRecords(): IntersectionObserverEntry[] {
        return []
      }
      unobserve(_target: Element): void {}
    }
    vi.stubGlobal('IntersectionObserver', MockIntersectionObserver as unknown as typeof IntersectionObserver)
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('renders cover by default in lazy mode', () => {
    render(<GifMediaDisplay src="/api/v1/media/2" coverSrc="/cover.png" alt="演示" />)
    expect(screen.getByAltText('演示').getAttribute('src')).toBe('/cover.png')
    expect(screen.queryByTestId('gif-media-img')).toBeNull()
  })

  it('switches to GIF when intersection observer fires', () => {
    render(<GifMediaDisplay src="/api/v1/media/2" coverSrc="/cover.png" alt="演示" />)
    act(() => {
      observerCallback(
        [{ isIntersecting: true } as unknown as IntersectionObserverEntry],
        {} as IntersectionObserver,
      )
    })
    const img = screen.getByTestId('gif-media-img') as HTMLImageElement
    expect(img.src).toContain('/api/v1/media/2')
  })

  it('falls back to cover when GIF errors', () => {
    render(<GifMediaDisplay src="/api/v1/media/2" coverSrc="/cover.png" alt="演示" lazy={false} />)
    const img = screen.getByTestId('gif-media-img') as HTMLImageElement
    fireEvent.error(img)
    expect(screen.getByAltText('演示').getAttribute('src')).toBe('/cover.png')
  })

  it('renders a placeholder with role=img when no cover provided', () => {
    render(<GifMediaDisplay src="/api/v1/media/2" alt="演示" />)
    expect(screen.getByRole('img', { name: '演示' })).not.toBeNull()
  })

  it('shows GIF immediately when lazy is false', () => {
    render(<GifMediaDisplay src="/api/v1/media/2" coverSrc="/cover.png" alt="演示" lazy={false} />)
    expect(screen.getByTestId('gif-media-img')).not.toBeNull()
  })
})
