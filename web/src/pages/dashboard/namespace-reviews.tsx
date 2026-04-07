import { useState } from 'react'
import { useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Card } from '@/shared/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { useNamespaceDetail } from '@/shared/hooks/use-namespace-queries'
import { useReviewList } from '@/features/review/use-review-list'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { NamespaceHeader } from '@/features/namespace/namespace-header'

type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
type TimeSortDirection = 'ASC' | 'DESC'
const PAGE_SIZE = 10

function ReviewListSection({ namespaceId }: { namespaceId?: number }) {
  const { t, i18n } = useTranslation()
  const [pages, setPages] = useState<Record<ReviewStatus, number>>({
    PENDING: 0,
    APPROVED: 0,
    REJECTED: 0,
  })
  const [activeStatus, setActiveStatus] = useState<ReviewStatus>('PENDING')
  const [sortDirection, setSortDirection] = useState<TimeSortDirection>('DESC')
  const pending = useReviewList('PENDING', namespaceId, pages.PENDING, PAGE_SIZE, sortDirection, activeStatus === 'PENDING')
  const approved = useReviewList('APPROVED', namespaceId, pages.APPROVED, PAGE_SIZE, sortDirection, activeStatus === 'APPROVED')
  const rejected = useReviewList('REJECTED', namespaceId, pages.REJECTED, PAGE_SIZE, sortDirection, activeStatus === 'REJECTED')

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
        <Pagination page={currentPage} totalPages={totalPages} onPageChange={(nextPage) => changePage(status, nextPage)} />
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
                <div className="text-sm text-muted-foreground">{t('nsReviews.version', { version: review.version })}</div>
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
          </div>
        ))}
        {query.data ? renderPagination(status, query.data.totalElements, query.data.totalPages) : null}
      </Card>
    )
  }

  return (
    <Tabs value={activeStatus} onValueChange={(value) => setActiveStatus(value as ReviewStatus)}>
      <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <TabsList>
          <TabsTrigger value="PENDING">{t('nsReviews.tabPending')}</TabsTrigger>
          <TabsTrigger value="APPROVED">{t('nsReviews.tabApproved')}</TabsTrigger>
          <TabsTrigger value="REJECTED">{t('nsReviews.tabRejected')}</TabsTrigger>
        </TabsList>
        <div className="w-full max-w-48">
          <p className="mb-2 text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">
            {t('nsReviews.sortLabel')}
          </p>
          <Select value={sortDirection} onValueChange={handleSortChange}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="DESC">{t('nsReviews.sortNewest')}</SelectItem>
              <SelectItem value="ASC">{t('nsReviews.sortOldest')}</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
      <TabsContent value="PENDING" className="mt-0">{renderItems(pending, 'PENDING')}</TabsContent>
      <TabsContent value="APPROVED" className="mt-0">{renderItems(approved, 'APPROVED')}</TabsContent>
      <TabsContent value="REJECTED" className="mt-0">{renderItems(rejected, 'REJECTED')}</TabsContent>
    </Tabs>
  )
}

export function NamespaceReviewsPage() {
  const { t } = useTranslation()
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
      <DashboardPageHeader
        title={t('nsReviews.title')}
        subtitle={namespace ? t('nsReviews.reviewsFor', { name: namespace.displayName }) : t('nsReviews.loadingNamespace')}
      />
      {namespace ? <NamespaceHeader namespace={namespace} /> : null}
      {readOnlyMessage ? (
        <Card className="border-border/50 bg-secondary/40 p-4 text-sm text-muted-foreground">
          {readOnlyMessage}
        </Card>
      ) : null}
      <ReviewListSection namespaceId={namespace?.id} />
    </div>
  )
}
