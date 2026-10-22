import { useTranslation } from 'react-i18next'
import type { GithubSyncRun } from '@/api/client'
import { Pagination } from '@/shared/components/pagination'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { githubSyncStatusLabel } from './status-label'

type RunPage = { items?: GithubSyncRun[]; total?: number; size?: number }

export function GithubSyncRunList({ data, loading, error, page, onPageChange, onSelectRun }: {
  data?: RunPage
  loading: boolean
  error: Error | null
  page: number
  onPageChange: (page: number) => void
  onSelectRun?: (id: string) => void
}) {
  const { t, i18n } = useTranslation()
  const totalPages = Math.ceil((data?.total ?? 0) / (data?.size || 20))

  return <>
    <Card className="sync-section overflow-x-auto">
      <div className="sync-section-header">
        <h2 className="text-lg font-semibold">{t('githubSync.runs')}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{t('githubSync.runsHelp')}</p>
      </div>
      {loading ? <p className="p-6 text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
      {error ? <p role="alert" className="p-6 text-sm text-destructive">{t('githubSync.loadFailed')}: {error.message}</p> : null}
      {!loading && !error && !data?.items?.length ? <p className="p-6 text-sm text-muted-foreground">{t('githubSync.empty')}</p> : null}
      {data?.items?.length ? (
        <Table className="min-w-[920px] [&_td]:px-3 [&_th]:px-3">
          <TableHeader><TableRow>
            <TableHead>{t('githubSync.runId')}</TableHead><TableHead>{t('githubSync.runKind')}</TableHead>
            <TableHead>{t('githubSync.runTarget')}</TableHead>
            <TableHead>{t('githubSync.sourceState')}</TableHead><TableHead>{t('githubSync.tasks')}</TableHead>
            <TableHead>{t('githubSync.packageStages')}</TableHead>
            <TableHead>{t('githubSync.retryAt')}</TableHead><TableHead>{t('githubSync.createdAt')}</TableHead>
            {onSelectRun ? <TableHead>{t('githubSync.details')}</TableHead> : null}
          </TableRow></TableHeader>
          <TableBody>{data.items.map((run) => (
            <TableRow key={run.id}>
              <TableCell className="font-mono text-xs">{run.id?.slice(0, 8) ?? '—'}</TableCell>
              <TableCell>{githubSyncStatusLabel(t, run.kind)}</TableCell>
              <TableCell className="max-w-48 break-all text-sm">{run.targetCount && run.targetCount > 1
                ? t('githubSync.runMultipleTargets', { count: run.targetCount })
                : run.target?.value || '—'}</TableCell>
              <TableCell className="min-w-40"><span className="sync-state" data-tone={run.status === 'FAILED' ? 'red' : run.status === 'ACQUISITION_COMPLETE' ? 'teal' : 'amber'}>{githubSyncStatusLabel(t, run.status)}</span>{run.lastErrorCode ? <div className="mt-1 text-xs text-destructive">{t('githubSync.failureReason')} {githubSyncStatusLabel(t, run.lastErrorCode)}</div> : null}</TableCell>
              <TableCell>{run.taskCount ?? 0}</TableCell>
              <TableCell className="min-w-52"><div className="grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
                <span>{t('githubSync.received')}: <strong>{run.receivedPackages ?? 0}</strong></span>
                <span>{t('githubSync.processing')}: <strong>{run.processingPackages ?? 0}</strong></span>
                <span>{t('githubSync.pendingReview')}: <strong>{run.pendingReviewPackages ?? 0}</strong></span>
                <span>{t('githubSync.blocked')}: <strong>{run.blockedPackages ?? 0}</strong></span>
                <span>{t('githubSync.failed')}: <strong>{run.failedPackages ?? 0}</strong></span>
              </div></TableCell>
              <TableCell>{formatLocalDateTime(run.nextRetryAt, i18n.language)}</TableCell>
              <TableCell>{formatLocalDateTime(run.createdAt, i18n.language)}</TableCell>
              {onSelectRun ? <TableCell>{run.id ? <Button data-analytics-id="github.run.open" size="sm" className="sync-action" variant="outline" onClick={() => onSelectRun(run.id!)}>{t('githubSync.viewRun')}</Button> : null}</TableCell> : null}
            </TableRow>
          ))}</TableBody>
        </Table>
      ) : null}
    </Card>
    {data && totalPages > 1 ? <Pagination analyticsId="github.run.paginate" page={page} totalPages={totalPages} onPageChange={onPageChange} /> : null}
  </>
}
