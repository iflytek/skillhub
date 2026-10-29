import { useTranslation } from 'react-i18next'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { GithubAdmissionConfig } from './github-admission-config'
import { GithubCredentialConfig } from './github-credential-config'
import { GithubScheduleConfig } from './github-schedule-config'
import { useGithubSyncAdmissionPolicy, useGithubSyncSchedulePolicy } from './use-github-sync-admin'

export function GithubSyncSettingsDrawer({ onClose, visible }: { onClose: () => void; visible: boolean }) {
  const { t } = useTranslation()
  const admission = useGithubSyncAdmissionPolicy(visible)
  const schedule = useGithubSyncSchedulePolicy(visible)

  return <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
    <DialogContent closeAnalyticsId="github.settings.close" className="github-sync-surface !left-auto !right-0 !top-0 !h-dvh !max-h-dvh !grid-rows-[auto_minmax(0,1fr)] !gap-0 !overflow-hidden w-[min(100vw,44rem)] !translate-x-0 !translate-y-0 !rounded-none p-0">
      <DialogHeader className="border-b px-5 py-4 text-left">
        <DialogTitle>{t('githubSync.systemSettings')}</DialogTitle>
        <DialogDescription>{t('githubSync.settingsHelp')}</DialogDescription>
      </DialogHeader>
      <div className="min-h-0 space-y-6 overflow-y-auto overscroll-contain px-5 py-5">
        <GithubCredentialConfig />
        {admission.isLoading ? <p className="text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
        {admission.error ? <p role="alert" className="text-sm text-destructive">{t('githubSync.loadFailed')}: {admission.error.message}</p> : null}
        {admission.data ? <GithubAdmissionConfig policy={admission.data} /> : null}
        {schedule.isLoading ? <p className="text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
        {schedule.error ? <p role="alert" className="text-sm text-destructive">{t('githubSync.loadFailed')}: {schedule.error.message}</p> : null}
        {schedule.data ? <GithubScheduleConfig policy={schedule.data} /> : null}
      </div>
    </DialogContent>
  </Dialog>
}
