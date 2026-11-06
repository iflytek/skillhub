import { useState } from 'react'
import { useParams, Link, useNavigate } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ApiError, authApi } from '@/api/client'
import { ArrowLeft, LogIn, Mail, RefreshCw, ShieldCheck } from 'lucide-react'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'
import { useAuth } from '@/features/auth/use-auth'
import { notificationEmailApi } from '@/features/notification/notification-email-api'
import { clearSessionScopedQueries, getNotificationQueryKeyScope } from '@/features/notification/notification-session'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { withBasePath } from '@/shared/lib/base-path'

export function NotificationEmailBatchPage() {
  const { id } = useParams({ from: '/dashboard/notifications/email/$id' })
  return <EmailBatchDetail key={id} id={id} />
}

export function EmailBatchDetail({ id }: { id: string }) {
  const { t, i18n } = useTranslation()
  const { user, isLoading: authLoading } = useAuth()
  const navigate = useNavigate()
  const client = useQueryClient()
  const returnTo = `/dashboard/notifications/email/${encodeURIComponent(id)}`
  const [cursors, setCursors] = useState<(string | null)[]>([null])
  const [index, setIndex] = useState(0)
  const cursor = cursors[index]
  const query = useQuery({
    queryKey: [...getNotificationQueryKeyScope(user?.userId), 'email-batch', id, cursor],
    queryFn: () => notificationEmailApi.batch(id, cursor),
    enabled: !!user,
    meta: { skipGlobalErrorHandler: true },
    retry: (attempts, error) => !(error instanceof ApiError && [401, 403, 404].includes(error.status)) && attempts < 1,
  })
  const switchAccount = useMutation({
    mutationFn: authApi.logout,
    meta: { skipGlobalErrorHandler: true },
    onSuccess: async () => {
      clearSessionScopedQueries(client)
      client.setQueryData(['auth', 'me'], null)
      await navigate({ to: '/login', search: { returnTo } })
    },
  })
  const status = query.error instanceof ApiError ? query.error.status : null
  const signInRequired = !authLoading && (!user || status === 401)
  const unavailable = status === 403 || status === 404
  const loading = authLoading || (!!user && query.isPending)
  const accountName = user?.displayName || user?.userId

  const batch = query.data
  return <div className={APP_SHELL_PAGE_CLASS_NAME}>
    <Link to="/settings/notifications" className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" aria-hidden="true" />{t('notificationEmail.back')}</Link>
    <DashboardPageHeader title={t('notificationEmail.batchTitle')} subtitle={t('notificationEmail.batchHelp')} />
    {loading ? <Card role="status" className="space-y-4 p-6 sm:p-8"><span className="sr-only">{t('notificationEmail.loading')}</span><div aria-hidden="true" className="space-y-3"><div className="h-5 w-40 animate-pulse rounded bg-muted" /><div className="h-4 w-3/4 animate-pulse rounded bg-muted" /><div className="h-4 w-1/2 animate-pulse rounded bg-muted" /></div></Card> : null}
    {signInRequired || unavailable || (query.isError && !loading) ? <Card role={signInRequired ? 'status' : 'alert'} className="overflow-hidden">
      <div className="flex flex-col gap-5 p-6 sm:flex-row sm:gap-6 sm:p-8">
        <div className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-muted text-muted-foreground">{unavailable ? <ShieldCheck className="size-6" aria-hidden="true" /> : signInRequired ? <LogIn className="size-6" aria-hidden="true" /> : <Mail className="size-6" aria-hidden="true" />}</div>
        <div className="min-w-0 flex-1 space-y-5">
          <div className="space-y-2"><h2 className="text-xl font-semibold leading-7">{t(signInRequired ? 'notificationEmail.batchState.signInTitle' : unavailable ? 'notificationEmail.batchState.unavailableTitle' : 'notificationEmail.batchState.failureTitle')}</h2><p className="max-w-2xl text-sm leading-6 text-muted-foreground">{t(signInRequired ? 'notificationEmail.batchState.signInDescription' : unavailable ? 'notificationEmail.batchState.unavailableDescription' : 'notificationEmail.batchState.failureDescription')}</p></div>
          {user && unavailable ? <p className="break-words text-sm [overflow-wrap:anywhere]">{t('notificationEmail.batchState.currentAccount', { account: accountName })}</p> : null}
          <div className="flex flex-wrap gap-3">
            {signInRequired ? <Link to="/login" search={{ returnTo }} className={buttonVariants()}><LogIn className="size-4" aria-hidden="true" />{t('notificationEmail.batchState.signIn')}</Link> : unavailable ? <Button onClick={() => switchAccount.mutate()} disabled={switchAccount.isPending}><LogIn className="size-4" aria-hidden="true" />{t(switchAccount.isPending ? 'notificationEmail.batchState.switchingAccount' : 'notificationEmail.batchState.switchAccount')}</Button> : null}
            {!signInRequired ? <Button variant={unavailable ? 'outline' : 'default'} disabled={query.isFetching} onClick={() => void query.refetch()}><RefreshCw className="size-4" aria-hidden="true" />{t('notificationEmail.refresh')}</Button> : null}
            <Link to="/settings/notifications" className={buttonVariants({ variant: 'outline' })}>{t('notificationEmail.back')}</Link>
          </div>
          {switchAccount.isError ? <p role="alert" className="text-sm text-destructive">{t('notificationEmail.batchState.switchFailed')}</p> : null}
        </div>
      </div>
      {unavailable ? <p className="border-t bg-muted/30 px-6 py-4 text-xs leading-5 text-muted-foreground sm:px-8">{t('notificationEmail.batchState.accountHint')}</p> : null}
    </Card> : null}
    {batch && !query.isError && !signInRequired ? <Card className="space-y-4 p-4 sm:p-6">
      <div className="flex flex-wrap gap-4 text-sm"><span>{t(`notificationEmail.status.${batch.status}`)}</span><time>{batch.createdAt ? new Date(batch.createdAt).toLocaleString(i18n.language) : ''}</time></div>
      {batch.mergedIntoDeliveryId ? <div className="space-y-2 rounded-lg border p-3 text-sm"><p>{t('notificationEmail.mergedHelp')}</p><Link to="/dashboard/notifications/email/$id" params={{ id: String(batch.mergedIntoDeliveryId) }} className="underline">{t('notificationEmail.viewMergedBatch', { id: batch.mergedIntoDeliveryId })}</Link></div> : null}
      <p className="text-sm text-muted-foreground">{t('notificationEmail.itemCount', { count: batch.totalItems })}</p>
      {!!batch.unavailableCount && <p role="status" className="text-sm text-muted-foreground">{t('notificationEmail.unavailableItems', { count: batch.unavailableCount })}</p>}
      {!batch.items?.length ? <p className="py-6 text-center text-sm text-muted-foreground">{t('notificationEmail.noItems')}</p> : <ul className="divide-y">{batch.items.map((item) => {
        const category = t(`notification.preferences.${item.category?.toLowerCase()}`, { defaultValue: t('notificationEmail.title') })
        const title = item.eventType
          ? t(`notificationEvents.${item.eventType}.title`, { defaultValue: category })
          : category
        return <li key={item.id} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
          <div className="min-w-0 space-y-1 break-words"><p className="font-medium">{title}</p><div className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">{title !== category ? <span>{category}</span> : null}<time>{item.occurredAt ? new Date(item.occurredAt).toLocaleString(i18n.language) : ''}</time></div></div>
          {item.targetPath?.startsWith('/') && !item.targetPath.startsWith('//') && !item.targetPath.includes('\\') ? <a href={withBasePath(item.targetPath)} className="underline">{t('notificationEmail.openItem')}</a> : null}
        </li>
      })}</ul>}
      <nav aria-label={t('pagination.label')} className="flex items-center justify-center gap-2"><Button size="sm" variant="outline" disabled={index === 0 || query.isPending} onClick={() => setIndex((value) => value - 1)}>{t('pagination.prev')}</Button><Button size="sm" variant="outline" disabled={!batch.nextCursor || query.isPending} onClick={() => { if (batch.nextCursor) { setCursors((current) => [...current.slice(0, index + 1), batch.nextCursor ?? null]); setIndex((value) => value + 1) } }}>{t('pagination.next')}</Button></nav>
    </Card> : null}
  </div>
}
