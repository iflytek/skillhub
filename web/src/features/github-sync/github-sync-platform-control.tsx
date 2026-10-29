import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '@/shared/lib/api-error'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { useGithubPlatformControl, useUpdateGithubPlatformControl } from './use-github-sync-admin'

export function GithubSyncPlatformControl({ visible }: { visible: boolean }) {
  const { t } = useTranslation()
  const control = useGithubPlatformControl(visible)
  const update = useUpdateGithubPlatformControl()
  const [confirm, setConfirm] = useState(false)
  const paused = control.data?.desiredPaused === true

  const save = async () => {
    if (control.data?.version == null) return
    try {
      await update.mutateAsync({ expectedVersion: control.data.version, paused: !paused })
      setConfirm(false)
      toast.success(t(paused ? 'githubSync.controlResumeRequested' : 'githubSync.controlPauseRequested'))
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        toast.error(t('githubSync.controlConflict'))
        void control.refetch()
      } else toast.error(t('githubSync.saveFailed'), error instanceof Error ? error.message : '')
    }
  }

  return <>
    <Card className="sync-section flex flex-wrap items-center justify-between gap-4 p-4 sm:p-5">
      <div className="space-y-1"><h2 className="font-semibold">{t('githubSync.platformControl')}</h2>
        {control.isLoading ? <p className="text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
        {control.error ? <p role="alert" className="text-sm text-destructive">{t('githubSync.loadFailed')}: {control.error.message}</p> : null}
        {control.data ? <><p className="text-sm"><span className="sync-state" data-tone={control.data.state === 'PAUSED' ? 'amber' : control.data.state === 'PAUSING' ? 'blue' : 'teal'}>{t(`githubSync.controlState.${control.data.state}`, { defaultValue: control.data.state ?? '—' })}</span></p>
          {control.data.state === 'PAUSING' ? <p className="text-xs text-muted-foreground">{t('githubSync.pendingStops', { count: control.data.pendingStopCount ?? 0 })}{control.data.expiredUnconfirmedCount ? ` · ${t('githubSync.expiredStops', { count: control.data.expiredUnconfirmedCount })}` : ''}</p> : null}
          <p className="text-xs text-muted-foreground">{t('githubSync.platformControlScope')}</p></> : null}
      </div>
      <Button data-analytics-id={paused ? 'github.platform.resume.request' : 'github.platform.pause.request'} size="sm" variant={paused ? 'outline' : 'default'} disabled={!control.data || update.isPending} onClick={() => setConfirm(true)}>{t(paused ? 'githubSync.resumePlatform' : 'githubSync.pausePlatform')}</Button>
    </Card>
    <Dialog open={confirm} onOpenChange={setConfirm}>
      <DialogContent closeAnalyticsId={paused ? 'github.platform.resume.cancel' : 'github.platform.pause.cancel'} className="github-sync-surface rounded-md"><DialogHeader><DialogTitle>{t(paused ? 'githubSync.resumePlatform' : 'githubSync.pausePlatform')}</DialogTitle><DialogDescription>{t(paused ? 'githubSync.resumePlatformHelp' : 'githubSync.pausePlatformHelp')}</DialogDescription></DialogHeader>
        <DialogFooter><Button data-analytics-id={paused ? 'github.platform.resume.cancel' : 'github.platform.pause.cancel'} variant="outline" onClick={() => setConfirm(false)}>{t('githubSync.cancel')}</Button><Button data-analytics-id={paused ? 'github.platform.resume.confirm' : 'github.platform.pause.confirm'} disabled={update.isPending} onClick={() => void save()}>{t('githubSync.confirmControl')}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  </>
}
