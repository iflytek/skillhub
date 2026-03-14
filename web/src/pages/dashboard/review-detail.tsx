import { useState } from 'react'
import { useNavigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Textarea } from '@/shared/ui/textarea'
import { Label } from '@/shared/ui/label'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { toast } from '@/shared/lib/toast'
import { useReviewDetail, useApproveReview, useRejectReview } from '@/features/review/use-review-detail'

export function ReviewDetailPage() {
  const { id } = useParams({ from: '/dashboard/reviews/$id' })
  const navigate = useNavigate()
  const { t, i18n } = useTranslation()
  const taskId = Number(id)

  const { data: review, isLoading } = useReviewDetail(taskId)
  const approveMutation = useApproveReview()
  const rejectMutation = useRejectReview()

  const [comment, setComment] = useState('')
  const [showRejectForm, setShowRejectForm] = useState(false)
  const [approveDialog, setApproveDialog] = useState(false)
  const [rejectDialog, setRejectDialog] = useState(false)

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString(i18n.language)
  }

  const handleApprove = async () => {
    approveMutation.mutate(
      { taskId, comment: comment || undefined },
      {
        onSuccess: () => {
          toast.success(t('review.approveSuccess'))
          navigate({ to: '/dashboard/reviews' })
        },
        onError: () => {
          toast.error(t('review.approveFailed'))
        },
      }
    )
  }

  const handleReject = async () => {
    if (!comment.trim()) {
      toast.error(t('review.rejectReasonRequired'))
      return
    }
    rejectMutation.mutate(
      { taskId, comment },
      {
        onSuccess: () => {
          toast.success(t('review.rejectSuccess'))
          navigate({ to: '/dashboard/reviews' })
        },
        onError: () => {
          toast.error(t('review.rejectFailed'))
        },
      }
    )
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
        <h2 className="text-2xl font-bold font-heading mb-2">{t('review.notFound')}</h2>
      </div>
    )
  }

  return (
    <div className="space-y-8 max-w-3xl animate-fade-up">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-4xl font-bold font-heading mb-2">{t('review.detail')}</h1>
          <p className="text-muted-foreground">{t('review.id')}: {review.id}</p>
        </div>
        <Button variant="outline" onClick={() => navigate({ to: '/dashboard/reviews' })}>
          {t('review.backToList')}
        </Button>
      </div>

      <Card className="p-8 space-y-6">
          <div className="grid grid-cols-2 gap-6">
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.namespace')}</Label>
            <p className="font-semibold font-mono">{review.namespace}/{review.skillSlug}</p>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.version')}</Label>
            <p className="font-semibold">
              <span className="px-2.5 py-0.5 rounded-full bg-primary/10 text-primary text-sm font-mono">
                {review.version}
              </span>
            </p>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.status')}</Label>
            <p className="font-semibold">
              {review.status === 'PENDING' && (
                <span className="px-2.5 py-0.5 rounded-full bg-amber-500/10 text-amber-400 text-sm">{t('review.statusPending')}</span>
              )}
              {review.status === 'APPROVED' && (
                <span className="px-2.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 text-sm">{t('review.statusApproved')}</span>
              )}
              {review.status === 'REJECTED' && (
                <span className="px-2.5 py-0.5 rounded-full bg-red-500/10 text-red-400 text-sm">{t('review.statusRejected')}</span>
              )}
            </p>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.submitter')}</Label>
            <p className="font-semibold">{review.submittedByName || review.submittedBy}</p>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.submitTime')}</Label>
            <p className="font-semibold text-muted-foreground">{formatDate(review.submittedAt)}</p>
          </div>
          {review.reviewedBy && (
            <>
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.reviewer')}</Label>
                <p className="font-semibold">{review.reviewedByName || review.reviewedBy}</p>
              </div>
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.reviewTime')}</Label>
                <p className="font-semibold text-muted-foreground">
                  {review.reviewedAt ? formatDate(review.reviewedAt) : '—'}
                </p>
              </div>
            </>
          )}
        </div>

        {review.reviewComment && (
          <div className="space-y-2">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.reviewComment')}</Label>
            <p className="p-4 bg-secondary/50 rounded-xl text-sm leading-relaxed">{review.reviewComment}</p>
          </div>
        )}
      </Card>

      {review.status === 'PENDING' && (
        <Card className="p-8 space-y-6">
          <h2 className="text-xl font-bold font-heading">{t('review.actions')}</h2>

          <div className="space-y-3">
            <Label htmlFor="comment" className="text-sm font-semibold font-heading">{t('review.commentLabel')}</Label>
            <Textarea
              id="comment"
              placeholder={t('review.commentPlaceholder')}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              rows={4}
            />
          </div>

          <div className="flex gap-3">
            <Button
              onClick={() => setApproveDialog(true)}
              disabled={approveMutation.isPending || rejectMutation.isPending}
            >
              {t('review.approve')}
            </Button>
            {!showRejectForm ? (
              <Button
                variant="destructive"
                onClick={() => setShowRejectForm(true)}
                disabled={approveMutation.isPending || rejectMutation.isPending}
              >
                {t('review.reject')}
              </Button>
            ) : (
              <>
                <Button
                  variant="destructive"
                  onClick={() => {
                    if (!comment.trim()) {
                      toast.error(t('review.rejectReasonRequired'))
                      return
                    }
                    setRejectDialog(true)
                  }}
                  disabled={approveMutation.isPending || rejectMutation.isPending || !comment.trim()}
                >
                  {t('review.confirmReject')}
                </Button>
                <Button
                  variant="outline"
                  onClick={() => setShowRejectForm(false)}
                  disabled={approveMutation.isPending || rejectMutation.isPending}
                >
                  {t('review.cancelReject')}
                </Button>
              </>
            )}
          </div>

          {showRejectForm && !comment.trim() && (
            <p className="text-sm text-destructive">{t('review.rejectReasonRequired')}</p>
          )}
        </Card>
      )}

      <ConfirmDialog
        open={approveDialog}
        onOpenChange={setApproveDialog}
        title={t('review.approveTitle')}
        description={t('review.approveDescription')}
        confirmText={t('review.approveConfirm')}
        onConfirm={handleApprove}
      />

      <ConfirmDialog
        open={rejectDialog}
        onOpenChange={setRejectDialog}
        title={t('review.rejectTitle')}
        description={t('review.rejectDescription')}
        confirmText={t('review.rejectConfirm')}
        variant="destructive"
        onConfirm={handleReject}
      />
    </div>
  )
}
