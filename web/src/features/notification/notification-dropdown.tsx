import { useTranslation } from 'react-i18next'
import { Link } from '@tanstack/react-router'
import type { NotificationItem } from '@/api/types'
import { getNotificationItems } from './notification-page'
import { resolveNotificationDisplay } from './notification-content'
import { useAuth } from '@/features/auth/use-auth'
import { useNotifications, useMarkAllRead, useMarkRead } from './use-notifications'
import { resolveNotificationTarget } from './notification-target'

interface Props {
  onClose: () => void
}

function formatRelativeTime(dateStr: string, lang: string): string {
  const diff = Date.now() - new Date(dateStr).getTime()
  const minutes = Math.floor(diff / 60_000)
  const hours = Math.floor(diff / 3_600_000)
  const days = Math.floor(diff / 86_400_000)

  const isChinese = lang.startsWith('zh')

  if (minutes < 1) return isChinese ? '刚刚' : 'just now'
  if (minutes < 60) return isChinese ? `${minutes}分钟` : `${minutes}m`
  if (hours < 24) return isChinese ? `${hours}小时` : `${hours}h`
  if (days < 30) return isChinese ? `${days}天` : `${days}d`
  return new Date(dateStr).toLocaleDateString()
}

/**
 * Dropdown panel showing the latest 5 notifications with mark-all-read and view-all actions.
 */
export function NotificationDropdown({ onClose }: Props) {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const { data, isLoading } = useNotifications(user?.userId, 0, 5)
  const markAllRead = useMarkAllRead(user?.userId)
  const markRead = useMarkRead(user?.userId)

  const notifications = getNotificationItems(data)

  function handleItemClick(item: NotificationItem) {
    if (item.status === 'UNREAD') {
      markRead.mutate(item.id)
    }
    onClose()
  }

  function handleMarkAllRead() {
    markAllRead.mutate()
  }

  return (
    <div
      className="absolute right-0 top-10 z-50 w-80 rounded-xl border bg-white shadow-lg"
      style={{ borderColor: 'hsl(var(--border))' }}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b" style={{ borderColor: 'hsl(var(--border))' }}>
        <span className="text-sm font-semibold" style={{ color: 'hsl(var(--foreground))' }}>
          {t('notification.title')}
        </span>
        <button
          type="button"
          onClick={handleMarkAllRead}
          disabled={markAllRead.isPending || notifications.length === 0}
          className="text-xs hover:opacity-70 transition-opacity disabled:opacity-40"
          style={{ color: 'hsl(var(--text-secondary))' }}
        >
          {t('notification.markAllRead')}
        </button>
      </div>

      {/* Body */}
      <ul className="max-h-72 overflow-y-auto divide-y" style={{ borderColor: 'hsl(var(--border))' }}>
        {isLoading ? (
          <li className="px-4 py-6 text-center text-sm" style={{ color: 'hsl(var(--muted-foreground))' }}>
            …
          </li>
        ) : notifications.length === 0 ? (
          <li className="px-4 py-6 text-center text-sm" style={{ color: 'hsl(var(--muted-foreground))' }}>
            {t('notification.empty')}
          </li>
        ) : (
          notifications.map((item) => (
            <li key={item.id}>
              {(() => {
                const display = resolveNotificationDisplay(item, i18n.language)
                return (
              <Link
                to={resolveNotificationTarget(item)}
                onClick={() => handleItemClick(item)}
                className="flex items-start gap-3 px-4 py-3 hover:bg-gray-50 transition-colors"
              >
                {/* Unread dot */}
                <span className={`mt-1.5 flex-shrink-0 w-2 h-2 rounded-full ${item.status === 'UNREAD' ? 'bg-red-500' : 'bg-transparent'}`} />
                <div className="flex-1 min-w-0">
                  <p className="text-sm truncate" style={{ color: 'hsl(var(--foreground))' }}>
                    {display.title}
                  </p>
                  {display.description ? (
                    <p className="mt-0.5 truncate text-xs" style={{ color: 'hsl(var(--muted-foreground))' }}>
                      {display.description}
                    </p>
                  ) : null}
                  <p className="text-xs mt-0.5" style={{ color: 'hsl(var(--muted-foreground))' }}>
                    {t('notification.timeAgo', { time: formatRelativeTime(item.createdAt, i18n.language) })}
                  </p>
                </div>
              </Link>
                )
              })()}
            </li>
          ))
        )}
      </ul>

      {/* Footer */}
      <div className="px-4 py-2.5 border-t text-center" style={{ borderColor: 'hsl(var(--border))' }}>
        <Link
          to="/dashboard/notifications"
          onClick={onClose}
          className="text-xs hover:opacity-70 transition-opacity"
          style={{ color: 'hsl(var(--text-secondary))' }}
        >
          {t('notification.viewAll')}
        </Link>
      </div>
    </div>
  )
}
