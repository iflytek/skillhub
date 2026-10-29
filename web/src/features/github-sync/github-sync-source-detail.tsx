import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Pagination } from '@/shared/components/pagination'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { GithubRetryRejectedSkillButton } from './github-retry-rejected-skill-button'
import { GithubSourceIgnoreEditor } from './github-source-ignore-editor'
import { GithubSourceRescanButton } from './github-source-rescan-button'
import { GithubSyncConflictBinder } from './github-sync-conflict-binder'
import { GithubSyncPathIdentity } from './github-sync-path-identity'
import { githubSyncStatusLabel } from './status-label'
import { useGithubSyncSkillPaths } from './use-github-sync-admin'

const PAGE_SIZE = 20

export function GithubSyncSourceDetail({ sourceId, status, visible }: { sourceId: number; status?: string; visible: boolean }) {
  const { t, i18n } = useTranslation()
  const [page, setPage] = useState(0)
  const skills = useGithubSyncSkillPaths(sourceId, page, PAGE_SIZE, visible)
  const totalPages = Math.ceil((skills.data?.total ?? 0) / PAGE_SIZE)

  return <section className="space-y-4 border-t pt-4">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h3 className="font-semibold">{t('githubSync.skillPaths')} · #{sourceId}</h3>
      <GithubSourceRescanButton sourceId={sourceId} status={status} />
    </div>
    <GithubSourceIgnoreEditor sourceId={sourceId} />
    {skills.isLoading ? <p className="text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
    {skills.error ? <p role="alert" className="text-sm text-destructive">{t('githubSync.loadFailed')}: {skills.error.message}</p> : null}
    {!skills.isLoading && !skills.error && !skills.data?.items?.length ? <p className="text-sm text-muted-foreground">{t('githubSync.empty')}</p> : null}
    {skills.data?.items?.length ? <div className="overflow-x-auto"><Table className="min-w-[680px]">
      <TableHeader><TableRow><TableHead>{t('githubSync.path')}</TableHead><TableHead>{t('githubSync.acquisition')}</TableHead><TableHead>{t('githubSync.currentRule')}</TableHead><TableHead>{t('githubSync.ingestion')}</TableHead><TableHead>{t('githubSync.review')}</TableHead><TableHead>{t('githubSync.retryAt')}</TableHead></TableRow></TableHeader>
      <TableBody>{skills.data.items.map((skill) => <TableRow key={skill.skillPath}>
        <TableCell><GithubSyncPathIdentity path={skill} /></TableCell>
        <TableCell>{githubSyncStatusLabel(t, skill.acquisitionOutcome)}{skill.acquisitionReason ? <div className="text-xs text-muted-foreground">{githubSyncStatusLabel(t, skill.acquisitionReason)}</div> : null}</TableCell>
        <TableCell>{skill.ignoredByCurrentPolicy ? githubSyncStatusLabel(t, 'IGNORED') : '—'}</TableCell>
        <TableCell>{githubSyncStatusLabel(t, skill.ingestionStatus)}{skill.ingestionErrorCode ? <div className="text-xs text-destructive">{githubSyncStatusLabel(t, skill.ingestionErrorCode)}</div> : null}<GithubSyncConflictBinder path={skill} /></TableCell>
        <TableCell>{githubSyncStatusLabel(t, skill.reviewStatus)}<GithubRetryRejectedSkillButton sourceId={sourceId} path={skill} /></TableCell>
        <TableCell>{formatLocalDateTime(skill.acquisitionRetryAt, i18n.language)}</TableCell>
      </TableRow>)}</TableBody>
    </Table></div> : null}
    {skills.data && totalPages > 1 ? <Pagination analyticsId="github.source.paginate" page={page} totalPages={totalPages} onPageChange={setPage} /> : null}
  </section>
}
