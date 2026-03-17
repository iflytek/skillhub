export function limitPreviewItems<T>(items: T[] | null | undefined | unknown, limit: number): {
  items: T[]
  hasMore: boolean
  remainingCount: number
} {
  const normalizedItems: T[] = Array.isArray(items) ? (items as T[]) : []
  const visibleItems = normalizedItems.slice(0, limit)
  const remainingCount = Math.max(normalizedItems.length - visibleItems.length, 0)

  return {
    items: visibleItems,
    hasMore: remainingCount > 0,
    remainingCount,
  }
}
