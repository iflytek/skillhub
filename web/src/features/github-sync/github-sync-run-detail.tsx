import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ArrowLeft } from 'lucide-react'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { Pagination } from '@/shared/components/pagination'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { githubSyncStatusLabel } from './status-label'
import { useGithubSyncRunDetail, useGithubSyncRunPaths, useGithubSyncRunTasks } from './use-github-sync-admin'

const PAGE_SIZE = 20

export function GithubSyncRunDetail({ runId, onBack, visible = true }: { runId: string; onBack: () => void; visible?: boolean }) {
  const { t, i18n } = useTranslation()
  const [taskPage, setTaskPage] = useState(0)
  const [pathPage, setPathPage] = useState(0)
  const run = useGithubSyncRunDetail(runId, visible)
  const tasks = useGithubSyncRunTasks(runId, taskPage, PAGE_SIZE, visible)
  const paths = useGithubSyncRunPaths(runId, pathPage, PAGE_SIZE, visible)
  const date = (value?: string | null) => formatLocalDateTime(value ?? undefined, i18n.language)

  return <div className="space-y-5 animate-fade-up">
    <div className="sync-hero space-y-3">
      <Button data-analytics-id="github.runs.open" size="sm" variant="ghost" className="sync-action -ml-2" onClick={onBack}><ArrowLeft className="mr-2 h-4 w-4" />{t('githubSync.runDetail.back')}</Button>
      <h1 className="text-2xl font-semibold tracking-tight">{t('githubSync.runDetail.title')}</h1>
      <p className="break-all font-mono text-xs" style={{ color: 'var(--sync-subtle)' }}>{runId}</p>
    </div>
    {run.isLoading ? <p className="text-sm text-muted-foreground">{t('githubSync.runDetail.loading')}</p> : null}
    {run.error ? <p role="alert" className="text-sm text-destructive">{t('githubSync.runDetail.loadFailed')}: {run.error.message}</p> : null}
    {run.data ? <>
      <Card className="sync-section grid gap-0 divide-y p-0 sm:grid-cols-2 sm:divide-y-0 lg:grid-cols-4">
        <div className="p-4"><p className="text-xs" style={{ color: 'var(--sync-subtle)' }}>{t('githubSync.runKind')}</p><p className="mt-1 font-medium">{githubSyncStatusLabel(t, run.data.kind)}</p></div>
        <div className="p-4"><p className="text-xs" style={{ color: 'var(--sync-subtle)' }}>{t('githubSync.sourceState')}</p><p className="mt-1"><span className="sync-state" data-tone={run.data.status === 'FAILED' ? 'red' : run.data.status === 'ACQUISITION_COMPLETE' ? 'teal' : 'amber'}>{githubSyncStatusLabel(t, run.data.status)}</span></p></div>
        <div className="p-4"><p className="text-xs" style={{ color: 'var(--sync-subtle)' }}>{t('githubSync.createdAt')}</p><p className="mt-1 font-medium">{date(run.data.createdAt)}</p></div>
        <div className="p-4"><p className="text-xs" style={{ color: 'var(--sync-subtle)' }}>{t('githubSync.retryAt')}</p><p className="mt-1 font-medium">{date(run.data.nextRetryAt)}</p></div>
      </Card>
      <Card className="sync-section overflow-hidden">
        <div className="sync-section-header"><h2 className="font-semibold">{t('githubSync.packageStages')}</h2></div>
        {run.data.lastErrorCode ? <p className="sync-rail mx-4 mt-4 px-3 py-2 text-sm" data-tone="red">{githubSyncStatusLabel(t, run.data.lastErrorCode)}</p> : null}
        <dl className="grid grid-cols-2 text-sm sm:grid-cols-4">
          <div className="border-t p-3"><dt className="text-muted-foreground">{t('githubSync.runDetail.taskCount')}</dt><dd className="mt-1 font-semibold tabular-nums">{run.data.taskCount}</dd></div>
          <div className="border-t p-3"><dt className="text-muted-foreground">{t('githubSync.runDetail.pathCount')}</dt><dd className="mt-1 font-semibold tabular-nums">{run.data.pathCount}</dd></div>
          <div className="border-t p-3"><dt className="text-muted-foreground">{t('githubSync.received')}</dt><dd className="mt-1 font-semibold tabular-nums">{run.data.receivedPackages}</dd></div>
          <div className="border-t p-3"><dt className="text-muted-foreground">{t('githubSync.processing')}</dt><dd className="mt-1 font-semibold tabular-nums">{run.data.processingPackages}</dd></div>
          <div className="border-t p-3"><dt className="text-muted-foreground">{t('githubSync.pendingReview')}</dt><dd className="mt-1 font-semibold tabular-nums" style={{ color: 'var(--sync-amber)' }}>{run.data.pendingReviewPackages}</dd></div>
          <div className="border-t p-3"><dt className="text-muted-foreground">{t('githubSync.blocked')}</dt><dd className="mt-1 font-semibold tabular-nums" style={{ color: 'var(--sync-amber)' }}>{run.data.blockedPackages}</dd></div>
          <div className="border-t p-3"><dt className="text-muted-foreground">{t('githubSync.failed')}</dt><dd className="mt-1 font-semibold tabular-nums" style={{ color: 'var(--sync-red)' }}>{run.data.failedPackages}</dd></div>
        </dl>
      </Card>
      <div className="min-w-0 space-y-4">
          <Card className="sync-section overflow-hidden">
            <div className="sync-section-header"><h2 className="font-semibold">{t('githubSync.runDetail.skillPaths')}</h2><p className="mt-1 text-sm" style={{ color: 'var(--sync-subtle)' }}>{t('githubSync.runDetail.pathCount')}: {run.data.pathCount}</p></div>
            {paths.isLoading ? <p className="p-5 text-sm text-muted-foreground">{t('githubSync.runDetail.loading')}</p> : null}
            {paths.error ? <p role="alert" className="p-5 text-sm text-destructive">{t('githubSync.runDetail.loadFailed')}: {paths.error.message}</p> : null}
            {!paths.isLoading && !paths.error && !paths.data?.items?.length ? <p className="p-5 text-sm text-muted-foreground">{run.data.kind === 'DISCOVERY' ? t('githubSync.runDetail.discoveryNoPaths') : t('githubSync.runDetail.noPaths')}</p> : null}
            {paths.data?.items?.length ? <div className="overflow-x-auto"><Table className="min-w-[720px]">
              <TableHeader><TableRow><TableHead>{t('githubSync.runDetail.repository')}</TableHead><TableHead>{t('githubSync.path')}</TableHead><TableHead>{t('githubSync.runDetail.pathOutcome')}</TableHead><TableHead>{t('githubSync.runDetail.ingestionStatus')}</TableHead><TableHead>{t('githubSync.runDetail.reviewStatus')}</TableHead></TableRow></TableHeader>
              <TableBody>{paths.data.items.map((path) => <TableRow key={`${path.taskId}:${path.commitSha}:${path.skillPath}`}>
                <TableCell className="text-sm">{path.repository ?? '—'}</TableCell>
                <TableCell><div className="break-all font-medium">{path.skillPath}</div><div className="font-mono text-xs text-muted-foreground">{path.commitSha?.slice(0, 12)}</div></TableCell>
                <TableCell>{githubSyncStatusLabel(t, path.outcome)}{path.reason ? <div className="mt-1 text-xs text-muted-foreground">{githubSyncStatusLabel(t, path.reason)}</div> : null}{path.retryAt ? <div className="mt-1 text-xs text-muted-foreground">{t('githubSync.retryAt')}: {date(path.retryAt)}</div> : null}</TableCell>
                <TableCell>{githubSyncStatusLabel(t, path.ingestionStatus ?? undefined)}{path.ingestionErrorCode ? <div className="mt-1 text-xs text-destructive">{githubSyncStatusLabel(t, path.ingestionErrorCode)}</div> : null}</TableCell>
                <TableCell>{githubSyncStatusLabel(t, path.reviewStatus ?? undefined)}</TableCell>
              </TableRow>)}</TableBody>
            </Table></div> : null}
          </Card>
          {paths.data && (paths.data.total ?? 0) > PAGE_SIZE ? <Pagination analyticsId="github.path.paginate" page={pathPage} totalPages={Math.ceil((paths.data.total ?? 0) / PAGE_SIZE)} onPageChange={setPathPage} /> : null}
          <Card className="sync-section overflow-hidden">
            <div className="sync-section-header"><h2 className="font-semibold">{t('githubSync.runDetail.tasks')}</h2><p className="mt-1 text-sm" style={{ color: 'var(--sync-subtle)' }}>{t('githubSync.runDetail.taskCount')}: {run.data.taskCount}</p></div>
            {tasks.isLoading ? <p className="p-5 text-sm text-muted-foreground">{t('githubSync.runDetail.loading')}</p> : null}
            {tasks.error ? <p role="alert" className="p-5 text-sm text-destructive">{t('githubSync.runDetail.loadFailed')}: {tasks.error.message}</p> : null}
            {!tasks.isLoading && !tasks.error && !tasks.data?.items?.length ? <p className="p-5 text-sm text-muted-foreground">{t('githubSync.runDetail.noTasks')}</p> : null}
            {tasks.data?.items?.length ? <div className="overflow-x-auto"><Table className="min-w-[720px]">
              <TableHeader><TableRow><TableHead>{t('githubSync.runDetail.repository')}</TableHead><TableHead>{t('githubSync.runDetail.taskKind')}</TableHead><TableHead>{t('githubSync.runDetail.taskMode')}</TableHead><TableHead>{t('githubSync.runDetail.taskStatus')}</TableHead><TableHead>{t('githubSync.runDetail.retryAt')}</TableHead></TableRow></TableHeader>
              <TableBody>{tasks.data.items.map((task) => <TableRow key={task.id}>
                <TableCell><span className="font-medium">{task.repository ?? '—'}</span><div className="font-mono text-xs text-muted-foreground">{task.id.slice(0, 8)}</div></TableCell>
                <TableCell>{githubSyncStatusLabel(t, task.kind)}</TableCell><TableCell>{githubSyncStatusLabel(t, task.mode ?? undefined)}</TableCell>
                <TableCell>{githubSyncStatusLabel(t, task.status)}{task.lastErrorCode ? <div className="mt-1 text-xs text-destructive">{githubSyncStatusLabel(t, task.lastErrorCode)}</div> : null}</TableCell>
                <TableCell>{date(task.retryAt)}</TableCell>
              </TableRow>)}</TableBody>
            </Table></div> : null}
          </Card>
          {tasks.data && (tasks.data.total ?? 0) > PAGE_SIZE ? <Pagination analyticsId="github.task.paginate" page={taskPage} totalPages={Math.ceil((tasks.data.total ?? 0) / PAGE_SIZE)} onPageChange={setTaskPage} /> : null}
      </div>
    </> : null}
  </div>
}
