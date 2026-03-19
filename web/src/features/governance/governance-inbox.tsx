import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import type { GovernanceInboxItem } from '@/api/types'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'

interface GovernanceInboxProps {
  items?: GovernanceInboxItem[]
  isLoading: boolean
}

/**
 * Shows actionable governance work items and routes each item type to the most
 * relevant review surface. Navigation stays local because the backend payload is
 * intentionally generic and does not expose a single canonical frontend route.
 */
export function GovernanceInbox({ items, isLoading }: GovernanceInboxProps) {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()

  if (isLoading) {
    return <div className="h-32 animate-shimmer rounded-xl" />
  }

  if (!items || items.length === 0) {
    return <Card className="p-10 text-center text-muted-foreground">{t('governance.emptyInbox')}</Card>
  }

  const openItem = (item: GovernanceInboxItem) => {
    // Inbox items aggregate multiple workflows, so the UI resolves the target
    // screen from item type instead of relying on one backend-provided URL.
    if (item.type === 'REVIEW') {
      navigate({ to: `/dashboard/reviews/${item.id}` })
      return
    }
    if (item.type === 'PROMOTION') {
      navigate({ to: '/dashboard/promotions' })
      return
    }
    if (item.type === 'REPORT') {
      navigate({ to: '/dashboard/reports' })
      return
    }
    if (item.namespace && item.skillSlug) {
      navigate({ to: `/space/${item.namespace}/${item.skillSlug}` })
    }
  }

  return (
    <div className="space-y-4">
      {items.map((item) => (
        <Card key={`${item.type}-${item.id}`} className="p-5 space-y-3">
          <div className="flex items-start justify-between gap-4">
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <span className="rounded-full bg-secondary px-2 py-0.5 text-xs font-semibold text-secondary-foreground">
                  {item.type}
                </span>
                <div className="font-semibold font-heading">{item.title}</div>
              </div>
              {item.subtitle ? <div className="text-sm text-muted-foreground">{item.subtitle}</div> : null}
            </div>
            <div className="text-xs text-muted-foreground">
              {item.timestamp ? formatLocalDateTime(item.timestamp, i18n.language) : '-'}
            </div>
          </div>
          <div className="flex justify-end">
            <Button variant="outline" size="sm" onClick={() => openItem(item)}>
              {t('governance.openItem')}
            </Button>
          </div>
        </Card>
      ))}
    </div>
  )
}
