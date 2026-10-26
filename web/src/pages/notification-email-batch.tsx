import { useState } from 'react'
import { useParams, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ApiError } from '@/api/client'
import { useAuth } from '@/features/auth/use-auth'
import { notificationEmailApi } from '@/features/notification/notification-email-api'
import { getNotificationQueryKeyScope } from '@/features/notification/notification-session'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
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
  const [cursors, setCursors] = useState<(string | null)[]>([null])
  const [index, setIndex] = useState(0)
  const cursor = cursors[index]
  const query = useQuery({ queryKey: [...getNotificationQueryKeyScope(user?.userId), 'email-batch', id, cursor], queryFn: () => notificationEmailApi.batch(id, cursor), enabled: !!user })
  const batch = query.data
  return <div className="space-y-6">
    <Link to="/settings/notifications" className="text-sm text-muted-foreground underline">{t('notificationEmail.back')}</Link>
    <DashboardPageHeader title={t('notificationEmail.batchTitle')} subtitle={t('notificationEmail.batchHelp')} />
    {query.isPending ? <p role="status">{t('notificationEmail.loading')}</p> : null}
    {query.isError ? <div role="alert"><p>{t(query.error instanceof ApiError && query.error.status === 404 ? 'notificationEmail.expired' : query.error instanceof ApiError && query.error.status === 403 ? 'notificationEmail.forbidden' : 'notificationEmail.loadFailed')}</p><Button variant="outline" size="sm" onClick={() => void query.refetch()}>{t('notificationEmail.refresh')}</Button></div> : null}
    {batch ? <Card className="space-y-4 p-4 sm:p-6">
      <div className="flex flex-wrap gap-4 text-sm"><span>{t(`notificationEmail.status.${batch.status}`)}</span><time>{batch.createdAt ? new Date(batch.createdAt).toLocaleString(i18n.language) : ''}</time></div>
      {batch.mergedIntoDeliveryId ? <div className="space-y-2 rounded-lg border p-3 text-sm"><p>{t('notificationEmail.mergedHelp')}</p><Link to="/dashboard/notifications/email/$id" params={{ id: String(batch.mergedIntoDeliveryId) }} className="underline">{t('notificationEmail.viewMergedBatch', { id: batch.mergedIntoDeliveryId })}</Link></div> : null}
      <p className="text-sm text-muted-foreground">{t('notificationEmail.itemCount', { count: batch.totalItems })}</p>
      {!!batch.unavailableCount && <p role="status" className="text-sm text-muted-foreground">{t('notificationEmail.unavailableItems', { count: batch.unavailableCount })}</p>}
      {!batch.items?.length ? <p className="py-6 text-center text-sm text-muted-foreground">{t('notificationEmail.noItems')}</p> : <ul className="divide-y">{batch.items.map((item) => <li key={item.id} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm"><div><p>{t(`notification.preferences.${item.category?.toLowerCase()}`)}</p><time className="text-xs text-muted-foreground">{item.occurredAt ? new Date(item.occurredAt).toLocaleString(i18n.language) : ''}</time></div>{item.targetPath?.startsWith('/') && !item.targetPath.startsWith('//') && !item.targetPath.includes('\\') ? <a href={withBasePath(item.targetPath)} className="underline">{t('notificationEmail.openItem')}</a> : null}</li>)}</ul>}
      <nav aria-label={t('pagination.label')} className="flex items-center justify-center gap-2"><Button size="sm" variant="outline" disabled={index === 0 || query.isPending} onClick={() => setIndex((value) => value - 1)}>{t('pagination.prev')}</Button><Button size="sm" variant="outline" disabled={!batch.nextCursor || query.isPending} onClick={() => { if (batch.nextCursor) { setCursors((current) => [...current.slice(0, index + 1), batch.nextCursor ?? null]); setIndex((value) => value + 1) } }}>{t('pagination.next')}</Button></nav>
    </Card> : null}
  </div>
}
