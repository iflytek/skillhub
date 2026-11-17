import { useEffect, useState } from 'react'

// Only UI position/count metadata lives here; skill data stays in QueryClient.
const positions = new Map<string, { y: number; favoritesVisible: number }>()

export function saveMarketScroll(key: string, favoritesVisible: number) {
  positions.delete(key)
  positions.set(key, { y: window.scrollY, favoritesVisible })
  if (positions.size > 20) positions.delete(positions.keys().next().value!)
}

export function getMarketScroll(key: string) { return positions.get(key) }
export function clearMarketScroll(key: string) { positions.delete(key) }

export function useMarketScroll(key: string, ready: boolean) {
  const [restoredKey, setRestoredKey] = useState<string>()
  const restoring = positions.has(key) && restoredKey !== key
  useEffect(() => {
    const position = positions.get(key)
    if (!position || !ready || restoredKey === key) return
    let second = 0
    const first = window.requestAnimationFrame(() => {
      window.scrollTo({ top: position.y, behavior: 'auto' })
      second = window.requestAnimationFrame(() => {
        window.scrollTo({ top: position.y, behavior: 'auto' })
        setRestoredKey(key)
      })
    })
    return () => { window.cancelAnimationFrame(first); window.cancelAnimationFrame(second) }
  }, [key, ready, restoredKey])
  return restoring
}
