import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowRight, Mail, RefreshCw } from 'lucide-react'
import { ApiError } from '@/api/client'
import { notificationEmailApi, type EmailSettings, type EmailDeliveryPage } from './notification-email-api'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Switch } from '@/shared/ui/switch'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Pagination } from '@/shared/components/pagination'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'

interface EmailPanelProps {
  view?: 'mail' | 'deliveries' | 'all'
  onViewDeliveries?: () => void
  onDirtyChange?: (dirty: boolean) => void
  onBusyChange?: (busy: boolean) => void
}

export function NotificationEmailSettingsPanel({ view = 'all', onViewDeliveries, onDirtyChange, onBusyChange }: EmailPanelProps) {
  const { t } = useTranslation()
  const query = useQuery({ queryKey: ['system-config', 'notification-email'], queryFn: notificationEmailApi.settings })
  return <div className="space-y-6">
    <div hidden={view === 'deliveries'} className="grid items-start gap-8 xl:grid-cols-[minmax(0,1.5fr)_minmax(280px,1fr)]">
      <section className="min-w-0 space-y-5">
        <header className="flex items-start justify-between gap-4"><div><h3 className="text-lg font-semibold">{t('notificationEmail.adminTitle')}</h3><p className="mt-1 text-sm text-muted-foreground">{t('notificationEmail.audienceHelp')}</p></div>{query.data && <span className={`shrink-0 rounded-md px-2 py-1 text-xs ${query.data.smtpConfigured ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300' : 'bg-amber-500/10 text-amber-800 dark:text-amber-300'}`}>{t(query.data.smtpConfigured ? 'notificationEmail.smtpConfiguredStatus' : 'notificationEmail.smtpMissingStatus')}</span>}</header>
        {query.isPending ? <p role="status">{t('notificationEmail.loading')}</p> : null}
        {query.isError ? <div role="alert"><p>{t('notificationEmail.loadFailed')}</p><Button variant="outline" onClick={() => void query.refetch()}>{t('notificationEmail.refresh')}</Button></div> : null}
        {query.data ? <EmailSettingsFields key={query.data.version} settings={query.data} onDirtyChange={onDirtyChange} onBusyChange={onBusyChange} /> : null}
      </section>
      {view !== 'all' && <EmailDeliveryList compact onViewAll={onViewDeliveries} />}
    </div>
    <div hidden={view === 'mail'}><EmailDeliveryList /></div>
  </div>
}

function EmailSettingsFields({ settings, onDirtyChange, onBusyChange }: { settings: EmailSettings; onDirtyChange?: (dirty: boolean) => void; onBusyChange?: (busy: boolean) => void }) {
  const { t } = useTranslation()
  const client = useQueryClient()
  const [enabled, setEnabled] = useState(settings.enabled ?? false)
  const [windowMinutes, setWindowMinutes] = useState(String(settings.digestWindowMinutes ?? 60))
  const [publicBaseUrl, setPublicBaseUrl] = useState(settings.publicBaseUrl ?? '')
  const [saved, setSaved] = useState(false)
  const dirty = enabled !== (settings.enabled ?? false) || windowMinutes !== String(settings.digestWindowMinutes ?? 60) || publicBaseUrl !== (settings.publicBaseUrl ?? '')
  const update = useMutation({ mutationFn: notificationEmailApi.updateSettings, onSuccess: async () => { setSaved(true); await client.invalidateQueries({ queryKey: ['system-config', 'notification-email'] }) } })
  useEffect(() => { onDirtyChange?.(dirty) }, [dirty, onDirtyChange])
  useEffect(() => { onBusyChange?.(update.isPending) }, [update.isPending, onBusyChange])
  function discard() { setEnabled(settings.enabled ?? false); setWindowMinutes(String(settings.digestWindowMinutes ?? 60)); setPublicBaseUrl(settings.publicBaseUrl ?? ''); setSaved(false); update.reset() }
  return <form className="space-y-5" onSubmit={(event) => { event.preventDefault(); if (!dirty || update.isPending) return; setSaved(false); update.mutate({ enabled, digestWindowMinutes: Number(windowMinutes), publicBaseUrl, version: settings.version }) }}>
    <div className="flex items-center justify-between gap-6 border-b py-4">
      <div className="min-w-0 space-y-1"><Label htmlFor="business-email-enabled">{t('notificationEmail.enabled')}</Label><p id="business-email-enabled-help" className="text-xs leading-5 text-muted-foreground">{t('notificationEmail.enabledDraftHelp')}</p></div>
      <Switch id="business-email-enabled" checked={enabled} onCheckedChange={setEnabled} disabled={update.isPending} aria-describedby="business-email-enabled-help" />
    </div>
    <p className="border-l-2 bg-muted/40 px-4 py-3 text-xs leading-5 text-muted-foreground">{t('notificationEmail.smtpConfigurationHelp')}</p>
    <fieldset disabled={update.isPending} className="grid gap-4 sm:grid-cols-2"><div className="space-y-1.5"><Label htmlFor="email-digest-window">{t('notificationEmail.window')} <span aria-hidden className="text-destructive">*</span></Label><Input id="email-digest-window" type="number" min={5} max={1440} required value={windowMinutes} onChange={(event) => setWindowMinutes(event.target.value)} /><p className="text-xs leading-5 text-muted-foreground">{t('notificationEmail.windowHelp')}</p></div><div className="space-y-1.5"><Label htmlFor="email-base-url">{t('notificationEmail.baseUrl')}</Label><Input id="email-base-url" type="url" required={enabled} value={publicBaseUrl} onChange={(event) => setPublicBaseUrl(event.target.value)} placeholder="https://skills.example.com" /><p className="text-xs leading-5 text-muted-foreground">{t('notificationEmail.baseUrlHelp')}</p></div></fieldset>
    {update.isError ? <div role="alert" className="space-y-2 text-sm text-destructive"><p>{t(update.error instanceof ApiError && update.error.status === 409 ? 'notificationEmail.conflict' : 'notificationEmail.actionFailed')}</p><Button type="button" variant="outline" onClick={() => void client.invalidateQueries({ queryKey: ['system-config', 'notification-email'] })}>{t('notificationEmail.refresh')}</Button></div> : null}
    {saved && <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">{t('systemConfig.saved')}</p>}
    <footer className="flex flex-wrap items-center justify-between gap-3 border-t pt-4"><span className="text-xs text-muted-foreground">{t(dirty ? 'systemConfig.unsaved' : 'systemConfig.upToDate')}</span><div className="flex gap-2"><Button type="button" variant="outline" disabled={!dirty || update.isPending} onClick={discard}>{t('systemConfig.discard')}</Button><Button disabled={!dirty || update.isPending || (enabled && !settings.smtpConfigured)} type="submit">{t(update.isPending ? 'systemConfig.saving' : 'notificationEmail.save')}</Button></div></footer>
  </form>
}

type Delivery = NonNullable<EmailDeliveryPage['items']>[number]
function deliveryTone(status?: string) {
  if (status === 'SENT') return 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
  if (status === 'FAILED') return 'bg-destructive/10 text-destructive'
  if (status === 'UNKNOWN') return 'bg-amber-500/10 text-amber-800 dark:text-amber-300'
  return 'bg-muted text-muted-foreground'
}
function EmailDeliveryList({ compact = false, onViewAll }: { compact?: boolean; onViewAll?: () => void }) {
  const { t, i18n } = useTranslation()
  const client = useQueryClient()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')
  const [selected, setSelected] = useState<Delivery | null>(null)
  const [detail, setDetail] = useState<Delivery | null>(null)
  const detailTrigger = useRef<HTMLElement | null>(null)
  const retryTrigger = useRef<HTMLElement | null>(null)
  function openDetail(item: Delivery) { detailTrigger.current = document.activeElement instanceof HTMLElement ? document.activeElement : null; setDetail(item) }
  const deliveries = useQuery({ queryKey: ['system-config', 'email-deliveries', page, status], queryFn: () => notificationEmailApi.deliveries(page, status) })
  const retry = useMutation({ mutationFn: ({ id, risk }: { id: number; risk: boolean }) => notificationEmailApi.retry(id, risk), onSuccess: async () => { setSelected(null); await client.invalidateQueries({ queryKey: ['system-config', 'email-deliveries'] }) } })
  function requestRetry(item: Delivery) { retryTrigger.current = detail ? detailTrigger.current : document.activeElement instanceof HTMLElement ? document.activeElement : null; retry.reset(); setDetail(null); setSelected(item) }
  const date = (value?: string) => value ? new Date(value).toLocaleString(i18n.language) : '—'
  const rows = compact ? deliveries.data?.items?.slice(0, 3) : deliveries.data?.items
  return <section className={compact ? 'min-w-0 space-y-4 rounded-xl border p-5' : 'min-w-0 space-y-4'} data-density="compact">
    <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="text-base font-semibold">{t(compact ? 'notificationEmail.recentDeliveries' : 'notificationEmail.deliveries')}</h3>{compact ? <Button variant="ghost" onClick={onViewAll}>{t('notificationEmail.viewRecords')}<ArrowRight aria-hidden className="h-4 w-4" /></Button> : <div className="flex flex-wrap items-center gap-2"><Label htmlFor="email-delivery-status">{t('notificationEmail.filter')}</Label><Select value={status || 'ALL'} onValueChange={value => { setStatus(value === 'ALL' ? '' : value); setPage(0) }}><SelectTrigger id="email-delivery-status" className="w-40"><SelectValue /></SelectTrigger><SelectContent>{['ALL', 'PENDING', 'PREPARING', 'CLAIMED', 'SENDING', 'SENT', 'RETRY', 'FAILED', 'UNKNOWN', 'SUPPRESSED', 'MERGED'].map(item => <SelectItem value={item} key={item}>{t(item === 'ALL' ? 'notificationEmail.all' : `notificationEmail.status.${item}`)}</SelectItem>)}</SelectContent></Select><Button variant="outline" onClick={() => void deliveries.refetch()}><RefreshCw aria-hidden className="h-4 w-4" />{t('notificationEmail.refresh')}</Button></div>}</div>
    {!compact && <p className="text-xs leading-5 text-muted-foreground">{t('notificationEmail.retryPolicy')}</p>}
    {deliveries.isPending ? <p role="status">{t('notificationEmail.loading')}</p> : null}
    {deliveries.isError ? <div role="alert"><p>{t('notificationEmail.loadFailed')}</p><Button variant="outline" onClick={() => void deliveries.refetch()}>{t('notificationEmail.refresh')}</Button></div> : null}
    {rows?.length === 0 ? <div className="py-6 text-center"><Mail aria-hidden className="mx-auto mb-2 h-5 w-5 text-muted-foreground" /><p className="text-sm text-muted-foreground">{t('notificationEmail.noDeliveries')}</p></div> : null}
    {!!rows?.length && (compact ? <div className="divide-y">{rows.map(item => <div key={item.id} className="space-y-2 py-4"><div className="flex items-center justify-between gap-2"><Button variant="link" className="h-auto p-0" onClick={() => openDetail(item)}>#{item.id}</Button><span className={`rounded-md px-2 py-1 text-xs ${deliveryTone(item.status)}`}>{t(`notificationEmail.status.${item.status}`)}</span></div><p className="break-all text-sm">{item.recipientId}</p><p className="text-xs text-muted-foreground">{date(item.createdAt)} · {t('notificationEmail.attemptCount', { count: item.attempts })}</p></div>)}</div> : <div className="overflow-x-auto rounded-lg border"><table className="w-full min-w-[640px] text-left text-sm"><thead className="bg-muted/50"><tr>{['batch', 'recipient', 'state', 'attempts', 'created', 'actions'].map((key) => <th className="px-3 py-3 text-xs font-medium text-muted-foreground" key={key}>{t(`notificationEmail.column.${key}`)}</th>)}</tr></thead><tbody>{rows.map(item => <tr key={item.id} className="border-t"><td className="px-3 py-3"><Button variant="link" className="h-auto p-0" onClick={() => openDetail(item)}>#{item.id}</Button>{item.mergedIntoDeliveryId ? <span className="block text-xs text-muted-foreground">{t('notificationEmail.mergedTarget', { id: item.mergedIntoDeliveryId })}</span> : null}</td><td className="max-w-44 break-all px-3 py-3">{item.recipientId}</td><td className="px-3 py-3"><span className={`whitespace-nowrap rounded-md px-2 py-1 text-xs ${deliveryTone(item.status)}`}>{t(`notificationEmail.status.${item.status}`)}</span>{item.resultCode ? <span className="mt-1 block text-xs text-muted-foreground">{item.resultCode}</span> : null}</td><td className="px-3 py-3">{item.attempts}</td><td className="whitespace-nowrap px-3 py-3">{date(item.createdAt)}</td><td className="px-3 py-3"><div className="flex gap-2"><Button size="xs" variant="ghost" onClick={() => openDetail(item)}>{t('notificationEmail.details')}</Button>{item.status === 'FAILED' || item.status === 'UNKNOWN' ? <Button size="xs" variant="outline" onClick={() => requestRetry(item)}>{t('notificationEmail.retry')}</Button> : null}</div></td></tr>)}</tbody></table></div>)}
    {!compact && deliveries.data ? <Pagination compact page={page} totalPages={deliveries.data.totalPages ?? 0} onPageChange={setPage} /> : null}
    {compact && <p className="rounded-lg bg-muted/40 px-3 py-3 text-xs leading-6 text-muted-foreground">{t('notificationEmail.deliveryRequirements')}</p>}
    <Dialog open={!!detail} onOpenChange={open => { if (!open) setDetail(null) }}><DialogContent onCloseAutoFocus={event => { event.preventDefault(); if (!selected) detailTrigger.current?.focus() }}><DialogHeader><DialogTitle>{t('notificationEmail.detailTitle', { id: detail?.id })}</DialogTitle><DialogDescription>{t('notificationEmail.deliveryMeaning')}</DialogDescription></DialogHeader>{detail && <><dl className="grid grid-cols-[100px_minmax(0,1fr)] gap-x-4 gap-y-3 text-sm">{[[t('notificationEmail.column.recipient'), detail.recipientId], [t('notificationEmail.column.state'), t(`notificationEmail.status.${detail.status}`)], [t('notificationEmail.column.attempts'), detail.attempts], [t('notificationEmail.column.created'), date(detail.createdAt)], [t('notificationEmail.resultCode'), detail.resultCode ?? '—']].map(([label, value]) => <div key={String(label)} className="contents"><dt className="text-muted-foreground">{label}</dt><dd className="break-all">{value}</dd></div>)}</dl>{(detail.status === 'FAILED' || detail.status === 'UNKNOWN') && <Button onClick={() => requestRetry(detail)}>{t('notificationEmail.retry')}</Button>}</>}</DialogContent></Dialog>
    <ConfirmDialog onCloseAutoFocus={event => { event.preventDefault(); retryTrigger.current?.focus() }} open={!!selected} onOpenChange={(open) => { if (!open) setSelected(null) }} title={t('notificationEmail.retryTitle', { id: selected?.id })} description={<>{t(selected?.status === 'UNKNOWN' ? 'notificationEmail.duplicateRisk' : 'notificationEmail.retryHelp')}{retry.isError ? <span role="alert" className="mt-2 block text-destructive">{t('notificationEmail.actionFailed')}</span> : null}</>} confirmText={t('notificationEmail.retry')} closeOnConfirm={false} onConfirm={async () => { if (selected?.id !== undefined) { try { await retry.mutateAsync({ id: selected.id, risk: selected.status === 'UNKNOWN' }) } catch { /* Keep the dialog open with an actionable error. */ } } }} />
  </section>
}
