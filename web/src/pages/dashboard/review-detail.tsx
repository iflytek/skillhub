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
    return (
      <div className="space-y-6 max-w-3xl animate-fade-up">
        <div className="h-10 w-48 animate-shimmer rounded-lg" />
        <div className="h-64 animate-shimmer rounded-xl" />
      </div>
    )
  }

  if (!review) {
    return (
      <div className="text-center py-20 animate-fade-up">
        <h2 className="text-2xl font-bold font-heading mb-2">审核任务不存在</h2>
      </div>
    )
  }

  return (
    <div className="space-y-8 max-w-3xl animate-fade-up">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-4xl font-bold font-heading mb-2">审核详情</h1>
          <p className="text-muted-foreground">审核 ID: {review.id}</p>
        </div>
        <Button variant="outline" onClick={() => navigate({ to: '/dashboard/reviews' })}>
          返回列表
        </Button>
      </div>

      <Card className="p-8 space-y-6">
          <div className="grid grid-cols-2 gap-6">
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">命名空间/标识</Label>
            <p className="font-semibold font-mono">{review.namespace}/{review.skillSlug}</p>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">版本</Label>
            <p className="font-semibold">
              <span className="px-2.5 py-0.5 rounded-full bg-primary/10 text-primary text-sm font-mono">
                {review.version}
              </span>
            </p>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">状态</Label>
            <p className="font-semibold">
              {review.status === 'PENDING' && (
                <span className="px-2.5 py-0.5 rounded-full bg-amber-500/10 text-amber-400 text-sm">待审核</span>
              )}
              {review.status === 'APPROVED' && (
                <span className="px-2.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 text-sm">已通过</span>
              )}
              {review.status === 'REJECTED' && (
                <span className="px-2.5 py-0.5 rounded-full bg-red-500/10 text-red-400 text-sm">已拒绝</span>
              )}
            </p>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">提交者</Label>
            <p className="font-semibold">{review.submittedByName || review.submittedBy}</p>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">提交时间</Label>
            <p className="font-semibold text-muted-foreground">{formatDate(review.submittedAt)}</p>
          </div>
          {review.reviewedBy && (
            <>
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground uppercase tracking-wider">审核者</Label>
                <p className="font-semibold">{review.reviewedByName || review.reviewedBy}</p>
              </div>
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground uppercase tracking-wider">审核时间</Label>
                <p className="font-semibold text-muted-foreground">
                  {review.reviewedAt ? formatDate(review.reviewedAt) : '—'}
                </p>
              </div>
            </>
          )}
        </div>

        {review.reviewComment && (
          <div className="space-y-2">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">审核意见</Label>
            <p className="p-4 bg-secondary/50 rounded-xl text-sm leading-relaxed">{review.reviewComment}</p>
          </div>
        )}
      </Card>

      {review.status === 'PENDING' && (
        <Card className="p-8 space-y-6">
          <h2 className="text-xl font-bold font-heading">审核操作</h2>

          <div className="space-y-3">
            <Label htmlFor="comment" className="text-sm font-semibold font-heading">审核意见（可选）</Label>
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
