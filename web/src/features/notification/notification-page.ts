import type { NotificationItem, PagedResponse } from '@/api/types'

export function getNotificationItems(page?: PagedResponse<NotificationItem>) {
  return page?.items ?? []
}

export function getNotificationTotal(page?: PagedResponse<NotificationItem>) {
  return page?.total ?? 0
}
