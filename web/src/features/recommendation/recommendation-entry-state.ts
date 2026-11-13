import { useCallback, useContext, useMemo, useSyncExternalStore } from 'react'
import { useRouter } from '@tanstack/react-router'
import { SitePresentationPreviewContext } from '@/shared/hooks/use-site-presentation'
import { toRouterPath } from '@/shared/lib/base-path'

export type RecommendationEntryState = { topicId: string | null; search: string; label: string; page: number; carouselLeft: number; scrollY: number; panelScroll?: number }
const storageKey = 'skillhub:recommendation-entries:v1'
const limit = 20
const empty = { topicId: null, search: '', label: '', page: 0, carouselLeft: 0, scrollY: 0, panelScroll: 0 } satisfies RecommendationEntryState
function valid(value: unknown): value is RecommendationEntryState {
  if (!value || typeof value !== 'object') return false
  const v = value as Record<string, unknown>
  return (v.topicId === null || typeof v.topicId === 'string' && v.topicId.length <= 256)
    && typeof v.search === 'string' && v.search.length <= 2048 && typeof v.label === 'string' && v.label.length <= 256
    && (v.panelScroll === undefined || typeof v.panelScroll === 'number' && Number.isFinite(v.panelScroll) && v.panelScroll >= 0 && v.panelScroll <= 10_000_000)
    && ['page', 'carouselLeft', 'scrollY'].every(key => typeof v[key] === 'number' && Number.isFinite(v[key]) && v[key] >= 0 && v[key] <= 10_000_000)
}
function readEntries(): [string, RecommendationEntryState][] {
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem(storageKey) ?? '[]')
    return Array.isArray(value) ? value.filter((row): row is [string, RecommendationEntryState] => Array.isArray(row) && row.length === 2 && typeof row[0] === 'string' && valid(row[1])).slice(-limit) : []
  } catch { return [] }
}
export function recommendationEntryKey(location: { href: string; state: { __TSR_key?: string } }) {
  return location.state.__TSR_key ? `${location.state.__TSR_key}:${location.href}` : ''
}
export function useRecommendationEntry(enabled: boolean, entryPath?: '/' | '/featured') {
  const router = useRouter({ warn: false })
  const preview = useContext(SitePresentationPreviewContext)
  const subscribe = useCallback((notify: () => void) => {
    if (!router) return () => {}
    let live = true
    const update = () => { queueMicrotask(() => { if (live) notify() }) }
    const before = router.subscribe('onBeforeNavigate', update)
    const resolved = router.subscribe('onResolved', update)
    return () => { live = false; before(); resolved() }
  }, [router])
  const getKey = useCallback(() => {
    if (!enabled || preview || !router || !entryPath) return ''
    // Match the mounted public entry, rather than the mutable history destination.
    const current = router.state.location
    const resolved = router.state.resolvedLocation
    const location = toRouterPath(current.pathname) === entryPath ? current
      : resolved && toRouterPath(resolved.pathname) === entryPath ? resolved : undefined
    return location ? recommendationEntryKey(location) : ''
  }, [router, enabled, preview, entryPath])
  const key = useSyncExternalStore(subscribe, getKey, () => '')
  const saved = useMemo(() => key ? readEntries().find(([id]) => id === key)?.[1] : undefined, [key])
  const save = useCallback((value: RecommendationEntryState) => {
    if (!key || !valid(value)) return
    try { const entries = readEntries().filter(([id]) => id !== key); entries.push([key, value]); sessionStorage.setItem(storageKey, JSON.stringify(entries.slice(-limit))) } catch { /* Storage can be disabled; browsing remains available. */ }
  }, [key])
  return { key, saved, initial: saved ?? empty, save, router }
}
export type RecommendationEntry = ReturnType<typeof useRecommendationEntry>
