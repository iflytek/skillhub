import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { GithubDeepDiscoveryConfig } from './github-deep-discovery-config'
import { GithubDiscoveryConfig } from './github-discovery-config'
import { useGithubDeepDiscoveryConfiguration, useGithubDeepDiscoveryProgress, useGithubDeepDiscoverySummary } from './use-github-deep-discovery'
import { useGithubDiscoveryEntries, useGithubDiscoveryMutations } from './use-github-sync-admin'

function parseRepository(input: string) {
  const path = input.trim().replace(/^https:\/\/github\.com\//i, '').replace(/\.git\/?$/i, '').replace(/\/$/, '')
  const parts = path.split('/')
  return parts.length === 2 && parts.every((part) => /^[\w.-]+$/.test(part)) ? parts : null
}

export function GithubSyncCollectionDrawer({ onClose, onRepository, visible }: { onClose: () => void; onRepository: (id: number) => void; visible: boolean }) {
  const { t, i18n } = useTranslation()
  const configuration = useGithubDeepDiscoveryConfiguration(visible)
  const progress = useGithubDeepDiscoveryProgress(visible)
  const observedAt = progress.data?.observedAt
  const from = observedAt ? new Date(Date.parse(observedAt) - 7 * 86_400_000).toISOString() : undefined
  const summary = useGithubDeepDiscoverySummary(from, observedAt, visible)
  const entries = useGithubDiscoveryEntries(visible)
  const mutations = useGithubDiscoveryMutations()
  const [repository, setRepository] = useState('')
  const [expanded, setExpanded] = useState(false)
  const [configExpanded, setConfigExpanded] = useState(false)
  const [progressExpanded, setProgressExpanded] = useState(false)

  const addRepository = async (event: FormEvent) => {
    event.preventDefault()
    const parts = parseRepository(repository)
    if (!parts) { toast.error(t('githubSync.repositoryFormat')); return }
    try {
      await mutations.addRepository.mutateAsync({ ownerLogin: parts[0], repositoryName: parts[1] })
      setRepository('')
      toast.success(t('githubSync.repositoryQueued'))
    } catch (error) {
      toast.error(t('githubSync.saveFailed'), error instanceof Error ? error.message : '')
    }
  }

  const setEnabled = async (id: string, enabled: boolean) => {
    try {
      await mutations.setEnabled.mutateAsync({ id, enabled })
      toast.success(t('githubSync.saved'))
    } catch (error) {
      toast.error(t('githubSync.saveFailed'), error instanceof Error ? error.message : '')
    }
  }

  const specified = entries.data?.filter((entry) => entry.entryType === 'CURATED_REPOSITORY')
  return <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
    <DialogContent closeAnalyticsId="github.collection.close" className="github-sync-surface !left-auto !right-0 !top-0 !h-dvh !max-h-dvh !grid-rows-[auto_minmax(0,1fr)] !gap-0 !overflow-hidden w-[min(100vw,44rem)] !translate-x-0 !translate-y-0 !rounded-none p-0">
      <DialogHeader className="border-b px-5 py-4 text-left">
        <DialogTitle>{t('githubSync.collectionConfig')}</DialogTitle>
        <DialogDescription>{t('githubSync.collectionScope')}</DialogDescription>
      </DialogHeader>
      <div className="min-h-0 space-y-6 overflow-y-auto overscroll-contain px-5 py-5">
        <section className="space-y-3 rounded border p-4">
          <div><h2 className="font-semibold">{t('githubSync.automaticDiscovery')}</h2><p className="text-sm text-muted-foreground">{t('githubSync.automaticDiscoveryHelp')}</p></div>
          {configuration.isLoading ? <p className="text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
          {configuration.error ? <p role="alert" className="text-sm text-destructive">{t('githubSync.loadFailed')}: {configuration.error.message}</p> : null}
          {configuration.data ? <>
            <div className="flex flex-wrap items-center gap-3 text-sm">
              <span className="sync-state" data-tone={configuration.data.enabled ? 'teal' : 'amber'}>{configuration.data.enabled ? t('githubSync.enabled') : t('githubSync.disabled')}</span>
              <span>{t('deepDiscovery.budget')}: {t(`deepDiscovery.budgets.${configuration.data.budgetProfile}`, { defaultValue: '—' })}</span>
              {progress.data?.observedAt ? <span className="text-muted-foreground">{t('deepDiscovery.observedAt')}: {formatLocalDateTime(progress.data.observedAt, i18n.language)}</span> : null}
            </div>
            {summary.data?.recent ? <p className="text-sm text-muted-foreground">{t('githubSync.officialRecentResult', { candidates: summary.data.recent.newCandidateRepositories ?? '—', skills: summary.data.recent.newSkills ?? '—' })}{summary.data.recent.complete === false ? ` · ${t('githubSync.partialHistory')}` : ''}</p> : null}
            {summary.error ? <p role="alert" className="text-sm text-destructive">{t('githubSync.loadFailed')}: {summary.error.message}</p> : null}
            <Button data-analytics-id="github.discovery.edit" size="sm" variant="outline" aria-expanded={configExpanded} onClick={() => setConfigExpanded(!configExpanded)}>{t('githubSync.editAutomaticDiscovery')}</Button>
            {configExpanded ? <GithubDeepDiscoveryConfig key={configuration.data.version} configuration={configuration.data} onClose={onClose} embedded /> : null}
          </> : null}
          <div className="border-t pt-3"><button data-analytics-id="github.progress.toggle" type="button" className="flex w-full items-center justify-between text-left text-sm font-medium" aria-expanded={progressExpanded} onClick={() => setProgressExpanded(!progressExpanded)}>{t('githubSync.discoveryProgress')}<span aria-hidden="true">{progressExpanded ? '−' : '+'}</span></button>
            {progressExpanded ? <div className="mt-3 space-y-2 text-sm">
              {progress.error ? <p role="alert" className="text-destructive">{t('githubSync.loadFailed')}: {progress.error.message}</p> : null}
              {progress.data?.channels?.length ? <div className="divide-y rounded border">{progress.data.channels.map((channel) => <div key={channel.channel} className="space-y-1 p-2"><div className="flex flex-wrap justify-between gap-2"><span>{t(`deepDiscovery.channel.${channel.channel}`, { defaultValue: channel.channel ?? '—' })}</span><span>{t(`deepDiscovery.state.${channel.state}`, { defaultValue: channel.state ?? '—' })}</span></div><p className="text-xs text-muted-foreground">{t('deepDiscovery.completedShards')}: {channel.completedShards ?? '—'} · {t('deepDiscovery.pendingShards')}: {channel.pendingShards ?? '—'} · {t('deepDiscovery.gapShards')}: {channel.gapShards ?? '—'} · {t('deepDiscovery.lastProgress')}: {formatLocalDateTime(channel.lastProgressAt, i18n.language)}</p></div>)}</div> : <p className="text-muted-foreground">{t('githubSync.empty')}</p>}
              {summary.data?.waits?.map((wait, index) => <p key={`${wait.reason}:${index}`} className="rounded border border-amber-500/30 p-2">{t(`deepDiscovery.reason.${wait.reason}`, { defaultValue: wait.reason ?? '—' })}{wait.retryAt ? ` · ${t('deepDiscovery.retryAt')}: ${formatLocalDateTime(wait.retryAt, i18n.language)}` : ''}</p>)}
            </div> : null}
          </div>
        </section>
        <section className="space-y-4 rounded border p-4">
          <div><h2 className="font-semibold">{t('githubSync.specifiedRepository')}</h2><p className="text-sm text-muted-foreground">{t('githubSync.specifiedRepositoryHelp')}</p></div>
          <form data-analytics-id="github.repository.add" onSubmit={addRepository} className="flex flex-wrap gap-2">
            <Input className="min-w-52 flex-1" aria-label={t('githubSync.repository')} placeholder="https://github.com/owner/repository" value={repository} onChange={(event) => setRepository(event.target.value)} required />
            <Button size="sm" type="submit" disabled={mutations.addRepository.isPending}>{t('githubSync.addRepository')}</Button>
          </form>
          {entries.error ? <p role="alert" className="text-sm text-destructive">{t('githubSync.loadFailed')}: {entries.error.message}</p> : null}
          {entries.isLoading ? <p className="text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
          {specified?.length ? <div className="divide-y rounded border text-sm">{specified.map((entry) => <div key={entry.id} className="space-y-2 p-3">
            <div className="flex flex-wrap items-center justify-between gap-2"><span className="break-all font-medium">{entry.ownerLogin}/{entry.repositoryName}</span><span className="sync-state" data-tone={entry.enabled ? 'teal' : 'amber'}>{entry.enabled ? t('githubSync.enabled') : t('githubSync.disabled')}</span></div>
            <p className="text-xs text-muted-foreground">{entry.repositoryId ? `#${entry.repositoryId}` : t('githubSync.unresolved')} · {t('githubSync.nextCheck')}: {entry.nextDueAt ? formatLocalDateTime(entry.nextDueAt, i18n.language) : '—'}</p>
            {entry.latestExecution ? <p className="text-xs text-muted-foreground">{t('githubSync.latestExecution')}: {entry.latestExecution.status ? t(`githubSync.codes.${entry.latestExecution.status}`, { defaultValue: entry.latestExecution.status }) : '—'}{entry.latestExecution.errorCode ? ` · ${t(`githubSync.codes.${entry.latestExecution.errorCode}`, { defaultValue: entry.latestExecution.errorCode })}` : ''}{entry.latestExecution.retryAt ? ` · ${t('githubSync.retryAt')}: ${formatLocalDateTime(entry.latestExecution.retryAt, i18n.language)}` : ''}</p> : null}
            <div className="flex flex-wrap gap-2">{entry.repositoryId ? <Button data-analytics-id="github.repository.open" size="sm" variant="outline" onClick={() => onRepository(entry.repositoryId!)}>{t('githubSync.viewRepository')}</Button> : null}<Button data-analytics-id="github.repository.toggle" size="sm" variant="outline" disabled={!entry.id || mutations.setEnabled.isPending} onClick={() => entry.id && void setEnabled(entry.id, !entry.enabled)}>{entry.enabled ? t('githubSync.disable') : t('githubSync.enable')}</Button></div>
          </div>)}</div> : !entries.isLoading && !entries.error ? <p className="text-sm text-muted-foreground">{t('githubSync.noSpecifiedRepositories')}</p> : null}
        </section>
        <section className="rounded border p-4">
          <button data-analytics-id="github.acquisition.toggle" type="button" className="flex w-full items-center justify-between text-left font-semibold" aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>{t('githubSync.otherAcquisition')}<span aria-hidden="true">{expanded ? '−' : '+'}</span></button>
          {expanded ? <div className="mt-4"><GithubDiscoveryConfig layout="other" /></div> : <p className="mt-1 text-sm text-muted-foreground">{t('githubSync.otherAcquisitionHelp')}</p>}
        </section>
        {progress.error ? <p role="alert" className="text-sm text-destructive">{t('githubSync.loadFailed')}: {progress.error.message}</p> : null}
      </div>
    </DialogContent>
  </Dialog>
}
