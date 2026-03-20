import { useEffect, useRef } from 'react'
import type { QueryClient } from '@tanstack/react-query'
import { useQueryClient } from '@tanstack/react-query'
import { WEB_API_PREFIX } from '@/api/client'
import { incrementUnreadCount } from './notification-unread-cache'
import { createNotificationSseConnection } from './notification-sse-coordinator'

const SSE_URL = `${WEB_API_PREFIX}/notifications/sse`

type NotificationSseConnectionLike = ReturnType<typeof createNotificationSseConnection>

export function attachNotificationSseListeners(
  connection: NotificationSseConnectionLike,
  queryClient: QueryClient,
  userId: string,
) {
  connection.addEventListener('open', () => {
    // No unread-count sync here. The badge is hydrated once on page load and then
    // updated locally from SSE events to avoid reconnect-driven request loops.
  })

  connection.addEventListener('notification', () => {
    incrementUnreadCount(queryClient, userId)
    void queryClient.invalidateQueries({ queryKey: ['notifications', userId, 'list'] })
  })
}

/**
 * Opens an SSE connection to the notification stream.
 * On receiving a "notification" event, updates the local unread badge and invalidates
 * the notification list. Reconnects no longer refetch unread-count to avoid turning
 * SSE churn into near-polling traffic.
 */
export function useNotificationSse(userId?: string | null) {
  const queryClient = useQueryClient()
  const esRef = useRef<ReturnType<typeof createNotificationSseConnection> | null>(null)

  useEffect(() => {
    if (!userId) return

    const es = createNotificationSseConnection(SSE_URL)
    esRef.current = es
    attachNotificationSseListeners(es, queryClient, userId)

    return () => {
      es.close()
      esRef.current = null
    }
  }, [userId, queryClient])
}
