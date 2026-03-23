import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Trash2 } from 'lucide-react'
import type { NotificationItem } from '@/api/types'
import { useAuth } from '@/features/auth/use-auth'
import { resolveNotificationDisplay } from '@/features/notification/notification-content'
import { getNotificationItems, getNotificationTotal, shouldShowNotificationPagination } from '@/features/notification/notification-page'
import { resolveNotificationTarget } from '@/features/notification/notification-target'
import { useDeleteReadNotification, useNotificationList, useMarkAllRead, useMarkRead } from '@/features/notification/use-notifications'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'

const PAGE_SIZE = 20

type Category = 'ALL' | 'PUBLISH' | 'REVIEW' | 'PROMOTION' | 'REPORT'

const CATEGORIES: Category[] = ['ALL', 'PUBLISH', 'REVIEW', 'PROMOTION', 'REPORT']

function getCategoryKey(cat: Category): string {
  switch (cat) {
    case 'ALL': return 'notification.all'
    case 'PUBLISH': return 'notification.publish'
    case 'REVIEW': return 'notification.review'
    case 'PROMOTION': return 'notification.promotion'
    case 'REPORT': return 'notification.report'
  }
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

function CategoryBadge({ category }: { category: NotificationItem['category'] }) {
  const { t } = useTranslation()
  const colorMap: Record<NotificationItem['category'], string> = {
    PUBLISH: 'bg-blue-100 text-blue-700',
    REVIEW: 'bg-yellow-100 text-yellow-700',
    PROMOTION: 'bg-green-100 text-green-700',
    REPORT: 'bg-red-100 text-red-700',
  }
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${colorMap[category]}`}>
      {t(`notification.${category.toLowerCase()}`)}
    </span>
  )
}

export function NotificationsPage() {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [activeCategory, setActiveCategory] = useState<Category>('ALL')

  const categoryParam = activeCategory === 'ALL' ? undefined : activeCategory
  const { data, isLoading } = useNotificationList(user?.userId, page, PAGE_SIZE, categoryParam)
  const markAllRead = useMarkAllRead(user?.userId)
  const markRead = useMarkRead(user?.userId)
  const deleteRead = useDeleteReadNotification(user?.userId)

  const notifications = getNotificationItems(data)
  const totalPages = Math.max(Math.ceil(getNotificationTotal(data) / PAGE_SIZE), 1)

  function handleCategoryChange(cat: Category) {
    setActiveCategory(cat)
    setPage(0)
  }

  function handleItemClick(item: NotificationItem) {
    if (item.status === 'UNREAD') {
      markRead.mutate(item.id)
    }
    void navigate({ to: resolveNotificationTarget(item) })
  }

  function handleDelete(item: NotificationItem) {
    if (item.status !== 'READ') {
      return
    }
    deleteRead.mutate(item.id)
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader
        title={t('notification.title')}
        actions={
          <Button
            variant="outline"
            size="sm"
            onClick={() => markAllRead.mutate()}
            disabled={markAllRead.isPending || notifications.length === 0}
          >
            {t('notification.markAllRead')}
          </Button>
        }
      />

      {/* Category tabs */}
      <div className="flex gap-1 border-b">
        {CATEGORIES.map((cat) => (
          <button
            key={cat}
            type="button"
            onClick={() => handleCategoryChange(cat)}
            className={`px-4 py-2 text-sm font-medium transition-colors border-b-2 -mb-px ${
              activeCategory === cat
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            {t(getCategoryKey(cat))}
          </button>
        ))}
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-16 animate-shimmer rounded-xl" />
          ))}
        </div>
      ) : notifications.length === 0 ? (
        <Card className="p-12 text-center text-muted-foreground">{t('notification.empty')}</Card>
      ) : (
        <>
          <Card className="divide-y overflow-hidden p-0">
            {notifications.map((item) => {
              const display = resolveNotificationDisplay(item, i18n.language)
              return (
                <div key={item.id} className="flex items-start gap-3 px-5 py-4 hover:bg-muted/40 transition-colors">
                  <button
                    type="button"
                    onClick={() => handleItemClick(item)}
                    className="flex min-w-0 flex-1 items-start gap-3 text-left"
                  >
                    <span
                      className={`mt-2 h-2 w-2 flex-shrink-0 rounded-full ${
                        item.status === 'UNREAD' ? 'bg-red-500' : 'bg-transparent'
                      }`}
                    />
                    <div className="flex-1 min-w-0">
                      <p className={`text-sm ${item.status === 'UNREAD' ? 'font-semibold' : ''} truncate`}>
                        {display.title}
                      </p>
                      {display.description ? (
                        <p className="mt-0.5 text-xs text-muted-foreground truncate">{display.description}</p>
                      ) : null}
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        {t('notification.timeAgo', { time: formatRelativeTime(item.createdAt, i18n.language) })}
                      </p>
                    </div>
                  </button>
                  <div className="flex items-center gap-2">
                    <CategoryBadge category={item.category} />
                    {item.status === 'READ' ? (
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8 text-muted-foreground hover:text-foreground"
                        aria-label={t('notification.deleteRead')}
                        onClick={() => handleDelete(item)}
                        disabled={deleteRead.isPending}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    ) : null}
                  </div>
                </div>
              )
            })}
          </Card>

          {shouldShowNotificationPagination(getNotificationTotal(data), PAGE_SIZE) ? (
            <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
          ) : null}
        </>
      )}
    </div>
  )
}
