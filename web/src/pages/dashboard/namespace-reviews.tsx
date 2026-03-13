import { useParams } from '@tanstack/react-router'
import { Card } from '@/shared/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { useNamespaceDetail } from '@/shared/hooks/use-skill-queries'
import { useReviewList } from '@/features/review/use-review-list'

function ReviewListSection({ namespaceId }: { namespaceId?: number }) {
  const { data: pending } = useReviewList('PENDING', namespaceId)
  const { data: approved } = useReviewList('APPROVED', namespaceId)
  const { data: rejected } = useReviewList('REJECTED', namespaceId)

  const renderItems = (items?: typeof pending) => {
    if (!items || items.length === 0) {
      return <Card className="p-10 text-center text-muted-foreground">暂无审核记录</Card>
    }
    return (
      <Card className="divide-y divide-border/40">
        {items.map((review) => (
          <div key={review.id} className="p-5">
            <div className="flex items-center justify-between gap-4">
              <div>
                <div className="font-semibold font-heading">{review.namespace}/{review.skillSlug}</div>
                <div className="text-sm text-muted-foreground">版本 {review.version}</div>
              </div>
              <div className="text-sm text-muted-foreground">{new Date(review.submittedAt).toLocaleString('zh-CN')}</div>
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
        <TabsTrigger value="PENDING">待审核</TabsTrigger>
        <TabsTrigger value="APPROVED">已通过</TabsTrigger>
        <TabsTrigger value="REJECTED">已拒绝</TabsTrigger>
      </TabsList>
      <TabsContent value="PENDING" className="mt-6">{renderItems(pending)}</TabsContent>
      <TabsContent value="APPROVED" className="mt-6">{renderItems(approved)}</TabsContent>
      <TabsContent value="REJECTED" className="mt-6">{renderItems(rejected)}</TabsContent>
    </Tabs>
  )
}

export function NamespaceReviewsPage() {
  const { slug } = useParams({ from: '/dashboard/namespaces/$slug/reviews' })
  const { data: namespace } = useNamespaceDetail(slug)

  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="text-4xl font-bold font-heading mb-2">命名空间审核</h1>
        <p className="text-muted-foreground text-lg">
          {namespace ? `${namespace.displayName} 的审核任务` : '加载命名空间信息中'}
        </p>
      </div>
      <ReviewListSection namespaceId={namespace?.id} />
    </div>
  )
}
