import { useEffect, useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { WEB_API_PREFIX } from '@/api/client'
import { createNotificationSseConnection } from './notification-sse-coordinator'
import { NOTIFICATION_QUERY_KEYS } from './use-notifications'

const SSE_URL = `${WEB_API_PREFIX}/notifications/sse`

/**
 * Opens an SSE connection to the notification stream.
 * On receiving a "notification" event, invalidates the unread count and list queries.
 * On reconnect (open event after initial connect), refetches unread count to sync badge.
 */
export function useNotificationSse(enabled: boolean) {
  const queryClient = useQueryClient()
  const esRef = useRef<ReturnType<typeof createNotificationSseConnection> | null>(null)
  const connectedRef = useRef(false)

  useEffect(() => {
    if (!enabled) return

    const es = createNotificationSseConnection(SSE_URL)
    esRef.current = es

    es.addEventListener('open', () => {
      if (connectedRef.current) {
        // Reconnect — refetch unread count to sync any missed events
        void queryClient.invalidateQueries({ queryKey: NOTIFICATION_QUERY_KEYS.unreadCount() })
      }
      connectedRef.current = true
    })

    es.addEventListener('notification', () => {
      void queryClient.invalidateQueries({ queryKey: NOTIFICATION_QUERY_KEYS.unreadCount() })
      void queryClient.invalidateQueries({ queryKey: ['notifications', 'list'] })
    })

    return () => {
      es.close()
      esRef.current = null
      connectedRef.current = false
    }
  }, [enabled, queryClient])
}
