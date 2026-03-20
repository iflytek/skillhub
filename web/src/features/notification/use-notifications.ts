import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationApi } from '@/api/client'
import type { NotificationItem, PagedResponse } from '@/api/types'

export const NOTIFICATION_QUERY_KEYS = {
  list: (page?: number, size?: number) => ['notifications', 'list', page, size] as const,
  unreadCount: () => ['notifications', 'unread-count'] as const,
}

/**
 * Fetches paginated notification list.
 */
export function useNotifications(page = 0, size = 5) {
  return useQuery({
    queryKey: NOTIFICATION_QUERY_KEYS.list(page, size),
    queryFn: () => notificationApi.list({ page, size }) as Promise<PagedResponse<NotificationItem>>,
    staleTime: 30_000,
  })
}

/**
 * Fetches the current unread notification count for the badge.
 */
export function useUnreadCount() {
  return useQuery({
    queryKey: NOTIFICATION_QUERY_KEYS.unreadCount(),
    queryFn: () => notificationApi.getUnreadCount(),
    staleTime: 30_000,
  })
}

/**
 * Fetches paginated notification list with optional category filter.
 */
export function useNotificationList(page = 0, size = 20, category?: string) {
  return useQuery({
    queryKey: ['notifications', 'list', page, size, category],
    queryFn: () => notificationApi.list({ page, size, category }) as Promise<PagedResponse<NotificationItem>>,
    staleTime: 30_000,
  })
}

/**
 * Marks all notifications as read and invalidates relevant queries.
 */
export function useMarkAllRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => notificationApi.markAllRead(),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

/**
 * Marks a single notification as read and invalidates relevant queries.
 */
export function useMarkRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => notificationApi.markRead(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}
