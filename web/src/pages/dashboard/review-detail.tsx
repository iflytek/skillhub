import { useState } from 'react'
import { useNavigate, useParams } from '@tanstack/react-router'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Textarea } from '@/shared/ui/textarea'
import { Label } from '@/shared/ui/label'
import { useReviewDetail, useApproveReview, useRejectReview } from '@/features/review/use-review-detail'

export function ReviewDetailPage() {
  const { id } = useParams({ from: '/dashboard/reviews/$id' })
  const navigate = useNavigate()
  const taskId = Number(id)

  const { data: review, isLoading } = useReviewDetail(taskId)
  const approveMutation = useApproveReview()
  const rejectMutation = useRejectReview()

  const [comment, setComment] = useState('')
  const [showRejectForm, setShowRejectForm] = useState(false)

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('zh-CN')
  }

  const handleApprove = () => {
    if (window.confirm('确定要通过这个审核吗？')) {
      approveMutation.mutate(
        { taskId, comment: comment || undefined },
        {
          onSuccess: () => {
            navigate({ to: '/dashboard/reviews' })
          },
        }
      )
    }
  }

  const handleReject = () => {
    if (!comment.trim()) {
      alert('拒绝审核时必须填写原因')
      return
    }
    if (window.confirm('确定要拒绝这个审核吗？')) {
      rejectMutation.mutate(
        { taskId, comment },
        {
          onSuccess: () => {
            navigate({ to: '/dashboard/reviews' })
          },
        }
      )
    }
  }

  if (isLoading) {
    return <div className="text-center py-8 text-muted-foreground">加载中...</div>
  }

  if (!review) {
    return <div className="text-center py-8 text-muted-foreground">审核任务不存在</div>
  }

  return (
    <div className="space-y-6 max-w-3xl">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold mb-2">审核详情</h1>
          <p className="text-muted-foreground">审核 ID: {review.id}</p>
        </div>
        <Button variant="outline" onClick={() => navigate({ to: '/dashboard/reviews' })}>
          返回列表
        </Button>
      </div>

      <Card className="p-6 space-y-4">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <Label className="text-muted-foreground">技能名称</Label>
            <p className="font-medium">{review.skillName}</p>
          </div>
          <div>
            <Label className="text-muted-foreground">命名空间/标识</Label>
            <p className="font-medium">{review.namespace}/{review.skillSlug}</p>
          </div>
          <div>
            <Label className="text-muted-foreground">版本</Label>
            <p className="font-medium">{review.version}</p>
          </div>
          <div>
            <Label className="text-muted-foreground">状态</Label>
            <p className="font-medium">
              {review.status === 'PENDING' && '待审核'}
              {review.status === 'APPROVED' && '已通过'}
              {review.status === 'REJECTED' && '已拒绝'}
            </p>
          </div>
          <div>
            <Label className="text-muted-foreground">提交者</Label>
            <p className="font-medium">{review.submittedBy}</p>
          </div>
          <div>
            <Label className="text-muted-foreground">提交时间</Label>
            <p className="font-medium">{formatDate(review.submittedAt)}</p>
          </div>
          {review.reviewedBy && (
            <>
              <div>
                <Label className="text-muted-foreground">审核者</Label>
                <p className="font-medium">{review.reviewedBy}</p>
              </div>
              <div>
                <Label className="text-muted-foreground">审核时间</Label>
                <p className="font-medium">
                  {review.reviewedAt ? formatDate(review.reviewedAt) : '-'}
                </p>
              </div>
            </>
          )}
        </div>

        {review.comment && (
          <div>
            <Label className="text-muted-foreground">审核意见</Label>
            <p className="mt-2 p-3 bg-muted rounded-md">{review.comment}</p>
          </div>
        )}
      </Card>

      {review.status === 'PENDING' && (
        <Card className="p-6 space-y-4">
          <h2 className="text-xl font-semibold">审核操作</h2>

          <div className="space-y-2">
            <Label htmlFor="comment">审核意见（可选）</Label>
            <Textarea
              id="comment"
              placeholder="填写审核意见..."
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              rows={4}
            />
          </div>

          <div className="flex gap-3">
            <Button
              onClick={handleApprove}
              disabled={approveMutation.isPending || rejectMutation.isPending}
            >
              通过审核
            </Button>
            {!showRejectForm ? (
              <Button
                variant="destructive"
                onClick={() => setShowRejectForm(true)}
                disabled={approveMutation.isPending || rejectMutation.isPending}
              >
                拒绝审核
              </Button>
            ) : (
              <>
                <Button
                  variant="destructive"
                  onClick={handleReject}
                  disabled={approveMutation.isPending || rejectMutation.isPending || !comment.trim()}
                >
                  确认拒绝
                </Button>
                <Button
                  variant="outline"
                  onClick={() => setShowRejectForm(false)}
                  disabled={approveMutation.isPending || rejectMutation.isPending}
                >
                  取消
                </Button>
              </>
            )}
          </div>

          {showRejectForm && !comment.trim() && (
            <p className="text-sm text-destructive">拒绝审核时必须填写原因</p>
          )}
        </Card>
      )}
    </div>
  )
}
