import { useTranslation } from 'react-i18next'
import type { GovernanceActivityItem } from '@/api/types'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Card } from '@/shared/ui/card'

interface GovernanceActivityProps {
  items?: GovernanceActivityItem[]
  isLoading: boolean
}

/**
 * Renders a read-only timeline of governance actions surfaced on the dashboard.
 * The component stays intentionally thin: formatting and empty/loading states live
 * here, while filtering and retrieval are owned by the parent query layer.
 */
export function GovernanceActivity({ items, isLoading }: GovernanceActivityProps) {
  const { t, i18n } = useTranslation()

  if (isLoading) {
    return <div className="h-32 animate-shimmer rounded-xl" />
  }

  if (!items || items.length === 0) {
    return <Card className="p-10 text-center text-muted-foreground">{t('governance.emptyActivity')}</Card>
  }

  return (
    <div className="space-y-3">
      {items.map((item) => (
        <Card key={item.id} className="p-4 space-y-2">
          <div className="flex items-center justify-between gap-3">
            <div className="font-medium">{item.action}</div>
            <div className="text-xs text-muted-foreground">
              {item.timestamp ? formatLocalDateTime(item.timestamp, i18n.language) : '-'}
            </div>
          </div>
          <div className="text-sm text-muted-foreground">
            {item.actorDisplayName || item.actorUserId || t('governance.unknownActor')}
          </div>
          {item.details ? <div className="text-sm text-foreground break-all">{item.details}</div> : null}
        </Card>
      ))}
    </div>
  )
}
