import { useState } from 'react'
import { Link, useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { buildNamespaceReviewDetailPath } from '@/features/review/review-paths'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Card } from '@/shared/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { useNamespaceDetail } from '@/shared/hooks/use-namespace-queries'
import { useReviewList } from '@/features/review/use-review-list'
import { GithubSyncReviewQueue } from '@/features/review/github-sync-review-queue'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { NamespaceManagementBack, NamespaceManagementNav } from '@/features/namespace/namespace-management-nav'
import { Pagination } from '@/shared/components/pagination'
import { NamespaceHeader } from '@/features/namespace/namespace-header'

type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
type TimeSortDirection = 'ASC' | 'DESC'
const PAGE_SIZE = 10

function ReviewListSection({ namespaceId, slug }: { namespaceId?: number; slug: string }) {
  const { t, i18n } = useTranslation()
  const reviewsEnabled = typeof namespaceId === 'number' && namespaceId > 0
  const [pages, setPages] = useState<Record<ReviewStatus, number>>({
    PENDING: 0,
    APPROVED: 0,
    REJECTED: 0,
  })
  const [activeStatus, setActiveStatus] = useState<ReviewStatus>('PENDING')
  const [sortDirection, setSortDirection] = useState<TimeSortDirection>('DESC')
  const pending = useReviewList('PENDING', namespaceId, pages.PENDING, PAGE_SIZE, sortDirection, reviewsEnabled && activeStatus === 'PENDING')
  const approved = useReviewList('APPROVED', namespaceId, pages.APPROVED, PAGE_SIZE, sortDirection, reviewsEnabled && activeStatus === 'APPROVED')
  const rejected = useReviewList('REJECTED', namespaceId, pages.REJECTED, PAGE_SIZE, sortDirection, reviewsEnabled && activeStatus === 'REJECTED')

  const changePage = (status: ReviewStatus, nextPage: number) => {
    setPages((current) => ({ ...current, [status]: nextPage }))
  }

  const handleSortChange = (value: string) => {
    setSortDirection(value as TimeSortDirection)
    setPages({
      PENDING: 0,
      APPROVED: 0,
      REJECTED: 0,
    })
  }

  const renderPagination = (status: ReviewStatus, totalElements: number, totalPages: number) => {
    if (totalPages <= 1) {
      return null
    }

    const currentPage = pages[status]

    return (
      <div className="flex flex-col gap-3 border-t border-border/60 px-5 py-4 text-sm text-muted-foreground md:flex-row md:items-center md:justify-between">
        <p>{t('nsReviews.pageSummary', { total: totalElements, page: currentPage + 1 })}</p>
        <Pagination analyticsId="review.namespace.paginate" page={currentPage} totalPages={totalPages} onPageChange={(nextPage) => changePage(status, nextPage)} />
      </div>
    )
  }

  const renderItems = (query: typeof pending, status: ReviewStatus) => {
    if (query.isLoading) {
      return (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="h-16 animate-shimmer rounded-xl" />
          ))}
        </div>
      )
    }

    const list = query.data?.items
    if (!list || list.length === 0) {
      return <Card className="p-10 text-center text-muted-foreground">{t('nsReviews.empty')}</Card>
    }
    return (
      <Card className="overflow-hidden divide-y divide-border/40">
        {list.map((review) => (
          <div key={review.id} className="p-5">
            <div className="flex items-center justify-between gap-4">
              <div>
                <div className="font-semibold font-heading">{review.namespace}/{review.skillSlug}</div>
                <div className="text-sm text-muted-foreground">{review.subjectType === 'SKILL_METADATA' ? t(review.requestedVisibility ? 'skillVisibility.title' : review.avatarRemoval ? 'review.avatarRemoval' : 'review.avatarUpdate') : t('nsReviews.version', { version: review.version })}</div>
              </div>
              <div className="text-sm text-muted-foreground">
                {formatLocalDateTime(
                  status === 'PENDING' ? review.submittedAt : review.reviewedAt ?? review.submittedAt,
                  i18n.language,
                )}
              </div>
            </div>
            {review.reviewComment ? (
              <p className="mt-3 text-sm text-muted-foreground">{review.reviewComment}</p>
            ) : null}
            <div className="mt-4 flex justify-end">
              <Link data-analytics-id="review.detail.open"
                to={buildNamespaceReviewDetailPath(slug, review.id)}
                className="inline-flex items-center rounded-md border border-border/60 px-3 py-2 text-sm font-medium text-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
              >
                {t('nsReviews.openReview')}
              </Link>
            </div>
          </div>
        ))}
        {query.data ? renderPagination(status, query.data.totalElements, query.data.totalPages) : null}
      </Card>
    )
  }

  return (
    <section aria-label={t('nsReviews.title')}>
      <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div className="w-full max-w-48">
          <label htmlFor="namespace-review-status" className="mb-2 block text-sm font-medium text-muted-foreground">{t('nsReviews.statusLabel')}</label>
          <Select value={activeStatus} onValueChange={(value) => setActiveStatus(value as ReviewStatus)}>
            <SelectTrigger data-analytics-id="review.namespace.status.interact" id="namespace-review-status"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem data-analytics-id="review.namespace.status" value="PENDING">{t('nsReviews.tabPending')}</SelectItem>
              <SelectItem data-analytics-id="review.namespace.status" value="APPROVED">{t('nsReviews.tabApproved')}</SelectItem>
              <SelectItem data-analytics-id="review.namespace.status" value="REJECTED">{t('nsReviews.tabRejected')}</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="w-full max-w-48">
          <label htmlFor="namespace-review-sort" className="mb-2 block text-sm font-medium text-muted-foreground">
            {t('nsReviews.sortLabel')}
          </label>
          <Select value={sortDirection} onValueChange={handleSortChange}>
            <SelectTrigger data-analytics-id="review.namespace.sort.interact" id="namespace-review-sort">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem data-analytics-id="review.namespace.sort" value="DESC">{t('nsReviews.sortNewest')}</SelectItem>
              <SelectItem data-analytics-id="review.namespace.sort" value="ASC">{t('nsReviews.sortOldest')}</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
      {renderItems(activeStatus === 'PENDING' ? pending : activeStatus === 'APPROVED' ? approved : rejected, activeStatus)}
    </section>
  )
}

export function NamespaceReviewsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const search = useSearch({ from: '/dashboard/namespaces/$slug/reviews' })
  const queueType = search.queue ?? 'user'
  const { slug } = useParams({ from: '/dashboard/namespaces/$slug/reviews' })
  const { data: namespace } = useNamespaceDetail(slug)
  const readOnlyMessage = namespace?.type === 'GLOBAL'
    ? t('nsReviews.globalReadOnly')
    : namespace?.status === 'FROZEN'
      ? t('nsReviews.frozenReadOnly')
      : namespace?.status === 'ARCHIVED'
        ? t('nsReviews.archivedReadOnly')
        : null

  return (
    <div className="space-y-8 animate-fade-up">
      <NamespaceManagementBack />
      {namespace ? <NamespaceHeader namespace={namespace} /> : null}
      {readOnlyMessage ? (
        <Card className="border-border/50 bg-secondary/40 p-4 text-sm text-muted-foreground">
          {readOnlyMessage}
        </Card>
      ) : null}
      <NamespaceManagementNav slug={slug} active="reviews" />
      <Tabs value={queueType} onValueChange={(value) => void navigate({
        to: '/dashboard/namespaces/$slug/reviews', params: { slug },
        search: { queue: value === 'sync' ? 'sync' : undefined }, replace: true,
      })}>
        <TabsList>
          <TabsTrigger data-analytics-id="review.namespace.user.tab" value="user">{t('reviews.typeSkill')}</TabsTrigger>
          <TabsTrigger data-analytics-id="review.namespace.sync.tab" value="sync">{t('reviews.typeSync')}</TabsTrigger>
        </TabsList>
        <TabsContent value="user" className="mt-5">
          <ReviewListSection namespaceId={namespace?.id} slug={slug} />
        </TabsContent>
        <TabsContent value="sync" className="mt-5">
          <GithubSyncReviewQueue namespaceId={namespace?.id} namespaceSlug={slug}
            enabled={queueType === 'sync' && !!namespace?.id} />
        </TabsContent>
      </Tabs>
    </div>
  )
}
