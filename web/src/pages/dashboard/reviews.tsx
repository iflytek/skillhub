import { useQueries, useQueryClient } from '@tanstack/react-query'
import { reviewApi } from '@/api/client'
import { useEffect, useState } from 'react'
import { Eye } from 'lucide-react'
import { useBatchReview, isBatchReviewTask } from '@/features/review/batch-review'
import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { canManageNamespaceReviews } from '@/features/review/review-paths'
import { VerdictBadge } from '@/features/security-audit/verdict-badge'
import { ReviewCenterFilterForm } from '@/features/review/review-center-filter-form'
import { DEFAULT_REVIEW_FILTERS, parseReviewCenterFilters, reviewCenterSearch, type ReviewCenterFilters } from '@/features/review/review-center-filters'
import { useReviewScopes } from '@/features/review/use-review-scopes'
import { useReviewList } from '@/features/review/use-review-list'
import { SkillAvatar } from '@/features/skill/skill-avatar'
import type { ReviewTask } from '@/api/types'
import { useAuth } from '@/features/auth/use-auth'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/shared/ui/tabs'
import { ReviewResultsPanel } from '@/features/review/review-results-panel'
import { ReviewRepositoryGroups } from '@/features/review/review-repository-groups'
import { REVIEW_ORIGINS } from '@/features/review/review-center-filters'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { ProfileReviewTable } from './profile-review-table'

const PAGE_SIZE = 20

export function reviewTaskKind(review: ReviewTask): string {
  if (review.reviewKind) return review.reviewKind
  if (review.subjectType === 'SUITE_VERSION') return 'SUITE_PUBLISH'
  if (review.subjectType === 'SKILL_METADATA') return review.requestedVisibility ? 'VISIBILITY_CHANGE' : 'AVATAR_CHANGE'
  return 'SKILL_VERSION'
}

/** One authorized queue, regardless of submission source or namespace entry point. */
export function ReviewsPage() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const search = useSearch({ from: '/dashboard/reviews' })
  const { hasRole, user } = useAuth()
  const namespacesQuery = useMyNamespaces()
  const namespaces = namespacesQuery.data ?? []
  const routeFilters = parseReviewCenterFilters(search)
  const [draft, setDraft] = useState<ReviewCenterFilters>(routeFilters)
  const [applied, setApplied] = useState<ReviewCenterFilters>(routeFilters)
  const [page, setPage] = useState(search.page ?? 0)
  const { q: routeQ, status: routeStatus, namespace: routeNamespace, reviewKind: routeKind, originType: routeOrigin, scanVerdict: routeVerdict, sortDirection: routeSort } = routeFilters
  const routePage = search.page ?? 0
  const batch = useBatchReview(JSON.stringify([user?.userId, routeFilters, search.snapshotId, search.reviewView, search.memberQ]))

  useEffect(() => {
    const next = { q: routeQ, status: routeStatus, namespace: routeNamespace, reviewKind: routeKind, originType: routeOrigin, scanVerdict: routeVerdict, sortDirection: routeSort }
    setApplied(next)
    setDraft(next)
    setPage(routePage)
  }, [routeQ, routeStatus, routeNamespace, routeKind, routeOrigin, routeVerdict, routeSort, routePage])

  const isSkillAdmin = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')
  const isUserAdmin = hasRole('USER_ADMIN') || hasRole('SUPER_ADMIN')
  const managedNamespaces = namespaces.filter((namespace) => namespace.type === 'TEAM' && canManageNamespaceReviews(namespace.currentUserRole))
  const canReviewSkills = isSkillAdmin || managedNamespaces.length > 0
  const profileMode = isUserAdmin && (search.type === 'profile' || (!canReviewSkills && !namespacesQuery.isLoading))
  const scopes = useReviewScopes(canReviewSkills && !profileMode)
  const scope = scopes.data?.find((item) => item.slug === applied.namespace)
  const scopePending = !scopes.data || (applied.namespace !== 'ALL' && !scope)
  const grouped = applied.originType === 'GITHUB_SYNC' && applied.scanVerdict === 'ALL' && search.reviewView !== 'rows'
  const query = useReviewList(applied.status, applied.namespace === 'ALL' ? undefined : scope?.id,
    page, PAGE_SIZE, applied.sortDirection, canReviewSkills && !profileMode && !scopePending && !grouped,
    { q: applied.q.trim(), reviewKind: applied.reviewKind, originType: applied.originType, scanVerdict: applied.scanVerdict })
  const sourceCounts = useQueries({ queries: REVIEW_ORIGINS.map(originType => ({
    queryKey: ['reviews', 'source-count', user?.userId, scope?.id, applied.status, applied.namespace, applied.q, applied.reviewKind, applied.scanVerdict, originType],
    queryFn: async () => (await reviewApi.list({ status: applied.status, namespaceId: scope?.id,
      q: applied.q.trim(), reviewKind: applied.reviewKind, originType, scanVerdict: applied.scanVerdict, page: 0, size: 1 })).total,
    enabled: canReviewSkills && !profileMode && !scopePending && !scopes.isError,
    staleTime: 15000, retry: false,
  })) })
  const options = (scopes.data ?? []).map((item) => ({ value: item.slug, label: item.displayName || item.slug }))

  const applyNow = (filters: ReviewCenterFilters, nextPage = 0) => {
    const normalized = { ...filters, q: filters.q.trim() }
    setApplied(normalized)
    setDraft(normalized)
    setPage(nextPage)
    void navigate({ to: '/dashboard/reviews', search: { ...reviewCenterSearch(normalized, nextPage), reviewView: normalized.originType === 'GITHUB_SYNC' ? (search.reviewView ?? 'snapshots') : undefined } })
    // A repeated query or reset must refresh the same key rather than silently reuse a stale result.
    if (!scopePending && JSON.stringify(normalized) === JSON.stringify(applied) && nextPage === page) {
      if (grouped) void queryClient.invalidateQueries({ queryKey: ['reviews'] })
      else void query.refetch()
    }
  }
  const apply = (filters: ReviewCenterFilters, nextPage = 0) => {
    if (JSON.stringify(filters) !== JSON.stringify(applied)) batch.guardFilter(() => applyNow(filters, nextPage))
    else applyNow(filters, nextPage)
  }
  const scrollKey = ['reviews', 'scroll', user?.userId, routeFilters, search.reviewView, search.snapshotId, search.memberQ, page]
  const restoreScroll = () => {
    const top = queryClient.getQueryData<number>(scrollKey)
    if (top !== undefined && document.getElementById('review-results')) {
      queryClient.removeQueries({ queryKey: scrollKey, exact: true })
      window.scrollTo({ top, behavior: 'instant' })
    }
  }
  useEffect(() => {
    if (!grouped && query.data && !query.isFetching) restoreScroll()
  })
  const openReview = (review: ReviewTask) => {
    queryClient.setQueryData(scrollKey, window.scrollY)
    void navigate({ to: '/dashboard/reviews/$id', params: { id: String(review.id) }, search: { ...reviewCenterSearch(applied, page), reviewView: search.reviewView, snapshotId: search.snapshotId, groupPage: search.groupPage, memberQ: search.memberQ } })
  }
  // Keep previously shared snapshot URLs working after moving members to their own page.
  useEffect(() => {
    if (search.snapshotId) void navigate({ to: '/dashboard/reviews/repositories/$snapshotId', params: { snapshotId: String(search.snapshotId) }, search, replace: true })
  }, [search.snapshotId, search, navigate])
  const formatDate = (value: string) => formatLocalDateTime(value, i18n.language)
  const renderObject = (review: ReviewTask) => {
    const slug = review.subjectSlug || review.skillSlug || ''
    const name = review.displayName || slug
    return <div className="flex min-w-0 gap-3">
      <SkillAvatar name={name} seed={`${review.namespace}/${slug}`} size="h-8 w-8 text-xs" />
      <div className="min-w-0">
        <p className="break-words text-sm font-semibold">{name || t('reviewCenter.unknownObject')}</p>
        <p className="mt-1 break-all text-xs text-muted-foreground">@{review.namespace}/{slug}</p>
        <p className="mt-1 break-words text-xs text-muted-foreground">{t(review.subjectType === 'SUITE_VERSION' ? 'suite.resourceTypeSuite' : 'suite.resourceTypeSkill')}{review.version ? ` · ${review.version}` : ''}</p>
      </div>
    </div>
  }
  const renderMetadata = (review: ReviewTask) => {
    const kind = reviewTaskKind(review)
    return <>
      <span className="inline-flex rounded-full bg-secondary px-2.5 py-1 text-xs font-medium">{t(kind === 'AVATAR_CHANGE' && review.avatarRemoval ? 'reviewCenter.kind.AVATAR_REMOVAL' : `reviewCenter.kind.${kind}`)}</span>
      {applied.status !== 'PENDING' && <span className="mt-1 block text-xs text-muted-foreground">{t(`reviewCenter.status.${review.status}`)}</span>}
      {review.scanVerdict && <VerdictBadge compact verdict={review.scanVerdict} />}
      {review.decisionMethod && (
        <span className="mt-1 block text-xs text-muted-foreground">
          {t(`review.decisionMethod.${review.decisionMethod}`)}
        </span>
      )}
    </>
  }

  if (namespacesQuery.isLoading && !isSkillAdmin) {
    return <div className="space-y-6"><DashboardPageHeader title={t('reviewCenter.title')} /><div className="h-40 animate-shimmer rounded-lg" aria-label={t('reviewCenter.loading')} /></div>
  }
  if (!canReviewSkills && !isUserAdmin) {
    return <div className="space-y-6"><DashboardPageHeader title={t('reviewCenter.title')} /><Card className="p-6" role="alert"><p>{t(namespacesQuery.isError ? 'reviewCenter.scopeFailed' : 'reviewCenter.noPermission')}</p>{namespacesQuery.isError && <Button type="button" variant="outline" className="mt-3" onClick={() => void namespacesQuery.refetch()}>{t('reviewCenter.retry')}</Button>}</Card></div>
  }

  return <Tabs value={applied.originType} onValueChange={origin => apply({ ...applied, originType: origin as ReviewCenterFilters['originType'] })} className="space-y-7">
    <DashboardPageHeader title={t('reviewCenter.title')} subtitle={t(profileMode ? 'reviewCenter.profileDescription' : 'reviewCenter.description')}
      actions={<div className="flex w-full flex-wrap items-center justify-end gap-3 sm:w-auto">{!profileMode && <div className="w-full sm:w-[440px]"><TabsList aria-label={t('reviewCenter.filters.originType')} className="grid h-[52px] w-full grid-cols-3 gap-1 rounded-xl border-0 bg-secondary p-1 sm:h-10">{REVIEW_ORIGINS.map((origin, index) => <TabsTrigger key={origin} value={origin} disabled={batch.busy} className="h-11 min-w-0 rounded-lg border-0 px-2 py-0 text-xs font-medium text-muted-foreground after:hidden aria-selected:bg-primary aria-selected:text-primary-foreground sm:h-8 sm:text-sm">{t(`reviewCenter.origin.${origin}`)}{sourceCounts[index].data !== undefined && <span className="ml-2 hidden text-xs opacity-65 sm:inline">{sourceCounts[index].data}</span>}</TabsTrigger>)}</TabsList></div>}{isUserAdmin && canReviewSkills ? <Link to="/dashboard/reviews" search={profileMode ? {} : { type: 'profile' }} className="inline-flex min-h-9 items-center rounded-lg border border-border/60 px-3 text-sm hover:bg-secondary">{t(profileMode ? 'reviewCenter.skillQueue' : 'reviewCenter.profileQueue')}</Link> : null}</div>} />
    {profileMode ? <ProfileReviewTable /> : <TabsContent value={applied.originType} className="mt-0 space-y-7">
      <ReviewCenterFilterForm value={draft} namespaces={options} namespaceLoading={scopes.isLoading || scopes.isError} onChange={setDraft} onApply={() => apply(draft)} onReset={() => apply({ ...DEFAULT_REVIEW_FILTERS })} />
      {scopes.isError && <div role="alert" className="flex flex-wrap items-center gap-3 text-sm"><p className="text-destructive">{t('reviewCenter.scopeFailed')}</p><Button type="button" size="sm" variant="outline" onClick={() => void scopes.refetch()}>{t('reviewCenter.retry')}</Button></div>}
      {batch.dialogs}
      {applied.originType === 'GITHUB_SYNC' && <div className="flex justify-end gap-2 [&_button]:min-h-11 sm:[&_button]:min-h-8">
        {(['rows', 'snapshots'] as const).map(view => <Button key={view} variant={(grouped ? 'snapshots' : 'rows') === view ? 'secondary' : 'outline'} disabled={batch.busy || (view === 'snapshots' && applied.scanVerdict !== 'ALL')} onClick={() => batch.guardFilter(() => { void navigate({ to: '/dashboard/reviews', search: { ...reviewCenterSearch(applied), reviewView: view } }) })}>{t(`reviewGroups.${view}`)}</Button>)}
      </div>}
      {grouped ? <ReviewRepositoryGroups filters={applied} namespaceId={scope?.id} enabled={!scopePending && !scopes.isError} page={page}
        onGroup={snapshotId => batch.guardFilter(() => {
          queryClient.setQueryData(scrollKey, window.scrollY)
          void navigate({ to: '/dashboard/reviews/repositories/$snapshotId', params: { snapshotId: String(snapshotId) }, search: { ...reviewCenterSearch(applied), reviewView: 'snapshots', groupPage: page } })
        })}
        onPage={nextPage => { void navigate({ to: '/dashboard/reviews', search: { ...reviewCenterSearch(applied, nextPage), reviewView: 'snapshots' } }) }}
        onReady={restoreScroll} /> : <ReviewResultsPanel selected={batch.selected.length > 0} busy={query.isFetching} header={batch.toolbar ?? <>
          <h2 className="text-sm font-medium">{query.isLoading || scopePending ? t('reviewCenter.loading') : query.isError ? t('reviewCenter.results') : t('reviewCenter.resultsCount', { count: query.data?.totalElements ?? 0 })}</h2>
          <Button variant="ghost" size="sm" className="md:hidden" disabled={batch.busy || !query.data?.items.some(isBatchReviewTask)} onClick={() => batch.selectPage(query.data?.items ?? [])}>{t('reviewBatch.selectPage')}</Button>
          <Select value={applied.sortDirection} onValueChange={(sortDirection) => apply({ ...applied, sortDirection: sortDirection === 'ASC' ? 'ASC' : 'DESC' })}>
            <SelectTrigger aria-label={t('reviewCenter.sortLabel')} className="h-9 w-36 border-0 bg-transparent text-xs shadow-none"><SelectValue /></SelectTrigger>
            <SelectContent className="min-w-0 w-[var(--radix-select-trigger-width)] max-w-[var(--radix-select-trigger-width)]"><SelectItem value="DESC">{t('reviewCenter.newest')}</SelectItem><SelectItem value="ASC">{t('reviewCenter.oldest')}</SelectItem></SelectContent>
          </Select>
        </>}>
        {!scopes.isLoading && !scopes.isError && applied.namespace !== 'ALL' && !scope ? <Card className="space-y-3 p-6" role="alert"><p>{t('reviewCenter.scopeFailed')}</p><Button type="button" variant="outline" onClick={() => void scopes.refetch()}>{t('reviewCenter.retry')}</Button></Card>
          : scopes.isError ? null : query.isError ? <Card className="space-y-3 p-6" role="alert"><p>{t('reviewCenter.loadFailed')}</p><Button type="button" variant="outline" onClick={() => void query.refetch()}>{t('reviewCenter.retry')}</Button></Card>
            : query.isLoading || scopePending ? <div className="space-y-3" aria-label={t('reviewCenter.loading')}>{Array.from({ length: 4 }, (_, index) => <div key={index} className="h-20 animate-shimmer rounded-lg" />)}</div>
              : !query.data?.items.length ? <Card className="space-y-3 p-8 text-center"><p className="font-medium">{t(query.data?.totalElements && page > 0 ? 'reviewCenter.pageEmpty' : 'reviewCenter.empty')}</p><p className="text-sm text-muted-foreground">{t('reviewCenter.emptyDescription')}</p><Button type="button" variant="outline" onClick={() => query.data?.totalElements && page > 0 ? apply(applied, 0) : apply({ ...DEFAULT_REVIEW_FILTERS })}>{t(query.data?.totalElements && page > 0 ? 'reviewCenter.firstPage' : 'reviewCenter.filters.reset')}</Button></Card>
                : <>
                  <div className="hidden overflow-x-auto md:block">
                    <table className="w-full min-w-[700px] table-fixed text-left">
                      <thead className="bg-secondary text-xs font-medium text-muted-foreground"><tr><th className="w-10 px-3 py-3"><input type="checkbox" className="h-4 w-4 accent-primary" aria-label={t('reviewBatch.selectPage')} disabled={batch.busy || !query.data.items.some(isBatchReviewTask)} checked={query.data.items.some(isBatchReviewTask) && query.data.items.filter(isBatchReviewTask).every(task => batch.selected.some(item => item.id === task.id))} ref={input => { if (input) input.indeterminate = query.data.items.some(task => batch.selected.some(item => item.id === task.id)) && !query.data.items.filter(isBatchReviewTask).every(task => batch.selected.some(item => item.id === task.id)) }} onChange={() => batch.selectPage(query.data.items)} /></th><th className="w-[37%] px-4 py-3">{t('reviewCenter.columns.object')}</th><th className="w-[16%] px-4 py-3">{t('reviewCenter.columns.kind')}</th><th className="w-[20%] px-4 py-3">{t('reviewCenter.columns.source')}</th><th className="w-[17%] px-4 py-3">{t('reviewCenter.columns.submitter')}</th><th className="w-[6%] px-2 py-3"><span className="sr-only">{t('reviewCenter.columns.action')}</span></th></tr></thead>
                      <tbody className="divide-y divide-border/70">{query.data.items.map((review) => <tr key={review.id} className={batch.selected.some(item => item.id === review.id) ? "bg-accent/70" : "hover:bg-secondary/70"}><td className="px-3 py-5 align-top">{batch.checkbox(review)}</td><td className="px-4 py-5 align-top">{renderObject(review)}</td><td className="px-4 py-5 align-top">{renderMetadata(review)}</td><td className="px-4 py-5 align-top"><p className="text-sm">{t(`reviewCenter.origin.${review.originType || 'USER'}`)}</p><p className="mt-1 break-all text-xs text-muted-foreground">{review.namespace}</p></td><td className="px-4 py-5 align-top"><p className="truncate text-sm" title={review.submittedByName || review.submittedBy}>{review.submittedByName || review.submittedBy}</p><p className="mt-1 text-xs text-muted-foreground">{formatDate(review.submittedAt)}</p></td><td className="px-2 py-5 align-top"><Button data-analytics-id="review.detail.open" variant="ghost" size="icon" title={t('reviewCenter.open')} aria-label={t('reviewCenter.open')} onClick={() => openReview(review)}><Eye className="h-4 w-4" /></Button></td></tr>)}</tbody>
                    </table>
                  </div>
                  <div className="divide-y divide-border px-4 md:hidden">{query.data.items.map((review) => <article key={review.id} className="space-y-4 py-5"><div className="flex items-start gap-3"><label className="flex min-h-11 min-w-11 items-center justify-center">{batch.checkbox(review)}</label>{renderObject(review)}</div><div>{renderMetadata(review)}</div><div className="flex items-end justify-between gap-4"><p className="min-w-0 break-words text-xs text-muted-foreground">{review.submittedByName || review.submittedBy} · {t(`reviewCenter.origin.${review.originType || 'USER'}`)}<span className="mt-1 block">{formatDate(review.submittedAt)}</span></p><Button data-analytics-id="review.detail.open" variant="link" className="shrink-0" onClick={() => openReview(review)}>{t('reviewCenter.open')}</Button></div></article>)}</div>
                  <div className="flex flex-wrap items-center justify-between gap-3 border-t px-4 py-3 text-xs text-muted-foreground"><p>{t('reviewCenter.pageSummary', { total: query.data.totalElements, page: page + 1 })}</p><Pagination analyticsId="review.list.paginate" page={page} totalPages={Math.max(query.data.totalPages, 1)} onPageChange={(nextPage) => apply(applied, nextPage)} /></div>
                </>}
      </ReviewResultsPanel>}
    </TabsContent>}
  </Tabs>
}
