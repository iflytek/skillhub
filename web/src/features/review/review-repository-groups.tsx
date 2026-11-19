import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, ArrowRight, GitCommitHorizontal, GitFork } from 'lucide-react'
import { reviewApi } from '@/api/client'
import type { ReviewTask } from '@/api/types'
import type { ReviewCenterFilters } from './review-center-filters'
import type { useBatchReview } from './batch-review'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Card } from '@/shared/ui/card'
import { Pagination } from '@/shared/components/pagination'
import { formatLocalDateTime } from '@/shared/lib/date-time'

interface Props {
  filters: ReviewCenterFilters
  namespaceId?: number
  enabled: boolean
  page: number
  snapshotId?: number
  memberQuery?: string
  onMemberQuery?: (query: string) => void
  batch: ReturnType<typeof useBatchReview>
  onGroup: (id?: number) => void
  onPage: (page: number) => void
  onReady?: () => void
  onReview: (task: ReviewTask) => void
}

export function ReviewRepositoryGroups({ filters, namespaceId, enabled, page, snapshotId, batch, onGroup, onPage, onReview, memberQuery = '', onMemberQuery, onReady }: Props) {
  const { t, i18n } = useTranslation()
  const [memberDraft, setMemberDraft] = useState(memberQuery)
  useEffect(() => setMemberDraft(memberQuery), [memberQuery])
  const params = { status: filters.status, namespaceId, q: snapshotId ? memberQuery : filters.q, reviewKind: filters.reviewKind, sortDirection: filters.sortDirection, page, size: 20 }
  const query = useQuery<Awaited<ReturnType<typeof reviewApi.syncGroups>> | Awaited<ReturnType<typeof reviewApi.syncGroupMembers>>>({
    queryKey: ['reviews', 'repository-groups', params, snapshotId],
    queryFn: () => snapshotId ? reviewApi.syncGroupMembers(snapshotId, params) : reviewApi.syncGroups(params),
    enabled,
  })
  const context = useQuery({ queryKey: ['reviews', 'repository-group', snapshotId],
    queryFn: () => reviewApi.syncGroup(snapshotId!), enabled: enabled && !!snapshotId, retry: false })
  useEffect(() => { if (enabled && query.isSuccess) onReady?.() }, [enabled, query.isSuccess, query.data, onReady])
  if (!enabled) return null
  const groups = snapshotId ? undefined : query.data as Awaited<ReturnType<typeof reviewApi.syncGroups>> | undefined
  const members = snapshotId ? query.data as Awaited<ReturnType<typeof reviewApi.syncGroupMembers>> | undefined : undefined
  return <section className="space-y-4 [&_button]:min-h-11 sm:[&_button]:min-h-8" aria-busy={query.isFetching} aria-label={t('reviewGroups.title')}>
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h2 id="review-results" tabIndex={-1} className="text-lg font-semibold">{t(snapshotId ? 'reviewGroups.members' : 'reviewGroups.title')}</h2>
      {snapshotId && <Button variant="outline" disabled={batch.busy} onClick={() => onGroup()}><ArrowLeft className="mr-2 h-4 w-4" />{t('reviewGroups.back')}</Button>}
    </div>
    {snapshotId && context.data && <Card className="space-y-2 bg-secondary/50 p-5"><h3 className="break-all font-semibold">{context.data.owner}/{context.data.repository}</h3><p className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground"><span>@{context.data.namespace}</span><GitCommitHorizontal aria-hidden="true" className="h-4 w-4" /><code className="break-all">{context.data.commitSha}</code><span>{formatLocalDateTime(context.data.capturedAt, i18n.language)}</span></p></Card>}
    {snapshotId && context.isError && <p role="alert" className="text-sm text-destructive">{t('reviewCenter.loadFailed')}</p>}
    {snapshotId && <form className="flex flex-wrap gap-3" onSubmit={event => { event.preventDefault(); onMemberQuery?.(memberDraft.trim()) }}><Input aria-label={t('reviewGroups.searchMembers')} placeholder={t('reviewGroups.searchMembers')} maxLength={200} value={memberDraft} onChange={event => setMemberDraft(event.target.value)} className="h-11 min-w-0 flex-1 sm:h-8 sm:max-w-sm" /><Button type="submit" disabled={batch.busy}>{t('reviewCenter.filters.apply')}</Button></form>}
    {query.isLoading ? <div className="h-40 animate-shimmer rounded-lg" aria-label={t('reviewCenter.loading')} />
      : query.isError ? <Card className="space-y-3 p-6" role="alert"><p>{t('reviewCenter.loadFailed')}</p><Button variant="outline" onClick={() => void query.refetch()}>{t('reviewCenter.retry')}</Button></Card>
        : !query.data?.items.length ? <Card className="space-y-3 p-8 text-center"><p>{t('reviewGroups.empty')}</p><p className="text-sm text-muted-foreground">{t('reviewGroups.historyHint')}</p>{page > 0 && <Button variant="outline" onClick={() => onPage(0)}>{t('reviewCenter.firstPage')}</Button>}</Card>
          : <>
            {groups?.items.map(group => <Card key={group.snapshotId} className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0 space-y-2"><h3 className="flex items-start gap-2 font-semibold"><GitFork aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" /><span className="break-all">{group.owner}/{group.repository}</span></h3>
                <p className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground"><span>@{group.namespace}</span><GitCommitHorizontal aria-hidden="true" className="h-4 w-4" /><code>{group.commitSha.slice(0,12)}</code><span>{formatLocalDateTime(group.capturedAt, i18n.language)}</span></p>
                <p className="text-sm text-muted-foreground">{t('reviewGroups.summary', { total: group.total, passed: group.scanPassed, waiting: group.scanWaiting, blocked: group.scanBlocked })}</p>
              </div>
              <Button className="min-h-11 shrink-0 sm:min-h-8" onClick={() => onGroup(group.snapshotId)}>{t('reviewGroups.open')}<ArrowRight className="ml-2 h-4 w-4" /></Button>
            </Card>)}
            {members && <>
              <p className="text-sm text-muted-foreground">{t('reviewGroups.decisionHint')}</p>
              <div className="flex flex-wrap gap-2"><Button variant="outline" disabled={batch.busy} onClick={() => batch.selectPage(members.items.map(item => item.review))}>{t('reviewBatch.selectPage')}</Button><Button variant="outline" disabled={batch.busy || !members.items.some(item => item.scanStatus === 'PASSED' && item.review.status === 'PENDING')} onClick={() => batch.selectPage(members.items.filter(item => item.scanStatus === 'PASSED').map(item => item.review))}>{t('reviewGroups.selectPassed')}</Button></div>
              <div className="divide-y rounded-lg border">{members.items.map(({ review: task, scanStatus }) => <div key={task.id} className="flex items-center gap-3 p-4">
                <label className="flex min-h-11 min-w-11 items-center justify-center">{batch.checkbox(task)}</label>
                <div className="min-w-0 flex-1"><p className="break-words font-medium">{task.displayName || task.skillSlug}</p><p className="break-all text-xs text-muted-foreground">@{task.namespace}/{task.skillSlug} · {task.version} · {task.reviewKind && t(`reviewCenter.kind.${task.reviewKind}`)} · {t(`reviewCenter.status.${task.status}`)}</p></div>
                <span className={`rounded-md px-2 py-1 text-xs ${scanStatus === 'PASSED' ? 'bg-success/10 text-success' : scanStatus === 'BLOCKED' ? 'bg-destructive/10 text-destructive' : 'bg-warning/10 text-warning'}`}>{t(`reviewGroups.scan${scanStatus}`)}</span><Button variant="outline" onClick={() => onReview(task)}>{t('reviewCenter.open')}</Button>
              </div>)}</div>
            </>}
            <Pagination analyticsId="review.groups.paginate" page={page} totalPages={Math.max(1,Math.ceil(query.data.total / query.data.size))} onPageChange={onPage} />
          </>}
  </section>
}
