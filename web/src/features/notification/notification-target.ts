import type { NotificationItem } from '@/api/types'

export function resolveNotificationTarget(item: NotificationItem): string {
  if (isSafeInternalRoute(item.targetRoute)) {
    return item.targetRoute
  }

  switch (item.entityType?.toLowerCase()) {
    case 'review':
      return item.entityId ? `/dashboard/reviews/${item.entityId}` : '/dashboard/reviews'
    case 'report':
      return '/dashboard/reports'
    case 'promotion':
      return '/dashboard/promotions'
    default:
      return '/dashboard/notifications'
  }
}

function isSafeInternalRoute(targetRoute?: string | null): targetRoute is string {
  if (!targetRoute) {
    return false
  }
  return targetRoute.startsWith('/') && !targetRoute.startsWith('//')
}
