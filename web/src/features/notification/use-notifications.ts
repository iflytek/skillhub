import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { notificationApi } from '@/api/client'
import type { NotificationItem, PagedResponse } from '@/api/types'
import { decrementUnreadCount, resetUnreadCount } from './notification-unread-cache'
import { getNotificationQueryKeyScope } from './notification-session'

export const NOTIFICATION_QUERY_KEYS = {
  list: (userId?: string | null, page?: number, size?: number, category?: string) => [
    ...getNotificationQueryKeyScope(userId),
    'list',
    page,
    size,
    ...(category ? [category] : []),
  ] as const,
  unreadCount: (userId?: string | null) => [...getNotificationQueryKeyScope(userId), 'unread-count'] as const,
}

const NOTIFICATION_POLL_INTERVAL_MS = 10_000
type NotificationPage = PagedResponse<NotificationItem>

function updateCachedLists(
  queryClient: QueryClient,
  userId: string | null | undefined,
  update: (page: NotificationPage) => NotificationPage,
) {
  queryClient.setQueriesData<NotificationPage>(
    { queryKey: [...getNotificationQueryKeyScope(userId), 'list'] },
    (current) => current ? update(current) : current,
  )
}

export function markCachedNotificationRead(
  queryClient: QueryClient,
  userId: string | null | undefined,
  notificationId: number,
  readAt = new Date().toISOString(),
) {
  updateCachedLists(queryClient, userId, (page) => ({
    ...page,
    items: page.items.map((item) => item.id === notificationId
      ? { ...item, status: 'READ', readAt: item.readAt ?? readAt }
      : item),
  }))
}

export function markAllCachedNotificationsRead(
  queryClient: QueryClient,
  userId: string | null | undefined,
  readAt = new Date().toISOString(),
) {
  updateCachedLists(queryClient, userId, (page) => ({
    ...page,
    items: page.items.map((item) => item.status === 'READ'
      ? item
      : { ...item, status: 'READ', readAt }),
  }))
}

export function removeCachedNotification(
  queryClient: QueryClient,
  userId: string | null | undefined,
  notificationId: number,
) {
  updateCachedLists(queryClient, userId, (page) => {
    const items = page.items.filter((item) => item.id !== notificationId)
    return {
      ...page,
      items,
      total: page.total - (page.items.length - items.length),
    }
  })
}

export function getNotificationListQueryOptions(
  userId?: string | null,
  page = 0,
  size = 20,
  category?: string,
) {
  return {
    queryKey: NOTIFICATION_QUERY_KEYS.list(userId, page, size, category),
    queryFn: () => notificationApi.list({ page, size, category }),
    enabled: !!userId,
    staleTime: 0,
    refetchInterval: NOTIFICATION_POLL_INTERVAL_MS,
    refetchOnWindowFocus: true,
  }
}

export function getUnreadCountQueryOptions(userId?: string | null) {
  return {
    queryKey: NOTIFICATION_QUERY_KEYS.unreadCount(userId),
    queryFn: () => notificationApi.getUnreadCount(),
    enabled: !!userId,
    staleTime: 0,
    refetchInterval: NOTIFICATION_POLL_INTERVAL_MS,
    refetchOnWindowFocus: true,
  }
}

/**
 * Fetches the current unread notification count for the badge.
 */
export function useUnreadCount(userId?: string | null) {
  return useQuery(getUnreadCountQueryOptions(userId))
}

/**
 * Fetches paginated notification list with optional category filter.
 */
export function useNotificationList(userId?: string | null, page = 0, size = 20, category?: string) {
  return useQuery(getNotificationListQueryOptions(userId, page, size, category))
}

/**
 * Marks all notifications as read and invalidates relevant queries.
 */
export function useMarkAllRead(userId?: string | null) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => notificationApi.markAllRead(),
    onSuccess: () => {
      resetUnreadCount(queryClient, userId)
      markAllCachedNotificationsRead(queryClient, userId)
      void queryClient.invalidateQueries({ queryKey: getNotificationQueryKeyScope(userId) })
    },
  })
}

/**
 * Marks a single notification as read and invalidates relevant queries.
 */
export function useMarkRead(userId?: string | null) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => notificationApi.markRead(id),
    onSuccess: (_result, id) => {
      decrementUnreadCount(queryClient, userId)
      markCachedNotificationRead(queryClient, userId, id)
      void queryClient.invalidateQueries({ queryKey: getNotificationQueryKeyScope(userId) })
    },
  })
}

export function useDeleteReadNotification(userId?: string | null) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => notificationApi.deleteRead(id),
    onSuccess: (_result, id) => {
      removeCachedNotification(queryClient, userId, id)
      void queryClient.invalidateQueries({ queryKey: getNotificationQueryKeyScope(userId) })
    },
  })
}
