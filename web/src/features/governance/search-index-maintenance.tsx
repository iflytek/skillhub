import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { toast } from '@/shared/lib/toast'
import { useRebuildSearchIndex } from './use-governance'

/** System maintenance remains separate from moderation work. */
export function SearchIndexMaintenance() {
  const { t } = useTranslation()
  const { hasRole } = useAuth()
  const [open, setOpen] = useState(false)
  const mutation = useRebuildSearchIndex()
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
  return <>
    <Card className="space-y-4 border-amber-500/20 bg-amber-500/5 p-5">
      <div><h2 className="text-lg font-semibold">{t('governance.searchMaintenanceTitle')}</h2><p className="mt-1 text-sm text-muted-foreground">{t('governance.searchMaintenanceDescription')}</p></div>
      <div className="flex flex-wrap items-center gap-3"><Button data-analytics-id="governance.index.rebuild" type="button" variant="outline" disabled={mutation.isPending} onClick={() => setOpen(true)}>{t(mutation.isPending ? 'governance.searchRebuildRunning' : 'governance.searchRebuildAction')}</Button><span className="text-xs text-muted-foreground">{t('governance.searchMaintenanceHint')}</span></div>
    </Card>
    <ConfirmDialog closeOnConfirm={false} confirmAnalyticsId="governance.index.rebuild.confirm" cancelAnalyticsId="governance.index.rebuild.cancel" open={open} onOpenChange={setOpen} title={t('governance.searchRebuildConfirmTitle')} description={t('governance.searchRebuildConfirmDescription')} confirmText={t('governance.searchRebuildAction')} onConfirm={rebuild} />
  </>
}
