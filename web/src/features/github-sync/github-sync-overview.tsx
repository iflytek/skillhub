import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight } from 'lucide-react'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { githubSyncStatusLabel } from './status-label'
import { GithubSyncPlatformControl } from './github-sync-platform-control'
import { useGithubDeepDiscoveryConfiguration, useGithubDeepDiscoveryProgress, useGithubDeepDiscoverySummary } from './use-github-deep-discovery'
import { useGithubCredentials, useGithubDiscoveryEntries, useGithubPlatformControl, useGithubSyncAdmissionPolicy, useGithubSyncRepositories, useGithubSyncRuns, useGithubSyncWorkerStatus } from './use-github-sync-admin'

type Props = { visible: boolean; onCollection: () => void; onSettings: () => void; onRuns: () => void; onRun: (runId: string) => void }

export function GithubSyncOverview({ visible, onCollection, onSettings, onRuns, onRun }: Props) {
  const { t, i18n } = useTranslation()
  const worker = useGithubSyncWorkerStatus(visible)
  const control = useGithubPlatformControl(visible)
  const policy = useGithubSyncAdmissionPolicy(visible)
  const credentials = useGithubCredentials(visible)
  const entries = useGithubDiscoveryEntries(visible)
  const configuration = useGithubDeepDiscoveryConfiguration(visible)
  const progress = useGithubDeepDiscoveryProgress(visible)
  const observedAt = progress.data?.observedAt
  const from = observedAt ? new Date(Date.parse(observedAt) - 7 * 86_400_000).toISOString() : undefined
  const summary = useGithubDeepDiscoverySummary(from, observedAt, visible)
  const runs = useGithubSyncRuns(0, 5, visible)
  const registered = useGithubSyncRepositories({ page: 0, size: 1, state: 'REGISTERED' }, visible, 0, 30_000)
  const pending = useGithubSyncRepositories({ page: 0, size: 1, state: 'PENDING' }, visible, 0, 30_000)
  const date = (value?: string | null) => formatLocalDateTime(value ?? undefined, i18n.language)

  const workerCompatible = summary.data?.worker?.state === 'AVAILABLE'
  const capacityReady = policy.data?.globalHigh != null && policy.data?.globalLow != null
    && policy.data?.namespaceHigh != null && policy.data?.namespaceLow != null
  const acquisitionReady = configuration.data?.enabled === true || entries.data?.some((entry) => entry.enabled) === true
  const checking = worker.isLoading || policy.isLoading || entries.isLoading || configuration.isLoading || credentials.isLoading || progress.isLoading || summary.isLoading || control.isLoading || registered.isLoading || pending.isLoading
  const checklistNeeded = checking || Boolean(worker.error || policy.error || entries.error || configuration.error || credentials.error || progress.error || summary.error || control.error)
    || !workerCompatible || !capacityReady || !acquisitionReady
  const issues = [worker.error, policy.error, entries.error, configuration.error, credentials.error, progress.error, summary.error, control.error, runs.error, registered.error, pending.error].filter(Boolean)
  const waits = summary.data?.waits ?? []
  const manuallyPaused = control.data?.desiredPaused === true
  const capacityPaused = policy.data?.globalPaused === true || policy.data?.platformPaused === true

  return <div className="space-y-5">
    <GithubSyncPlatformControl visible={visible} />
    {checklistNeeded ? <Card className="sync-section p-4 sm:p-5">
      <h2 className="font-semibold">{t('githubSync.preparation')}</h2>
      <p className="mt-1 text-sm text-muted-foreground">{t('githubSync.preparationHelp')}</p>
      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <div className="sync-rail p-3 text-sm"><strong>{t('githubSync.compatibleWorker')}</strong><p className="mt-1 text-muted-foreground">{progress.error || summary.error ? t('githubSync.statusUnknown') : summary.isLoading || progress.isLoading ? t('githubSync.loading') : t(`deepDiscovery.workerState.${summary.data?.worker?.state}`, { defaultValue: summary.data?.worker?.state ?? t('githubSync.statusUnknown') })}</p></div>
        <div className="sync-rail p-3 text-sm"><strong>{t('githubSync.capacityTitle')}</strong><p className="mt-1 text-muted-foreground">{policy.error ? t('githubSync.statusUnknown') : policy.isLoading ? t('githubSync.loading') : capacityReady ? t('githubSync.configured') : t('githubSync.unconfigured')}</p><Button data-analytics-id="github.settings.open" size="sm" variant="link" onClick={onSettings}>{t('githubSync.systemSettings')}</Button></div>
        <div className="sync-rail p-3 text-sm"><strong>{t('githubSync.credentials')}</strong><p className="mt-1 text-muted-foreground">{credentials.error ? t('githubSync.statusUnknown') : credentials.isLoading ? t('githubSync.loading') : credentials.data?.some((item) => item.status === 'ACTIVE') ? t('githubSync.credentialConfigured') : t('githubSync.anonymousQuota')}</p><Button data-analytics-id="github.settings.open" size="sm" variant="link" onClick={onSettings}>{t('githubSync.systemSettings')}</Button></div>
        <div className="sync-rail p-3 text-sm"><strong>{t('githubSync.acquisitionMethod')}</strong><p className="mt-1 text-muted-foreground">{entries.error || configuration.error ? t('githubSync.statusUnknown') : entries.isLoading || configuration.isLoading ? t('githubSync.loading') : acquisitionReady ? t('githubSync.configured') : t('githubSync.noEnabledAcquisition')}</p><Button data-analytics-id="github.collection.open" size="sm" variant="link" onClick={onCollection}>{t('githubSync.collectionConfig')}</Button></div>
      </div>
    </Card> : null}
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
      <Card className="sync-section p-4"><p className="text-xs text-muted-foreground">{t('githubSync.workerStatusTitle')}</p><p className="mt-2 text-lg font-semibold">{worker.error ? t('githubSync.statusUnknown') : worker.data ? worker.data.online ? t('githubSync.workerOnline') : t('githubSync.workerOffline') : '—'}</p><p className="mt-1 text-xs text-muted-foreground">{t('githubSync.workerLastSeen')}: {date(worker.data?.lastSeenAt)}</p></Card>
      <Card className="sync-section p-4"><p className="text-xs text-muted-foreground">{t('githubSync.registeredRepositories')}</p><p className="mt-2 text-2xl font-semibold tabular-nums">{registered.data?.total ?? '—'}</p><p className="mt-1 text-xs text-muted-foreground">{t('githubSync.registeredRepositoriesHelp')}</p></Card>
      <Card className="sync-section p-4"><p className="text-xs text-muted-foreground">{t('githubSync.pendingLeads')}</p><p className="mt-2 text-2xl font-semibold tabular-nums">{pending.data?.total ?? '—'}</p><p className="mt-1 text-xs text-muted-foreground">{t('githubSync.pendingLeadsHelp')}</p></Card>
      <Card className="sync-section p-4"><p className="text-xs text-muted-foreground">{t('githubSync.platformUnresolved')}</p><p className="mt-2 text-2xl font-semibold tabular-nums">{policy.data?.platformUnresolvedCount ?? '—'}</p><Link data-analytics-id="review.queue.open" to="/dashboard/reviews" search={{ type: 'sync' }} className="mt-2 inline-flex items-center gap-1 text-sm text-primary hover:underline">{t('githubSync.openReviewQueue')}<ArrowRight className="h-4 w-4" /></Link></Card>
      <Card className="sync-section p-4"><p className="text-xs text-muted-foreground">{t('githubSync.admissionState')}</p><p className="mt-2 text-lg font-semibold">{policy.error ? t('githubSync.statusUnknown') : policy.data ? !capacityReady ? t('githubSync.unconfigured') : policy.data.globalPaused ? t('githubSync.paused') : t('githubSync.active') : '—'}</p><p className="mt-1 text-xs text-muted-foreground">{t('githubSync.capacityHint')}</p></Card>
    </div>
    {issues.length ? <Card className="sync-section p-4"><h2 className="font-semibold">{t('githubSync.actionItems')}</h2><p role="alert" className="mt-2 text-sm text-destructive">{t('githubSync.someStatusUnavailable')}</p></Card> : null}
    {manuallyPaused ? <Card className="sync-section p-4 text-sm">{t('githubSync.manualPauseNotice')}</Card> : null}
    {capacityPaused ? <Card className="sync-section p-4 text-sm">{t('githubSync.capacityPauseNotice')}</Card> : null}
    {waits.length ? <Card className="sync-section p-4"><h2 className="font-semibold">{t('githubSync.currentWaits')}</h2><div className="mt-3 divide-y">{waits.map((wait, index) => <div key={`${wait.reason}:${index}`} className="py-2 text-sm"><p className="font-medium">{t(`deepDiscovery.reason.${wait.reason}`, { defaultValue: wait.reason ?? '—' })}</p><p className="text-muted-foreground">{t(`deepDiscovery.recovery.${wait.reason}`, { defaultValue: t('deepDiscovery.autoResume') })}{wait.retryAt ? ` · ${t('deepDiscovery.retryAt')}: ${date(wait.retryAt)}` : ''}</p></div>)}</div></Card> : !issues.length && !checking && workerCompatible && capacityReady && acquisitionReady && !manuallyPaused && !capacityPaused ? <Card className="sync-section p-4 text-sm">{t('githubSync.operatingNormally')}</Card> : null}
    <Card className="sync-section overflow-hidden">
      <div className="sync-section-header flex items-center justify-between gap-2"><h2 className="font-semibold">{t('githubSync.recentRuns')}</h2><Button data-analytics-id="github.runs.open" size="sm" variant="ghost" onClick={onRuns}>{t('githubSync.viewAllRuns')}<ArrowRight className="h-4 w-4" /></Button></div>
      {runs.isLoading ? <p className="p-4 text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
      {runs.error ? <p role="alert" className="p-4 text-sm text-destructive">{t('githubSync.loadFailed')}: {runs.error.message}</p> : null}
      {!runs.isLoading && !runs.error && !runs.data?.items?.length ? <p className="p-4 text-sm text-muted-foreground">{t('githubSync.empty')}</p> : null}
      {runs.data?.items?.map((run) => <div key={run.id} className="sync-ledger-row text-sm"><div className="min-w-0"><p className="font-medium">{githubSyncStatusLabel(t, run.kind)} · <span className="font-mono text-xs">{run.id?.slice(0, 8)}</span></p><p className="text-xs text-muted-foreground">{t('githubSync.createdAt')}: {date(run.createdAt)}</p></div><div className="flex items-center gap-2"><span className="sync-state" data-tone={run.status === 'FAILED' ? 'red' : run.status === 'ACQUISITION_COMPLETE' ? 'teal' : 'amber'}>{githubSyncStatusLabel(t, run.status)}</span>{run.id ? <Button data-analytics-id="github.run.open" size="sm" variant="outline" onClick={() => onRun(run.id!)}>{t('githubSync.viewRun')}</Button> : null}</div></div>)}
    </Card>
  </div>
}
