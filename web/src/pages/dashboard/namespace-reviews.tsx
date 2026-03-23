import { useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Card } from '@/shared/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { useNamespaceDetail } from '@/shared/hooks/use-namespace-queries'
import { useReviewList } from '@/features/review/use-review-list'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { NamespaceHeader } from '@/features/namespace/namespace-header'

function ReviewListSection({ namespaceId }: { namespaceId?: number }) {
  const { t, i18n } = useTranslation()
  const { data: pending } = useReviewList('PENDING', namespaceId)
  const { data: approved } = useReviewList('APPROVED', namespaceId)
  const { data: rejected } = useReviewList('REJECTED', namespaceId)

  const renderItems = (items?: typeof pending) => {
    const list = items?.items
    if (!list || list.length === 0) {
      return <Card className="p-10 text-center text-muted-foreground">{t('nsReviews.empty')}</Card>
    }
    return (
      <Card className="divide-y divide-border/40">
        {list.map((review) => (
          <div key={review.id} className="p-5">
            <div className="flex items-center justify-between gap-4">
              <div>
                <div className="font-semibold font-heading">{review.namespace}/{review.skillSlug}</div>
                <div className="text-sm text-muted-foreground">{t('nsReviews.version', { version: review.version })}</div>
              </div>
              <div className="text-sm text-muted-foreground">{formatLocalDateTime(review.submittedAt, i18n.language)}</div>
            </div>
            {review.reviewComment ? (
              <p className="mt-3 text-sm text-muted-foreground">{review.reviewComment}</p>
            ) : null}
          </div>
        ))}
      </Card>
    )
  }

  return (
    <Tabs defaultValue="PENDING">
      <TabsList>
        <TabsTrigger value="PENDING">{t('nsReviews.tabPending')}</TabsTrigger>
        <TabsTrigger value="APPROVED">{t('nsReviews.tabApproved')}</TabsTrigger>
        <TabsTrigger value="REJECTED">{t('nsReviews.tabRejected')}</TabsTrigger>
      </TabsList>
      <TabsContent value="PENDING" className="mt-6">{renderItems(pending)}</TabsContent>
      <TabsContent value="APPROVED" className="mt-6">{renderItems(approved)}</TabsContent>
      <TabsContent value="REJECTED" className="mt-6">{renderItems(rejected)}</TabsContent>
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
