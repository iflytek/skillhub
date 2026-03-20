import type { NotificationItem } from '@/api/types'

export function resolveNotificationTarget(item: NotificationItem): string {
  if (item.targetRoute) {
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
