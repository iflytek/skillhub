import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { canManageNamespaceReviews } from '@/features/review/review-paths'
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
  const navigate = useNavigate()
  const search = useSearch({ from: '/dashboard/reviews' })
  const { hasRole } = useAuth()
  const namespacesQuery = useMyNamespaces()
  const namespaces = namespacesQuery.data ?? []
  const routeFilters = parseReviewCenterFilters(search)
  const [draft, setDraft] = useState<ReviewCenterFilters>(routeFilters)
  const [applied, setApplied] = useState<ReviewCenterFilters>(routeFilters)
  const [page, setPage] = useState(search.page ?? 0)
  const { q: routeQ, status: routeStatus, namespace: routeNamespace, reviewKind: routeKind, originType: routeOrigin, sortDirection: routeSort } = routeFilters
  const routePage = search.page ?? 0

  useEffect(() => {
    const next = { q: routeQ, status: routeStatus, namespace: routeNamespace, reviewKind: routeKind, originType: routeOrigin, sortDirection: routeSort }
    setApplied(next)
    setDraft(next)
    setPage(routePage)
  }, [routeQ, routeStatus, routeNamespace, routeKind, routeOrigin, routeSort, routePage])

  const isSkillAdmin = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')
  const isUserAdmin = hasRole('USER_ADMIN') || hasRole('SUPER_ADMIN')
  const managedNamespaces = namespaces.filter((namespace) => namespace.type === 'TEAM' && canManageNamespaceReviews(namespace.currentUserRole))
  const canReviewSkills = isSkillAdmin || managedNamespaces.length > 0
  const profileMode = isUserAdmin && (search.type === 'profile' || (!canReviewSkills && !namespacesQuery.isLoading))
  const scopes = useReviewScopes(canReviewSkills && !profileMode)
  const scope = scopes.data?.find((item) => item.slug === applied.namespace)
  const scopePending = !scopes.data || (applied.namespace !== 'ALL' && !scope)
  const query = useReviewList(applied.status, applied.namespace === 'ALL' ? undefined : scope?.id,
    page, PAGE_SIZE, applied.sortDirection, canReviewSkills && !profileMode && !scopePending,
    { q: applied.q.trim(), reviewKind: applied.reviewKind, originType: applied.originType })
  const options = (scopes.data ?? []).map((item) => ({ value: item.slug, label: item.displayName || item.slug }))

  const apply = (filters: ReviewCenterFilters, nextPage = 0) => {
    const normalized = { ...filters, q: filters.q.trim() }
    setApplied(normalized)
    setDraft(normalized)
    setPage(nextPage)
    void navigate({ to: '/dashboard/reviews', search: reviewCenterSearch(normalized, nextPage) })
    // A repeated query or reset must refresh the same key rather than silently reuse a stale result.
    if (!scopePending && JSON.stringify(normalized) === JSON.stringify(applied) && nextPage === page) void query.refetch()
  }
  const openReview = (review: ReviewTask) => {
    void navigate({ to: '/dashboard/reviews/$id', params: { id: String(review.id) }, search: reviewCenterSearch(applied, page) })
  }
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
    </>
  }

  if (namespacesQuery.isLoading && !isSkillAdmin) {
    return <div className="space-y-6"><DashboardPageHeader title={t('reviewCenter.title')} /><div className="h-40 animate-shimmer rounded-lg" aria-label={t('reviewCenter.loading')} /></div>
  }
  if (!canReviewSkills && !isUserAdmin) {
    return <div className="space-y-6"><DashboardPageHeader title={t('reviewCenter.title')} /><Card className="p-6" role="alert"><p>{t(namespacesQuery.isError ? 'reviewCenter.scopeFailed' : 'reviewCenter.noPermission')}</p>{namespacesQuery.isError && <Button type="button" variant="outline" className="mt-3" onClick={() => void namespacesQuery.refetch()}>{t('reviewCenter.retry')}</Button>}</Card></div>
  }

  return <div className="space-y-7">
    <DashboardPageHeader title={t('reviewCenter.title')} subtitle={t(profileMode ? 'reviewCenter.profileDescription' : 'reviewCenter.description')}
      actions={isUserAdmin && canReviewSkills ? <Link to="/dashboard/reviews" search={profileMode ? {} : { type: 'profile' }} className="inline-flex min-h-9 items-center rounded-lg border border-border/60 px-3 text-sm hover:bg-secondary">{t(profileMode ? 'reviewCenter.skillQueue' : 'reviewCenter.profileQueue')}</Link> : undefined} />
    {profileMode ? <ProfileReviewTable /> : <>
      <ReviewCenterFilterForm value={draft} namespaces={options} namespaceLoading={scopes.isLoading || scopes.isError} onChange={setDraft} onApply={() => apply(draft)} onReset={() => apply({ ...DEFAULT_REVIEW_FILTERS })} />
      {scopes.isError && <div role="alert" className="flex flex-wrap items-center gap-3 text-sm"><p className="text-destructive">{t('reviewCenter.scopeFailed')}</p><Button type="button" size="sm" variant="outline" onClick={() => void scopes.refetch()}>{t('reviewCenter.retry')}</Button></div>}
      <div className="space-y-4" aria-busy={query.isFetching}>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-sm font-medium">{query.isLoading || scopePending ? t('reviewCenter.loading') : query.isError ? t('reviewCenter.results') : t('reviewCenter.resultsCount', { count: query.data?.totalElements ?? 0 })}</h2>
          <Select value={applied.sortDirection} onValueChange={(sortDirection) => apply({ ...applied, sortDirection: sortDirection === 'ASC' ? 'ASC' : 'DESC' })}>
            <SelectTrigger aria-label={t('reviewCenter.sortLabel')} className="h-9 w-36 border-0 bg-transparent text-xs shadow-none"><SelectValue /></SelectTrigger>
            <SelectContent className="min-w-0 w-[var(--radix-select-trigger-width)] max-w-[var(--radix-select-trigger-width)]"><SelectItem value="DESC">{t('reviewCenter.newest')}</SelectItem><SelectItem value="ASC">{t('reviewCenter.oldest')}</SelectItem></SelectContent>
          </Select>
        </div>
        {!scopes.isLoading && !scopes.isError && applied.namespace !== 'ALL' && !scope ? <Card className="space-y-3 p-6" role="alert"><p>{t('reviewCenter.scopeFailed')}</p><Button type="button" variant="outline" onClick={() => void scopes.refetch()}>{t('reviewCenter.retry')}</Button></Card>
          : scopes.isError ? null : query.isError ? <Card className="space-y-3 p-6" role="alert"><p>{t('reviewCenter.loadFailed')}</p><Button type="button" variant="outline" onClick={() => void query.refetch()}>{t('reviewCenter.retry')}</Button></Card>
            : query.isLoading || scopePending ? <div className="space-y-3" aria-label={t('reviewCenter.loading')}>{Array.from({ length: 4 }, (_, index) => <div key={index} className="h-20 animate-shimmer rounded-lg" />)}</div>
              : !query.data?.items.length ? <Card className="space-y-3 p-8 text-center"><p className="font-medium">{t(query.data?.totalElements && page > 0 ? 'reviewCenter.pageEmpty' : 'reviewCenter.empty')}</p><p className="text-sm text-muted-foreground">{t('reviewCenter.emptyDescription')}</p><Button type="button" variant="outline" onClick={() => query.data?.totalElements && page > 0 ? apply(applied, 0) : apply({ ...DEFAULT_REVIEW_FILTERS })}>{t(query.data?.totalElements && page > 0 ? 'reviewCenter.firstPage' : 'reviewCenter.filters.reset')}</Button></Card>
                : <>
                  <div className="hidden overflow-x-auto md:block">
                    <table className="w-full min-w-[700px] table-fixed text-left">
                      <thead className="bg-secondary text-xs font-medium text-muted-foreground"><tr><th className="w-[41%] rounded-l-lg px-4 py-3">{t('reviewCenter.columns.object')}</th><th className="w-[16%] px-4 py-3">{t('reviewCenter.columns.kind')}</th><th className="w-[20%] px-4 py-3">{t('reviewCenter.columns.source')}</th><th className="w-[17%] px-4 py-3">{t('reviewCenter.columns.submitter')}</th><th className="w-[6%] rounded-r-lg px-2 py-3"><span className="sr-only">{t('reviewCenter.columns.action')}</span></th></tr></thead>
                      <tbody className="divide-y divide-border/70">{query.data.items.map((review) => <tr key={review.id} className="hover:bg-secondary/70"><td className="px-4 py-5 align-top">{renderObject(review)}</td><td className="px-4 py-5 align-top">{renderMetadata(review)}</td><td className="px-4 py-5 align-top"><p className="text-sm">{t(`reviewCenter.origin.${review.originType || 'USER'}`)}</p><p className="mt-1 break-all text-xs text-muted-foreground">{review.namespace}</p></td><td className="px-4 py-5 align-top"><p className="break-words text-sm">{review.submittedByName || review.submittedBy}</p><p className="mt-1 text-xs text-muted-foreground">{formatDate(review.submittedAt)}</p></td><td className="px-2 py-5 align-top"><Button data-analytics-id="review.detail.open" variant="link" className="h-8 px-0 text-xs" onClick={() => openReview(review)}>{t('reviewCenter.open')}</Button></td></tr>)}</tbody>
                    </table>
                  </div>
                  <div className="divide-y divide-border md:hidden">{query.data.items.map((review) => <article key={review.id} className="space-y-4 py-5">{renderObject(review)}<div>{renderMetadata(review)}</div><div className="flex items-end justify-between gap-4"><p className="min-w-0 break-words text-xs text-muted-foreground">{review.submittedByName || review.submittedBy} · {t(`reviewCenter.origin.${review.originType || 'USER'}`)}<span className="mt-1 block">{formatDate(review.submittedAt)}</span></p><Button data-analytics-id="review.detail.open" variant="link" className="shrink-0" onClick={() => openReview(review)}>{t('reviewCenter.open')}</Button></div></article>)}</div>
                  <div className="flex flex-wrap items-center justify-between gap-3 pt-3 text-xs text-muted-foreground"><p>{t('reviewCenter.pageSummary', { total: query.data.totalElements, page: page + 1 })}</p><Pagination analyticsId="review.list.paginate" page={page} totalPages={Math.max(query.data.totalPages, 1)} onPageChange={(nextPage) => apply(applied, nextPage)} /></div>
                </>}
      </div>
    </>}
  </div>
}
