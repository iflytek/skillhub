import type { QueryClient } from '@tanstack/react-query'
import type { NotificationUnreadCount } from '@/api/types'
import { NOTIFICATION_QUERY_KEYS } from './use-notifications'

function normalizeUnreadCount(data: NotificationUnreadCount | undefined) {
  return Math.max(data?.count ?? 0, 0)
}

export function incrementUnreadCount(queryClient: QueryClient, userId?: string | null) {
  queryClient.setQueryData<NotificationUnreadCount>(
    NOTIFICATION_QUERY_KEYS.unreadCount(userId),
    (current) => ({ count: normalizeUnreadCount(current) + 1 })
  )
}

export function decrementUnreadCount(queryClient: QueryClient, userId?: string | null) {
  queryClient.setQueryData<NotificationUnreadCount>(
    NOTIFICATION_QUERY_KEYS.unreadCount(userId),
    (current) => ({ count: Math.max(normalizeUnreadCount(current) - 1, 0) })
  )
}

export function resetUnreadCount(queryClient: QueryClient, userId?: string | null) {
  queryClient.setQueryData<NotificationUnreadCount>(
    NOTIFICATION_QUERY_KEYS.unreadCount(userId),
    { count: 0 }
  )
}
