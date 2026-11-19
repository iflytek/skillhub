import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Eye, GitCommitHorizontal, GitFork, Search } from 'lucide-react'
import { reviewApi } from '@/api/client'
import type { ReviewTask } from '@/api/types'
import { useAuth } from '@/features/auth/use-auth'
import { useBatchReview, isBatchReviewTask } from '@/features/review/batch-review'
import { parseReviewCenterFilters, REVIEW_STATUSES, type ReviewCenterStatus } from '@/features/review/review-center-filters'
import { ReviewResultsPanel } from '@/features/review/review-results-panel'
import { SkillAvatar } from '@/features/skill/skill-avatar'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { formatLocalDateTime } from '@/shared/lib/date-time'

/** One immutable acquisition snapshot, with its own URL, selection and return context. */
export function RepositoryReviewPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { snapshotId: rawId } = useParams({ from: '/dashboard/reviews/repositories/$snapshotId' })
  const search = useSearch({ from: '/dashboard/reviews/repositories/$snapshotId' })
  const snapshotId = Number(rawId)
  const validId = Number.isSafeInteger(snapshotId) && snapshotId > 0
  const { user } = useAuth()
  const client = useQueryClient()
  const filters = parseReviewCenterFilters(search)
  const page = search.page ?? 0
  const status = search.memberStatus ?? filters.status
  const memberQ = search.memberQ ?? ''
  const [draft, setDraft] = useState(memberQ)
  useEffect(() => setDraft(memberQ), [memberQ])
  const batch = useBatchReview(JSON.stringify(['repository', user?.userId, snapshotId, search.namespace, search.status, search.reviewKind, status, memberQ]))
  const context = useQuery({ queryKey: ['reviews', 'repository-group', user?.userId, snapshotId],
    queryFn: () => reviewApi.syncGroup(snapshotId), enabled: validId, retry: false })
  const params = { namespaceId: context.data?.namespaceId, status, q: memberQ, reviewKind: filters.reviewKind, page, size: 20, sortDirection: filters.sortDirection }
  const members = useQuery({ queryKey: ['reviews', 'repository-members', user?.userId, snapshotId, params],
    queryFn: () => reviewApi.syncGroupMembers(snapshotId, params), enabled: validId && context.isSuccess, retry: false })
  const scrollKey = ['reviews', 'repository-scroll', user?.userId, snapshotId, status, memberQ, page]
  useEffect(() => {
    const top = client.getQueryData<number>(scrollKey)
    if (members.isSuccess && !members.isFetching && top !== undefined && document.getElementById('review-results')) {
      client.removeQueries({ queryKey: scrollKey, exact: true })
      window.scrollTo({ top, behavior: 'instant' })
    }
  })
  const updateMembers = (next: { memberQ?: string; memberStatus?: ReviewCenterStatus; page?: number }) => {
    void navigate({ to: '/dashboard/reviews/repositories/$snapshotId', params: { snapshotId: rawId }, search: { ...search, snapshotId: undefined, ...next } })
  }
  const back = () => batch.guardFilter(() => {
    void navigate({ to: '/dashboard/reviews', search: { ...search, originType: 'GITHUB_SYNC', reviewView: 'snapshots', page: search.groupPage, groupPage: undefined, snapshotId: undefined, memberQ: undefined, memberStatus: undefined } })
  })
  const openReview = (task: ReviewTask) => {
    client.setQueryData(scrollKey, window.scrollY)
    void navigate({ to: '/dashboard/reviews/$id', params: { id: String(task.id) }, search: { ...search, snapshotId } })
  }
  const renderObject = (task: ReviewTask) => <div className="flex min-w-0 items-start gap-3"><SkillAvatar name={task.displayName || task.skillSlug || '?'} seed={`${task.namespace}/${task.skillSlug}`} size="h-8 w-8 text-xs" /><div className="min-w-0"><p className="break-words text-sm font-semibold">{task.displayName || task.skillSlug}</p><p className="mt-1 break-all text-xs text-muted-foreground">@{task.namespace}/{task.skillSlug}</p><p className="mt-1 text-xs text-muted-foreground">{task.version}</p></div></div>
  const kind = (task: ReviewTask) => <><span className="inline-flex rounded-md bg-secondary px-2 py-1 text-xs">{t(`reviewCenter.kind.${task.reviewKind || 'SKILL_VERSION'}`)}</span>{task.status !== 'PENDING' && <p className="mt-1 text-xs text-muted-foreground">{t(`reviewCenter.status.${task.status}`)}</p>}</>
  const scan = (value: string) => <span className={`inline-flex whitespace-nowrap rounded-md px-2 py-1 text-xs ${value === 'PASSED' ? 'bg-success/10 text-success' : value === 'BLOCKED' ? 'bg-destructive/10 text-destructive' : 'bg-warning/10 text-warning'}`}>{t(`reviewGroups.scan${value}`)}</span>
  const openButton = (task: ReviewTask) => <Button variant="ghost" size="icon" className="h-11 w-11 sm:h-8 sm:w-8" title={t('reviewCenter.open')} aria-label={t('reviewCenter.open')} onClick={() => openReview(task)}><Eye aria-hidden="true" className="h-4 w-4" /></Button>
  const tasks = members.data?.items ?? []
  const selectable = tasks.map(item => item.review).filter(isBatchReviewTask)
  const allSelected = selectable.length > 0 && selectable.every(task => batch.selected.some(selected => selected.id === task.id))
  const statusError = context.error && typeof context.error === 'object' && 'status' in context.error ? context.error.status : undefined

  return <div className="space-y-6">
    <Button variant="link" className="min-h-11 px-0 sm:min-h-8" disabled={batch.busy} onClick={back}><ArrowLeft aria-hidden="true" className="mr-2 h-4 w-4" />{t('reviewGroups.back')}</Button>
    <DashboardPageHeader title={context.data ? `${context.data.owner}/${context.data.repository}` : t('reviewGroups.detailTitle')} subtitle={t('reviewGroups.detailDescription')} />
    {batch.dialogs}
    {!validId || context.isError ? <Card className="space-y-3 p-6" role="alert"><p>{t(statusError === 403 ? 'reviewCenter.noPermission' : 'reviewCenter.loadFailed')}</p>{validId && <Button variant="outline" onClick={() => void context.refetch()}>{t('reviewCenter.retry')}</Button>}</Card>
      : context.isPending ? <div className="h-28 animate-shimmer rounded-lg" aria-label={t('reviewCenter.loading')} />
        : <>
          <div className="flex flex-wrap items-center justify-between gap-4 rounded-lg border bg-secondary/40 px-5 py-4">
            <div className="min-w-0 space-y-2"><p className="flex items-center gap-2 text-sm"><GitFork aria-hidden="true" className="h-4 w-4 shrink-0" /><span className="break-all">@{context.data.namespace}</span></p><p className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground"><GitCommitHorizontal aria-hidden="true" className="h-4 w-4" /><code className="break-all" title={context.data.commitSha}>{context.data.commitSha.slice(0,12)}</code><span>{formatLocalDateTime(context.data.capturedAt, i18n.language)}</span></p></div>
            <p className="text-sm text-muted-foreground">{t('reviewGroups.summary', { total: context.data.total, passed: context.data.scanPassed, waiting: context.data.scanWaiting, blocked: context.data.scanBlocked })}</p>
          </div>
          <form className="flex flex-wrap items-end gap-3" aria-label={t('reviewGroups.searchMembers')} onSubmit={event => { event.preventDefault(); batch.guardFilter(() => updateMembers({ memberQ: draft.trim() || undefined, page: undefined })) }}>
            <label className="w-full min-w-0 space-y-2 sm:w-auto sm:max-w-md sm:flex-1"><span className="text-xs text-muted-foreground">{t('reviewGroups.searchMembers')}</span><Input disabled={batch.busy} maxLength={200} value={draft} onChange={event => setDraft(event.target.value)} className="h-11 sm:h-8" placeholder={t('reviewGroups.searchMembers')} /></label>
            <div className="space-y-2"><label htmlFor="repository-review-status" className="block text-xs text-muted-foreground">{t('reviewCenter.filters.status')}</label><Select disabled={batch.busy} value={status} onValueChange={value => batch.guardFilter(() => updateMembers({ memberStatus: value as ReviewCenterStatus, page: undefined }))}><SelectTrigger id="repository-review-status" aria-label={t('reviewCenter.filters.status')} className="h-11 w-40 sm:h-8"><SelectValue /></SelectTrigger><SelectContent className="w-[var(--radix-select-trigger-width)] min-w-0">{REVIEW_STATUSES.map(value => <SelectItem key={value} value={value}>{t(`reviewCenter.status.${value}`)}</SelectItem>)}</SelectContent></Select></div>
            <Button type="submit" className="h-11 sm:h-8" disabled={batch.busy}><Search aria-hidden="true" className="mr-2 h-4 w-4" />{t('reviewCenter.filters.apply')}</Button>
          </form>
          <p className="text-xs leading-5 text-muted-foreground">{t('reviewGroups.decisionHint')}</p>
          <ReviewResultsPanel selected={batch.selected.length > 0} busy={members.isFetching} header={batch.toolbar ?? <><h2 className="text-sm font-medium">{t('reviewCenter.resultsCount', { count: members.data?.total ?? 0 })}</h2><Button variant="ghost" disabled={batch.busy || !tasks.some(item => item.scanStatus === 'PASSED' && isBatchReviewTask(item.review))} onClick={() => batch.selectPage(tasks.filter(item => item.scanStatus === 'PASSED').map(item => item.review))}>{t('reviewGroups.selectPassed')}</Button></>}
            footer={members.data && members.data.total > 0 ? <><p>{t('reviewCenter.pageSummary', { total: members.data.total, page: page + 1 })}</p><Pagination analyticsId="review.repository.members.paginate" page={page} totalPages={Math.max(1,Math.ceil(members.data.total / members.data.size))} onPageChange={next => updateMembers({ page: next || undefined })} /></> : undefined}>
            {members.isPending ? <div className="m-4 h-40 animate-shimmer rounded-lg" aria-label={t('reviewCenter.loading')} />
              : members.isError ? <div className="space-y-3 p-6" role="alert"><p>{t('reviewCenter.loadFailed')}</p><Button variant="outline" onClick={() => void members.refetch()}>{t('reviewCenter.retry')}</Button></div>
                : !tasks.length ? <div className="space-y-3 p-8 text-center"><p>{t('reviewGroups.empty')}</p>{page > 0 && <Button variant="outline" onClick={() => updateMembers({ page: undefined })}>{t('reviewCenter.firstPage')}</Button>}</div>
                  : <>
                    <div className="hidden overflow-x-auto md:block"><table className="w-full min-w-[700px] table-fixed text-left"><thead className="bg-secondary text-xs text-muted-foreground"><tr><th scope="col" className="w-10 px-3 py-3"><input type="checkbox" className="h-4 w-4 accent-primary" aria-label={t('reviewBatch.selectPage')} disabled={batch.busy || !selectable.length} checked={allSelected} ref={input => { if (input) input.indeterminate = !allSelected && selectable.some(task => batch.selected.some(selected => selected.id === task.id)) }} onChange={() => batch.selectPage(tasks.map(item => item.review))} /></th><th scope="col" className="w-[38%] px-4 py-3">{t('reviewCenter.columns.object')}</th><th scope="col" className="w-[17%] px-4 py-3">{t('reviewCenter.columns.kind')}</th><th scope="col" className="w-[17%] px-4 py-3">{t('reviewGroups.scanColumn')}</th><th scope="col" className="w-[21%] px-4 py-3">{t('reviewCenter.columns.submitter')}</th><th scope="col" className="w-12 px-2 py-3"><span className="sr-only">{t('reviewCenter.columns.action')}</span></th></tr></thead><tbody className="divide-y">{tasks.map(({ review: task, scanStatus }) => <tr key={task.id} className={batch.selected.some(selected => selected.id === task.id) ? 'bg-accent/70' : 'hover:bg-secondary/70'}><td className="px-3 py-4 align-top">{batch.checkbox(task)}</td><td className="px-4 py-4 align-top">{renderObject(task)}</td><td className="px-4 py-4 align-top">{kind(task)}</td><td className="px-4 py-4 align-top">{scan(scanStatus)}</td><td className="px-4 py-4 align-top"><p className="truncate text-sm" title={task.submittedByName || task.submittedBy}>{task.submittedByName || task.submittedBy}</p><p className="mt-1 text-xs text-muted-foreground">{formatLocalDateTime(task.submittedAt, i18n.language)}</p></td><td className="px-2 py-3 align-top">{openButton(task)}</td></tr>)}</tbody></table></div>
                    <div className="divide-y px-4 md:hidden">{tasks.map(({ review: task, scanStatus }) => <article key={task.id} className="space-y-3 py-4"><div className="flex items-start gap-2"><label className="flex min-h-11 min-w-11 items-center justify-center">{batch.checkbox(task)}</label>{renderObject(task)}</div><div className="flex flex-wrap items-center justify-between gap-3"><div className="flex flex-wrap gap-2">{kind(task)}{scan(scanStatus)}</div>{openButton(task)}</div></article>)}</div>
                  </>}
          </ReviewResultsPanel>
        </>}
  </div>
}
