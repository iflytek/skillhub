export function limitPreviewItems<T>(items: T[], limit: number): {
  items: T[]
  hasMore: boolean
  remainingCount: number
} {
  const visibleItems = items.slice(0, limit)
  const remainingCount = Math.max(items.length - visibleItems.length, 0)

  return {
    items: visibleItems,
    hasMore: remainingCount > 0,
    remainingCount,
  }
}
