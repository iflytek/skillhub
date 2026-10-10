import type { NotificationItem, PagedResponse } from '@/api/types'

export function getNotificationItems(page?: PagedResponse<NotificationItem>) {
  return page?.items ?? []
}

export function getNotificationTotal(page?: PagedResponse<NotificationItem>) {
  return page?.total ?? 0
}

export function shouldShowNotificationPagination(total: number, pageSize: number) {
  return pageSize > 0 && total > pageSize
}
