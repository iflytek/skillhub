import { useNavigate } from '@tanstack/react-router'
import { Card } from '@/shared/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import { useReviewList } from '@/features/review/use-review-list'

export function ReviewsPage() {
  const navigate = useNavigate()
  const { data: pendingReviews, isLoading: isPendingLoading } = useReviewList('PENDING')
  const { data: approvedReviews, isLoading: isApprovedLoading } = useReviewList('APPROVED')
  const { data: rejectedReviews, isLoading: isRejectedLoading } = useReviewList('REJECTED')

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('zh-CN')
  }

  const handleRowClick = (reviewId: number) => {
    navigate({ to: `/dashboard/reviews/${reviewId}` })
  }

  const renderReviewTable = (reviews: typeof pendingReviews, isLoading: boolean, status: string) => {
    if (isLoading) {
      return (
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="h-14 animate-shimmer rounded-lg" />
          ))}
        </div>
      )
    }

    if (!reviews || reviews.length === 0) {
      return (
        <Card className="p-12 text-center">
          <p className="text-muted-foreground">暂无审核任务</p>
        </Card>
      )
    }

    return (
      <Card className="overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>技能</TableHead>
              <TableHead>版本</TableHead>
              <TableHead>提交者</TableHead>
              <TableHead>提交时间</TableHead>
              {status !== 'PENDING' && <TableHead>审核者</TableHead>}
              {status !== 'PENDING' && <TableHead>审核时间</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {reviews.map((review) => (
              <TableRow
                key={review.id}
                className="cursor-pointer hover:bg-secondary/50 transition-colors"
                onClick={() => handleRowClick(review.id)}
              >
                <TableCell className="font-medium font-heading">
                  {review.namespace}/{review.skillSlug}
                </TableCell>
                <TableCell>
                  <span className="font-mono text-xs px-2 py-0.5 rounded-full bg-secondary/60">
                    {review.version}
                  </span>
                </TableCell>
                <TableCell>{review.submittedByName || review.submittedBy}</TableCell>
                <TableCell className="text-muted-foreground">{formatDate(review.submittedAt)}</TableCell>
                {status !== 'PENDING' && (
                  <TableCell>{review.reviewedByName || review.reviewedBy || '—'}</TableCell>
                )}
                {status !== 'PENDING' && (
                  <TableCell className="text-muted-foreground">
                    {review.reviewedAt ? formatDate(review.reviewedAt) : '—'}
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    )
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="text-4xl font-bold font-heading mb-2">审核中心</h1>
        <p className="text-muted-foreground text-lg">管理技能版本审核</p>
      </div>

      <Tabs defaultValue="PENDING">
        <TabsList>
          <TabsTrigger value="PENDING">待审核</TabsTrigger>
          <TabsTrigger value="APPROVED">已通过</TabsTrigger>
          <TabsTrigger value="REJECTED">已拒绝</TabsTrigger>
        </TabsList>

        <TabsContent value="PENDING" className="mt-6">
          {renderReviewTable(pendingReviews, isPendingLoading, 'PENDING')}
        </TabsContent>

        <TabsContent value="APPROVED" className="mt-6">
          {renderReviewTable(approvedReviews, isApprovedLoading, 'APPROVED')}
        </TabsContent>

        <TabsContent value="REJECTED" className="mt-6">
          {renderReviewTable(rejectedReviews, isRejectedLoading, 'REJECTED')}
        </TabsContent>
      </Tabs>
    </div>
  )
}
