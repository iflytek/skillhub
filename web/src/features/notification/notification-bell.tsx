import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { useUnreadCount } from './use-notifications'
import { useNotificationSse } from './use-notification-sse'
import { NotificationDropdown } from './notification-dropdown'

export function resolveNotificationUserId(user?: { userId?: string } | null) {
  return user?.userId
}

/**
 * Bell icon with unread badge. Toggles the notification dropdown on click.
 * SSE connection is established here at the authenticated user level.
 */
export function NotificationBell() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  const notificationUserId = resolveNotificationUserId(user)
  const { data: unreadData } = useUnreadCount(notificationUserId)
  const unreadCount = unreadData?.count ?? 0

  useNotificationSse(notificationUserId)

  // Close dropdown when clicking outside
  useEffect(() => {
    if (!open) return
    function handleClick(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open])

  const badgeLabel = unreadCount > 99 ? '99+' : String(unreadCount)

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        aria-label={t('notification.title')}
        onClick={() => setOpen((v) => !v)}
        className="relative flex items-center justify-center w-8 h-8 rounded-full hover:bg-gray-100 transition-colors"
      >
        {/* Bell SVG */}
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>

        {/* Unread badge */}
        {unreadCount > 0 && (
          <span
            className="absolute -top-1 -right-1 min-w-[16px] h-4 px-0.5 flex items-center justify-center rounded-full bg-red-500 text-white text-[10px] font-semibold leading-none"
            aria-label={`${unreadCount} unread`}
          >
            {badgeLabel}
          </span>
        )}
      </button>

      {open && (
        <NotificationDropdown onClose={() => setOpen(false)} />
      )}
    </div>
  )
}
