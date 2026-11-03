import { useState, type FormEvent } from 'react'
import { useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { toast } from '@/shared/lib/toast'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { githubSyncStatusLabel } from '@/features/github-sync/status-label'
import { GithubSourceIgnoreEditor } from '@/features/github-sync/github-source-ignore-editor'
import { GithubSyncConflictBinder } from '@/features/github-sync/github-sync-conflict-binder'
import { GithubSyncPathIdentity } from '@/features/github-sync/github-sync-path-identity'
import { GithubSourceRescanButton } from '@/features/github-sync/github-source-rescan-button'
import { GithubRetryRejectedSkillButton } from '@/features/github-sync/github-retry-rejected-skill-button'
import { GithubSyncRunList } from '@/features/github-sync/github-sync-run-list'
import {
  useGithubTeamEntries, useGithubTeamResolved, useGithubTeamRuns,
  useGithubTeamSkillPaths, useGithubTeamSourceMutations,
} from '@/features/github-sync/use-github-team-sources'

const PAGE_SIZE = 20

export function NamespaceGithubSourcesPage() {
  const { slug } = useParams({ from: '/dashboard/namespaces/$slug/github-sources' })
  const { t, i18n } = useTranslation()
  const [repository, setRepository] = useState('')
  const [visibility, setVisibility] = useState('NAMESPACE_ONLY')
  const [page, setPage] = useState(0)
  const [runPage, setRunPage] = useState(0)
  const [skillPage, setSkillPage] = useState(0)
  const [selectedSource, setSelectedSource] = useState<number | null>(null)
  const entries = useGithubTeamEntries(slug)
  const resolved = useGithubTeamResolved(slug, page, PAGE_SIZE)
  const runs = useGithubTeamRuns(slug, runPage, PAGE_SIZE)
  const skills = useGithubTeamSkillPaths(slug, selectedSource, skillPage, PAGE_SIZE)
  const mutations = useGithubTeamSourceMutations(slug)

  const create = async (event: FormEvent) => {
    event.preventDefault()
    const parts = repository.trim().split('/')
    if (parts.length !== 2 || !parts[0] || !parts[1]) {
      toast.error(t('githubSync.repositoryFormat'))
      return
    }
    try {
      await mutations.create.mutateAsync({ ownerLogin: parts[0], repositoryName: parts[1], visibility })
      setRepository('')
      toast.success(t('githubSync.saved'))
    } catch (error) {
      toast.error(t('githubSync.saveFailed'), error instanceof Error ? error.message : '')
    }
  }

  const changeEnabled = async (entryId: string, enabled: boolean) => {
    try {
      await mutations.setEnabled.mutateAsync({ entryId, enabled })
      toast.success(t('githubSync.saved'))
    } catch (error) {
      toast.error(t('githubSync.saveFailed'), error instanceof Error ? error.message : '')
    }
  }

  const changeVisibility = async (entryId: string, nextVisibility: string) => {
    try {
      await mutations.setVisibility.mutateAsync({ entryId, visibility: nextVisibility })
      toast.success(t('githubSync.saved'))
    } catch (error) {
      toast.error(t('githubSync.saveFailed'), error instanceof Error ? error.message : '')
    }
  }

  return (
    <div className="space-y-6 animate-fade-up">
      <DashboardPageHeader title={t('githubSync.teamTitle', { slug })} subtitle={t('githubSync.teamSubtitle')} />
      <Card className="p-5 space-y-3">
        <h2 className="text-lg font-semibold">{t('githubSync.addRepository')}</h2>
        <form data-analytics-id="github.namespace.create" onSubmit={create} className="flex flex-wrap items-end gap-3">
          <label className="min-w-52 flex-1 text-sm">{t('githubSync.repository')}
            <Input placeholder="owner/repository" value={repository} onChange={(event) => setRepository(event.target.value)} required />
          </label>
          <label className="text-sm">{t('githubSync.visibility')}
            <select className="block h-9 rounded-md border bg-background px-2" value={visibility} onChange={(event) => setVisibility(event.target.value)}>
              <option data-analytics-id="github.namespace.create.visibility.namespace" value="NAMESPACE_ONLY">{t('githubSync.namespaceOnly')}</option>
              <option data-analytics-id="github.namespace.create.visibility.public" value="PUBLIC">{t('githubSync.public')}</option>
            </select>
          </label>
          <Button type="submit" disabled={mutations.create.isPending}>{t('githubSync.add')}</Button>
        </form>
        <p className="text-xs text-muted-foreground">{t('githubSync.visibilityHint')}</p>
      </Card>

      <Card>
        {entries.isLoading ? <p className="p-6 text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
        {entries.error ? <p role="alert" className="p-6 text-sm text-destructive">{t('githubSync.loadFailed')}: {entries.error.message}</p> : null}
        {entries.data?.length === 0 ? <p className="p-6 text-sm text-muted-foreground">{t('githubSync.empty')}</p> : null}
        {entries.data?.length ? (
          <Table>
            <TableHeader><TableRow>
              <TableHead>{t('githubSync.repository')}</TableHead><TableHead>{t('githubSync.repositoryId')}</TableHead>
              <TableHead>{t('githubSync.sourceState')}</TableHead><TableHead>{t('githubSync.visibility')}</TableHead>
              <TableHead>{t('githubSync.details')}</TableHead>
            </TableRow></TableHeader>
            <TableBody>{entries.data.map((entry) => (
              <TableRow key={entry.id}>
                <TableCell className="break-all font-medium">{entry.ownerLogin}/{entry.repositoryName}</TableCell>
                <TableCell>{entry.repositoryId ?? t('githubSync.unresolved')}</TableCell>
                <TableCell>{entry.enabled ? t('githubSync.active') : t('githubSync.paused')}</TableCell>
                <TableCell>
                  <select aria-label={t('githubSync.visibility')} className="h-9 rounded-md border bg-background px-2" value={entry.sourceVisibility ?? 'NAMESPACE_ONLY'} disabled={!entry.id || mutations.setVisibility.isPending} onChange={(event) => entry.id && changeVisibility(entry.id, event.target.value)}>
                    <option data-analytics-id="github.namespace.visibility.namespace" value="NAMESPACE_ONLY">{t('githubSync.namespaceOnly')}</option><option data-analytics-id="github.namespace.visibility.public" value="PUBLIC">{t('githubSync.public')}</option>
                  </select>
                </TableCell>
                <TableCell><Button data-analytics-id={entry.enabled ? 'github.namespace.disable' : 'github.namespace.enable'} size="sm" variant="outline" disabled={!entry.id || mutations.setEnabled.isPending} onClick={() => entry.id && changeEnabled(entry.id, !entry.enabled)}>{entry.enabled ? t('githubSync.disable') : t('githubSync.enable')}</Button></TableCell>
              </TableRow>
            ))}</TableBody>
          </Table>
        ) : null}
      </Card>

      <Card>
        <h2 className="p-5 text-lg font-semibold">{t('githubSync.sources')}</h2>
        {resolved.isLoading ? <p className="p-6 text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
        {resolved.error ? <p role="alert" className="p-6 text-sm text-destructive">{t('githubSync.loadFailed')}: {resolved.error.message}</p> : null}
        {resolved.data?.items?.length === 0 ? <p className="p-6 text-sm text-muted-foreground">{t('githubSync.empty')}</p> : null}
        {resolved.data?.items?.length ? (
          <Table className="[&_td]:px-3 [&_th]:px-3">
            <TableHeader><TableRow><TableHead>{t('githubSync.repository')}</TableHead><TableHead>{t('githubSync.sourceState')}</TableHead><TableHead>{t('githubSync.packageStages')}</TableHead><TableHead>{t('githubSync.nextCheck')}</TableHead><TableHead>{t('githubSync.details')}</TableHead></TableRow></TableHeader>
            <TableBody>{resolved.data.items.map((source) => (
              <TableRow key={source.id}>
                <TableCell className="min-w-44 break-all">{source.ownerLogin}/{source.repositoryName}</TableCell>
                <TableCell className="min-w-40">{githubSyncStatusLabel(t, source.status)}{source.lastErrorCode ? <div className="text-xs text-destructive">{githubSyncStatusLabel(t, source.lastErrorCode)}</div> : null}{source.latestTaskErrorCode ? <div className="text-xs text-destructive">{githubSyncStatusLabel(t, source.latestTaskErrorCode)}{source.latestTaskErrorDetail ? ` · ${source.latestTaskErrorDetail}` : ''}</div> : null}{source.latestTaskRetryAt ? <div className="text-xs text-muted-foreground">{t('githubSync.retryAt')}: {formatLocalDateTime(source.latestTaskRetryAt, i18n.language)}</div> : null}</TableCell>
                <TableCell className="min-w-52"><div className="grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
                  <span>{t('githubSync.received')}: <strong>{source.receivedPackages ?? 0}</strong></span>
                  <span>{t('githubSync.processing')}: <strong>{source.processingPackages ?? 0}</strong></span>
                  <span>{t('githubSync.pendingReview')}: <strong>{source.pendingReviewPackages ?? 0}</strong></span>
                  <span>{t('githubSync.blocked')}: <strong>{source.blockedPackages ?? 0}</strong></span>
                  <span>{t('githubSync.failed')}: <strong>{source.failedPackages ?? 0}</strong></span>
                </div></TableCell>
                <TableCell>{formatLocalDateTime(source.nextCheckAt, i18n.language)}</TableCell>
                <TableCell className="space-y-2"><Button data-analytics-id="github.source.skills" size="sm" variant="outline" onClick={() => { setSelectedSource(source.id ?? null); setSkillPage(0) }}>{t('githubSync.viewSkills')}</Button>{source.id != null ? <GithubSourceRescanButton sourceId={source.id} status={source.status} namespaceSlug={slug} /> : null}</TableCell>
              </TableRow>
            ))}</TableBody>
          </Table>
        ) : null}
        {resolved.data && Math.ceil((resolved.data.total ?? 0) / (resolved.data.size || PAGE_SIZE)) > 1 ? (
          <Pagination analyticsId="github.namespace.paginate" page={page} totalPages={Math.ceil((resolved.data.total ?? 0) / (resolved.data.size || PAGE_SIZE))} onPageChange={setPage} />
        ) : null}
      </Card>
      <section className="space-y-3" aria-label={t('githubSync.runs')}>
        <h2 className="text-lg font-semibold">{t('githubSync.runs')}</h2>
        <GithubSyncRunList data={runs.data} loading={runs.isLoading} error={runs.error}
          page={runPage} onPageChange={setRunPage} />
      </section>
      {selectedSource !== null ? (
        <Card className="p-5 space-y-3">
          <div className="flex items-center justify-between"><h2 className="text-lg font-semibold">{t('githubSync.skillPaths')} · #{selectedSource}</h2><Button data-analytics-id="github.namespace.skills.close" size="sm" variant="ghost" onClick={() => setSelectedSource(null)}>{t('githubSync.close')}</Button></div>
          <GithubSourceIgnoreEditor sourceId={selectedSource} namespaceSlug={slug} />
          {skills.isLoading ? <p className="text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
          {skills.error ? <p role="alert" className="text-sm text-destructive">{t('githubSync.loadFailed')}: {skills.error.message}</p> : null}
          {skills.data?.items?.length === 0 ? <p className="text-sm text-muted-foreground">{t('githubSync.empty')}</p> : null}
          {skills.data?.items?.length ? (
            <Table><TableHeader><TableRow><TableHead>{t('githubSync.path')}</TableHead><TableHead>{t('githubSync.acquisition')}</TableHead><TableHead>{t('githubSync.currentRule')}</TableHead><TableHead>{t('githubSync.ingestion')}</TableHead><TableHead>{t('githubSync.review')}</TableHead><TableHead>{t('githubSync.retryAt')}</TableHead></TableRow></TableHeader>
              <TableBody>{skills.data.items.map((skill) => <TableRow key={skill.skillPath}><TableCell><GithubSyncPathIdentity path={skill} /></TableCell><TableCell>{githubSyncStatusLabel(t, skill.acquisitionOutcome)}{skill.acquisitionReason ? <div className="text-xs text-muted-foreground">{githubSyncStatusLabel(t, skill.acquisitionReason)}</div> : null}</TableCell><TableCell>{skill.ignoredByCurrentPolicy ? githubSyncStatusLabel(t, 'IGNORED') : '—'}</TableCell><TableCell>{githubSyncStatusLabel(t, skill.ingestionStatus)}{skill.ingestionErrorCode ? <div className="text-xs text-destructive">{githubSyncStatusLabel(t, skill.ingestionErrorCode)}</div> : null}<GithubSyncConflictBinder path={skill} namespaceSlug={slug} /></TableCell><TableCell>{githubSyncStatusLabel(t, skill.reviewStatus)}<GithubRetryRejectedSkillButton sourceId={selectedSource} path={skill} namespaceSlug={slug} /></TableCell><TableCell>{formatLocalDateTime(skill.acquisitionRetryAt, i18n.language)}</TableCell></TableRow>)}</TableBody>
            </Table>
          ) : null}
          {skills.data && Math.ceil((skills.data.total ?? 0) / (skills.data.size || PAGE_SIZE)) > 1 ? (
            <Pagination analyticsId="github.source.paginate" page={skillPage} totalPages={Math.ceil((skills.data.total ?? 0) / (skills.data.size || PAGE_SIZE))} onPageChange={setSkillPage} />
          ) : null}
        </Card>
      ) : null}
    </div>
  )
}
