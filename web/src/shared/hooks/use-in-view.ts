import { useCallback, useEffect, useRef, useState } from 'react'

export function useInView(options?: IntersectionObserverInit) {
  const [element, setElement] = useState<HTMLElement | null>(null)
  const ref = useCallback((node: HTMLElement | null) => setElement(node), [])
  const [inView, setInView] = useState(false)
  const optionsRef = useRef(options)

  useEffect(() => {
    optionsRef.current = options
  }, [options])

  useEffect(() => {
    const el = element
    if (!el) return

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setInView(true)
          observer.unobserve(el)
        }
      },
      { threshold: 0.15, ...optionsRef.current },
    )

    observer.observe(el)
    return () => observer.disconnect()
  }, [element])

  return { ref, inView }
}
