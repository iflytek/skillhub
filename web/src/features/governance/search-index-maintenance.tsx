import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Search } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Button } from '@/shared/ui/button'
import { toast } from '@/shared/lib/toast'
import { useRebuildSearchIndex } from './use-governance'

/** System maintenance remains separate from moderation work. */
export function SearchIndexMaintenance({ onBusyChange }: { onBusyChange?: (busy: boolean) => void }) {
  const { t } = useTranslation()
  const { hasRole } = useAuth()
  const [open, setOpen] = useState(false)
  const mutation = useRebuildSearchIndex()
  useEffect(() => { onBusyChange?.(mutation.isPending) }, [mutation.isPending, onBusyChange])
  if (!hasRole('SUPER_ADMIN')) return null
  const rebuild = async () => {
    try {
      await mutation.mutateAsync()
      setOpen(false)
      toast.success(t('governance.searchRebuildSuccessTitle'), t('governance.searchRebuildSuccessDescription'))
    } catch (error) {
      toast.error(t('governance.searchRebuildErrorTitle'), error instanceof Error ? error.message : '')
    }
  }
  return <section className="space-y-5">
    <header><h3 className="text-lg font-semibold">{t('governance.searchMaintenanceTitle')}</h3><p className="mt-1 text-sm text-muted-foreground">{t('governance.searchMaintenanceDescription')}</p></header>
    <div className="flex flex-wrap items-center gap-5 rounded-xl border p-5"><div className="rounded-lg bg-muted p-3"><Search aria-hidden className="h-5 w-5 text-muted-foreground" /></div><div className="min-w-0 flex-1"><h4 className="font-medium">{t('governance.searchRebuildAction')}</h4><p className="mt-1 text-sm text-muted-foreground">{t('governance.searchRebuildConfirmDescription')}</p><p className="mt-2 text-xs text-muted-foreground">{t('governance.searchMaintenanceHint')}</p></div><Button data-analytics-id="governance.index.rebuild" disabled={mutation.isPending} onClick={() => setOpen(true)}>{t(mutation.isPending ? 'governance.searchRebuildRunning' : 'governance.searchRebuildAction')}</Button></div>
    <ConfirmDialog closeOnConfirm={false} confirmAnalyticsId="governance.index.rebuild.confirm" cancelAnalyticsId="governance.index.rebuild.cancel" open={open} onOpenChange={setOpen} title={t('governance.searchRebuildConfirmTitle')} description={t('governance.searchRebuildConfirmDescription')} confirmText={t('governance.searchRebuildAction')} onConfirm={rebuild} />
  </section>
}
