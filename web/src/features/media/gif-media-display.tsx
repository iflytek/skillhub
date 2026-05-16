import { useEffect, useRef, useState } from 'react'

export type MediaDisplayProps = {
  /** Resource URL — typically `/api/v1/media/{id}` returned by the backend. */
  src: string
  /** Static fallback shown until the GIF is in the viewport, or when the GIF fails to load. */
  coverSrc?: string | null
  /** Accessibility text. Required for screen readers; passes through to {@code img.alt}. */
  alt: string
  /**
   * When {@code true} (default), the GIF only starts loading once the element scrolls
   * into view. Skill cards in lists set this to keep above-the-fold paint quick.
   */
  lazy?: boolean
  className?: string
}

/**
 * Renders a structured GIF / image media asset with three behaviours from the design doc:
 *
 * <ul>
 *   <li>Cards default to the static cover; full GIF loads only on the detail page.</li>
 *   <li>Detail pages can request {@code lazy=false} to show the GIF immediately.</li>
 *   <li>Failed loads gracefully fall back to the cover (or an inline placeholder).</li>
 * </ul>
 */
export function GifMediaDisplay({ src, coverSrc, alt, lazy = true, className }: MediaDisplayProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const [shouldLoadGif, setShouldLoadGif] = useState(!lazy)
  const [errored, setErrored] = useState(false)

  useEffect(() => {
    if (shouldLoadGif) return
    if (!containerRef.current) return

    if (typeof IntersectionObserver === 'undefined') {
      setShouldLoadGif(true)
      return
    }

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setShouldLoadGif(true)
            observer.disconnect()
            break
          }
        }
      },
      { rootMargin: '200px' },
    )
    observer.observe(containerRef.current)
    return () => observer.disconnect()
  }, [shouldLoadGif])

  const showCover = !shouldLoadGif || errored

  return (
    <div ref={containerRef} className={className} data-testid="gif-media-display">
      {showCover ? (
        coverSrc ? (
          <img src={coverSrc} alt={alt} loading="lazy" />
        ) : (
          <div className="bg-muted text-muted-foreground" role="img" aria-label={alt}>
            <span className="sr-only">{alt}</span>
          </div>
        )
      ) : (
        <img
          src={src}
          alt={alt}
          loading="lazy"
          onError={() => setErrored(true)}
          data-testid="gif-media-img"
        />
      )}
    </div>
  )
}

export default GifMediaDisplay
