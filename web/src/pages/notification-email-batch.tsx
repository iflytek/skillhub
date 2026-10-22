import { useState } from 'react'
import { useParams, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ApiError } from '@/api/client'
import { useAuth } from '@/features/auth/use-auth'
import { notificationEmailApi } from '@/features/notification/notification-email-api'
import { getNotificationQueryKeyScope } from '@/features/notification/notification-session'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { withBasePath } from '@/shared/lib/base-path'

export function NotificationEmailBatchPage() {
  const { id } = useParams({ from: '/dashboard/notifications/email/$id' })
  return <EmailBatchDetail key={id} id={id} />
}

export function EmailBatchDetail({ id }: { id: string }) {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const [page, setPage] = useState(0)
  const query = useQuery({ queryKey: [...getNotificationQueryKeyScope(user?.userId), 'email-batch', id, page], queryFn: () => notificationEmailApi.batch(id, page), enabled: !!user })
  const batch = query.data
  return <div className="space-y-6">
    <Link to="/settings/notifications" className="text-sm text-muted-foreground underline">{t('notificationEmail.back')}</Link>
    <DashboardPageHeader title={t('notificationEmail.batchTitle')} subtitle={t('notificationEmail.batchHelp')} />
    {query.isPending ? <p role="status">{t('notificationEmail.loading')}</p> : null}
    {query.isError ? <div role="alert"><p>{t(query.error instanceof ApiError && query.error.status === 404 ? 'notificationEmail.expired' : query.error instanceof ApiError && query.error.status === 403 ? 'notificationEmail.forbidden' : 'notificationEmail.loadFailed')}</p><Button variant="outline" size="sm" onClick={() => void query.refetch()}>{t('notificationEmail.refresh')}</Button></div> : null}
    {batch ? <Card className="space-y-4 p-4 sm:p-6">
      <div className="flex flex-wrap gap-4 text-sm"><span>{t(`notificationEmail.status.${batch.status}`)}</span><time>{batch.createdAt ? new Date(batch.createdAt).toLocaleString(i18n.language) : ''}</time></div>
      <p className="text-sm text-muted-foreground">{t('notificationEmail.itemCount', { count: batch.totalItems })}</p>
      {!!batch.unavailableItems && <p role="status" className="text-sm text-muted-foreground">{t('notificationEmail.unavailableItems', { count: batch.unavailableItems })}</p>}
      {!batch.items?.length ? <p className="py-6 text-center text-sm text-muted-foreground">{t('notificationEmail.noItems')}</p> : <ul className="divide-y">{batch.items.map((item) => <li key={item.id} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm"><div><p>{t(`notification.preferences.${item.category?.toLowerCase()}`)}</p><time className="text-xs text-muted-foreground">{item.occurredAt ? new Date(item.occurredAt).toLocaleString(i18n.language) : ''}</time></div>{item.targetPath?.startsWith('/') && !item.targetPath.startsWith('//') && !item.targetPath.includes('\\') ? <a href={withBasePath(item.targetPath)} className="underline">{t('notificationEmail.openItem')}</a> : null}</li>)}</ul>}
      <Pagination compact page={page} totalPages={batch.totalPages ?? 0} onPageChange={setPage} />
    </Card> : null}
  </div>
}
