import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ApiError } from '@/api/client'
import { notificationEmailApi, type EmailSettings, type EmailDeliveryPage } from './notification-email-api'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Pagination } from '@/shared/components/pagination'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'

export function NotificationEmailSettingsPanel() {
  const { t } = useTranslation()
  const query = useQuery({ queryKey: ['system-config', 'notification-email'], queryFn: notificationEmailApi.settings })
  return <Card><CardHeader><CardTitle>{t('notificationEmail.adminTitle')}</CardTitle></CardHeader><CardContent className="space-y-5">
    {query.isPending ? <p role="status">{t('notificationEmail.loading')}</p> : null}
    {query.isError ? <div role="alert"><p>{t('notificationEmail.loadFailed')}</p><Button variant="outline" size="sm" onClick={() => void query.refetch()}>{t('notificationEmail.refresh')}</Button></div> : null}
    {query.data ? <EmailSettingsFields key={query.data.version} settings={query.data} /> : null}
    <EmailDeliveryList />
  </CardContent></Card>
}

function EmailSettingsFields({ settings }: { settings: EmailSettings }) {
  const { t } = useTranslation()
  const client = useQueryClient()
  const [enabled, setEnabled] = useState(settings.enabled ?? false)
  const [windowMinutes, setWindowMinutes] = useState(String(settings.digestWindowMinutes ?? 60))
  const [publicBaseUrl, setPublicBaseUrl] = useState(settings.publicBaseUrl ?? '')
  const update = useMutation({ mutationFn: notificationEmailApi.updateSettings, onSuccess: () => client.invalidateQueries({ queryKey: ['system-config', 'notification-email'] }) })
  return <form className="space-y-4" onSubmit={(event) => { event.preventDefault(); update.mutate({ enabled, digestWindowMinutes: Number(windowMinutes), publicBaseUrl, version: settings.version }) }}>
    <label className="flex items-center gap-3 text-sm"><input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />{t('notificationEmail.enabled')}</label>
    <p className="text-sm text-muted-foreground">{t(settings.smtpConfigured ? 'notificationEmail.smtpReady' : 'notificationEmail.smtpMissing')}</p>
    <div className="grid gap-4 sm:grid-cols-2"><div className="space-y-1"><Label htmlFor="email-digest-window">{t('notificationEmail.window')}</Label><Input id="email-digest-window" type="number" min={5} max={1440} required value={windowMinutes} onChange={(event) => setWindowMinutes(event.target.value)} /></div><div className="space-y-1"><Label htmlFor="email-base-url">{t('notificationEmail.baseUrl')}</Label><Input id="email-base-url" type="url" required={enabled} value={publicBaseUrl} onChange={(event) => setPublicBaseUrl(event.target.value)} placeholder="https://skills.example.com" /></div></div>
    <p className="text-xs text-muted-foreground">{t('notificationEmail.windowHelp')}</p>
    <p className="text-xs text-muted-foreground">{t('notificationEmail.inFlight')}</p>
    {update.isError ? <p role="alert" className="text-sm text-destructive">{t(update.error instanceof ApiError && update.error.status === 409 ? 'notificationEmail.conflict' : 'notificationEmail.actionFailed')} <Button type="button" variant="outline" size="sm" onClick={() => void client.invalidateQueries({ queryKey: ['system-config', 'notification-email'] })}>{t('notificationEmail.refresh')}</Button></p> : null}
    <Button size="sm" disabled={update.isPending || (enabled && !settings.smtpConfigured)} type="submit">{t('notificationEmail.save')}</Button>
  </form>
}

type Delivery = NonNullable<EmailDeliveryPage['items']>[number]
function EmailDeliveryList() {
  const { t, i18n } = useTranslation()
  const client = useQueryClient()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')
  const [selected, setSelected] = useState<Delivery | null>(null)
  const deliveries = useQuery({ queryKey: ['system-config', 'email-deliveries', page, status], queryFn: () => notificationEmailApi.deliveries(page, status) })
  const retry = useMutation({ mutationFn: ({ id, risk }: { id: number; risk: boolean }) => notificationEmailApi.retry(id, risk), onSuccess: async () => { setSelected(null); await client.invalidateQueries({ queryKey: ['system-config', 'email-deliveries'] }) } })
  return <section className="space-y-3 border-t pt-5" data-density="compact"><div className="flex flex-wrap items-center justify-between gap-3"><h3 className="text-sm font-semibold">{t('notificationEmail.deliveries')}</h3><div className="flex items-center gap-2"><Label htmlFor="email-delivery-status">{t('notificationEmail.filter')}</Label><select id="email-delivery-status" className="h-8 rounded-md border bg-background px-2 text-sm" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}>{['', 'PENDING', 'CLAIMED', 'SENDING', 'SENT', 'RETRY', 'FAILED', 'UNKNOWN', 'SUPPRESSED'].map((item) => <option value={item} key={item}>{t(item ? `notificationEmail.status.${item}` : 'notificationEmail.all')}</option>)}</select></div></div>
    {deliveries.isPending ? <p role="status">{t('notificationEmail.loading')}</p> : null}
    {deliveries.isError ? <div role="alert"><p>{t('notificationEmail.loadFailed')}</p><Button size="sm" variant="outline" onClick={() => void deliveries.refetch()}>{t('notificationEmail.refresh')}</Button></div> : null}
    {deliveries.data?.items?.length === 0 ? <p className="py-4 text-center text-sm text-muted-foreground">{t('notificationEmail.noDeliveries')}</p> : null}
    {!!deliveries.data?.items?.length && <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr>{['batch', 'recipient', 'state', 'attempts', 'created', 'actions'].map((key) => <th className="px-2 py-2 font-medium" key={key}>{t(`notificationEmail.column.${key}`)}</th>)}</tr></thead><tbody>{deliveries.data.items.map((item) => <tr key={item.id} className="border-t"><td className="px-2 py-2">#{item.id}</td><td className="max-w-44 break-all px-2 py-2">{item.recipientId}</td><td className="px-2 py-2"><span>{t(`notificationEmail.status.${item.status}`)}</span>{item.resultCode ? <span className="block text-xs text-muted-foreground">{item.resultCode}</span> : null}</td><td className="px-2 py-2">{item.attempts}</td><td className="whitespace-nowrap px-2 py-2">{item.createdAt ? new Date(item.createdAt).toLocaleString(i18n.language) : ''}</td><td className="px-2 py-2">{item.status === 'FAILED' || item.status === 'UNKNOWN' ? <Button size="sm" variant="outline" onClick={() => { retry.reset(); setSelected(item) }}>{t('notificationEmail.retry')}</Button> : null}</td></tr>)}</tbody></table></div>}
    {deliveries.data ? <Pagination compact page={page} totalPages={deliveries.data.totalPages ?? 0} onPageChange={setPage} /> : null}
    <ConfirmDialog open={!!selected} onOpenChange={(open) => { if (!open) setSelected(null) }} title={t('notificationEmail.retryTitle', { id: selected?.id })} description={<>{t(selected?.status === 'UNKNOWN' ? 'notificationEmail.duplicateRisk' : 'notificationEmail.retryHelp')}{retry.isError ? <span role="alert" className="mt-2 block text-destructive">{t('notificationEmail.actionFailed')}</span> : null}</>} confirmText={t('notificationEmail.retry')} closeOnConfirm={false} onConfirm={async () => { if (selected?.id !== undefined) { try { await retry.mutateAsync({ id: selected.id, risk: selected.status === 'UNKNOWN' }) } catch { /* Keep the dialog open with an actionable error. */ } } }} />
  </section>
}
