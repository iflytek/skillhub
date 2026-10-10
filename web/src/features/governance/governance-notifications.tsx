import { useTranslation } from 'react-i18next'
import type { GovernanceNotification } from '@/api/types'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'

interface GovernanceNotificationsProps {
  items?: GovernanceNotification[]
  isLoading: boolean
  onMarkRead: (id: number) => void
  isMarkingRead: boolean
}

/**
 * Displays governance notifications and exposes the minimal interaction needed
 * by the dashboard: mark unread items as read. More complex navigation remains
 * outside this component because notification payloads are heterogeneous.
 */
export function GovernanceNotifications({ items, isLoading, onMarkRead, isMarkingRead }: GovernanceNotificationsProps) {
  const { t, i18n } = useTranslation()

  if (isLoading) {
    return <div className="h-32 animate-shimmer rounded-xl" />
  }

  if (!items || items.length === 0) {
    return <Card className="p-10 text-center text-muted-foreground">{t('governance.emptyNotifications')}</Card>
  }

  return (
    <div className="space-y-3">
      {items.map((item) => (
        <Card key={`${item.category}-${item.id ?? item.entityId}`} className="p-4 space-y-2">
          <div className="flex items-center justify-between gap-3">
            <div className="font-medium">{item.title}</div>
            <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${item.status === 'UNREAD' ? 'bg-primary/10 text-primary' : 'bg-secondary text-secondary-foreground'}`}>
              {item.status}
            </span>
          </div>
          {item.createdAt ? (
            <div className="text-xs text-muted-foreground">
              {formatLocalDateTime(item.createdAt, i18n.language)}
            </div>
          ) : null}
          {item.bodyJson ? <div className="text-sm text-muted-foreground break-all">{item.bodyJson}</div> : null}
          {item.status === 'UNREAD' && item.id ? (
            <div className="flex justify-end">
              <Button size="sm" variant="outline" disabled={isMarkingRead} onClick={() => onMarkRead(item.id as number)}>
                {t('governance.markRead')}
              </Button>
            </div>
          ) : null}
        </Card>
      ))}
    </div>
  )
}
