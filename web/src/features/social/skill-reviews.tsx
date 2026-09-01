import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Loader2, MessageSquare, ShieldAlert, Star } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Textarea } from '@/shared/ui/textarea'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { cn } from '@/shared/lib/utils'
import {
  type MySkillReview,
  type SkillReview,
  useClearSkillReview,
  useModerateSkillReview,
  useMySkillReview,
  useSkillReviews,
  useUpsertSkillReview,
} from './use-skill-reviews'

interface SkillReviewsProps {
  skillId: number
  canInteract: boolean
  onRequireLogin: () => void
}

function ReviewStars({ value, onChange, disabled = false }: {
  value: number
  onChange?: (value: number) => void
  disabled?: boolean
}) {
  const { t } = useTranslation()
  const ratingName = useId()

  if (!onChange) {
    return (
      <div
        className="flex items-center gap-1"
        role="img"
        aria-label={t('skillReviews.ratingDisplay', { score: value })}
      >
        {[1, 2, 3, 4, 5].map((score) => (
          <Star
            key={score}
            aria-hidden="true"
            className={cn(
              'h-4 w-4',
              score <= value ? 'fill-yellow-400 text-yellow-400' : 'text-muted-foreground/40',
            )}
          />
        ))}
      </div>
    )
  }

  return (
    <div className="flex items-center gap-1" role="radiogroup" aria-label={t('skillReviews.scoreLabel')}>
      {[1, 2, 3, 4, 5].map((score) => (
        <span key={score}>
          <input
            id={`${ratingName}-${score}`}
            className="peer sr-only"
            type="radio"
            name={ratingName}
            value={score}
            checked={score === value}
            onChange={() => onChange(score)}
            disabled={disabled}
            aria-label={t('skillReviews.ratingOption', { score })}
          />
          <label
            htmlFor={`${ratingName}-${score}`}
            className="block cursor-pointer rounded p-0.5 transition-transform hover:scale-110 peer-disabled:cursor-not-allowed peer-disabled:opacity-50 peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary"
          >
            <Star aria-hidden="true" className={cn(
              'h-4 w-4',
              score <= value ? 'fill-yellow-400 text-yellow-400' : 'text-muted-foreground/40',
            )} />
          </label>
        </span>
      ))}
    </div>
  )
}

function ReviewEditor({ skillId, review, onDone }: {
  skillId: number
  review?: MySkillReview
  onDone: () => void
}) {
  const { t } = useTranslation()
  const reviewTextId = useId()
  const [score, setScore] = useState(review?.rated ? review.score : 5)
  const [reviewText, setReviewText] = useState(review?.reviewText ?? '')
  const save = useUpsertSkillReview(skillId)
  const clear = useClearSkillReview(skillId)
  const pending = save.isPending || clear.isPending

  const handleSave = () => {
    const normalized = reviewText.trim()
    if (!normalized) {
      toast.error(t('skillReviews.textRequired'))
      return
    }
    save.mutate({ score, reviewText: normalized }, {
      onSuccess: () => {
        toast.success(t('skillReviews.saved'))
        onDone()
      },
      onError: (error) => toast.error(t('skillReviews.saveFailed'), error.message),
    })
  }

  const handleDelete = () => {
    clear.mutate(undefined, {
      onSuccess: () => {
        toast.success(t('skillReviews.deleted'))
        onDone()
      },
      onError: (error) => toast.error(t('skillReviews.deleteFailed'), error.message),
    })
  }

  return (
    <div className="space-y-4 rounded-xl border border-border/60 bg-secondary/20 p-4">
      <div className="flex items-center justify-between gap-3">
        <span className="text-sm font-medium">{t('skillReviews.scoreLabel')}</span>
        <ReviewStars value={score} onChange={setScore} disabled={pending} />
      </div>
      <label htmlFor={reviewTextId} className="sr-only">{t('skillReviews.reviewTextLabel')}</label>
      <Textarea
        id={reviewTextId}
        value={reviewText}
        onChange={(event) => setReviewText(event.target.value)}
        maxLength={2000}
        placeholder={t('skillReviews.placeholder')}
        disabled={pending}
      />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="text-xs text-muted-foreground">{reviewText.length}/2000</span>
        <div className="ml-auto flex flex-wrap justify-end gap-2">
          {review?.reviewed ? (
            <Button variant="ghost" size="sm" onClick={handleDelete} disabled={pending}>
              {t('skillReviews.delete')}
            </Button>
          ) : null}
          <Button variant="outline" size="sm" onClick={onDone} disabled={pending}>
            {t('skillReviews.cancel')}
          </Button>
          <Button size="sm" onClick={handleSave} disabled={pending || !reviewText.trim()}>
            {pending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            {t('skillReviews.save')}
          </Button>
        </div>
      </div>
    </div>
  )
}

function ReviewRow({ skillId, review, canModerate }: {
  skillId: number
  review: SkillReview
  canModerate: boolean
}) {
  const { t, i18n } = useTranslation()
  const moderation = useModerateSkillReview(skillId)
  const hidden = review.status === 'HIDDEN'
  const initials = review.displayName.trim().slice(0, 1).toUpperCase() || '?'

  const moderate = () => {
    moderation.mutate({ reviewId: review.id, action: hidden ? 'restore' : 'hide' }, {
      onSuccess: () => toast.success(t(hidden ? 'skillReviews.restored' : 'skillReviews.hidden')),
      onError: (error) => toast.error(t('skillReviews.moderationFailed'), error.message),
    })
  }

  return (
    <div className={cn('space-y-3 py-5 first:pt-0 last:pb-0', hidden && 'opacity-60')}>
      <div className="flex items-start gap-3">
        {review.avatarUrl ? (
          <img src={review.avatarUrl} alt="" className="h-9 w-9 rounded-full object-cover" />
        ) : (
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
            {initials}
          </div>
        )}
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="min-w-0 max-w-full flex flex-wrap items-center gap-2">
              <span className="min-w-0 max-w-full break-words font-medium text-foreground [overflow-wrap:anywhere]">{review.displayName}</span>
              <ReviewStars value={review.score} />
              {hidden ? (
                <span className="rounded-full bg-destructive/10 px-2 py-0.5 text-xs text-destructive">
                  {t('skillReviews.hiddenStatus')}
                </span>
              ) : null}
            </div>
            <span className="text-xs text-muted-foreground">
              {formatLocalDateTime(review.updatedAt, i18n.language)}
            </span>
          </div>
          <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-foreground/90 [overflow-wrap:anywhere]">{review.reviewText}</p>
          {hidden && review.moderationReason ? (
            <p className="mt-2 text-xs text-muted-foreground">
              {t('skillReviews.moderationReason', { reason: review.moderationReason })}
            </p>
          ) : null}
        </div>
      </div>
      {canModerate ? (
        <div className="flex justify-end">
          <Button variant="ghost" size="sm" onClick={moderate} disabled={moderation.isPending}>
            <ShieldAlert className="mr-2 h-4 w-4" />
            {t(hidden ? 'skillReviews.restore' : 'skillReviews.hide')}
          </Button>
        </div>
      ) : null}
    </div>
  )
}

export function SkillReviews({ skillId, canInteract, onRequireLogin }: SkillReviewsProps) {
  const { t } = useTranslation()
  const { isAuthenticated, hasRole } = useAuth()
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState(false)
  const reviews = useSkillReviews(skillId, page)
  const mine = useMySkillReview(skillId, isAuthenticated)
  const clearMine = useClearSkillReview(skillId)
  const canModerate = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')
  const totalPages = reviews.data ? Math.ceil(reviews.data.total / reviews.data.size) : 0

  const finishEditing = () => {
    setEditing(false)
    setPage(0)
  }

  const startEditing = () => {
    if (!isAuthenticated) {
      onRequireLogin()
      return
    }
    setEditing(true)
  }

  const deleteUnavailableReview = () => {
    clearMine.mutate(undefined, {
      onSuccess: () => {
        toast.success(t('skillReviews.deleted'))
        setPage(0)
      },
      onError: (error) => toast.error(t('skillReviews.deleteFailed'), error.message),
    })
  }

  return (
    <Card className="p-6 space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <MessageSquare className="h-5 w-5 text-primary" />
            <h2 className="font-heading text-lg font-semibold">{t('skillReviews.title')}</h2>
          </div>
          <p className="mt-1 text-sm text-muted-foreground">
            {t('skillReviews.count', { count: reviews.data?.total ?? 0 })}
          </p>
        </div>
        {canInteract && !editing ? (
          <Button variant="outline" onClick={startEditing}>
            {mine.data?.reviewed ? t('skillReviews.edit') : t('skillReviews.write')}
          </Button>
        ) : !canInteract && isAuthenticated && mine.data?.reviewed ? (
          <Button variant="outline" onClick={deleteUnavailableReview} disabled={clearMine.isPending}>
            {t('skillReviews.delete')}
          </Button>
        ) : null}
      </div>

      {mine.data?.status === 'HIDDEN' ? (
        <div className="rounded-xl border border-destructive/20 bg-destructive/5 p-3 text-sm text-muted-foreground">
          {t('skillReviews.yourReviewHidden')}
          {mine.data.moderationReason ? ` ${t('skillReviews.moderationReason', { reason: mine.data.moderationReason })}` : ''}
        </div>
      ) : null}

      {editing ? (
        <ReviewEditor
          key={`${mine.data?.reviewId ?? 'new'}-${mine.data?.updatedAt ?? ''}`}
          skillId={skillId}
          review={mine.data}
          onDone={finishEditing}
        />
      ) : null}

      {reviews.isLoading ? (
        <div className="flex items-center justify-center py-10 text-muted-foreground">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          {t('skillReviews.loading')}
        </div>
      ) : reviews.isError ? (
        <div className="rounded-xl border border-destructive/20 bg-destructive/5 p-4 text-sm text-destructive">
          {t('skillReviews.loadFailed')}
        </div>
      ) : reviews.data?.items.length ? (
        <div className="divide-y divide-border/50">
          {reviews.data.items.map((review) => (
            <ReviewRow key={review.id} skillId={skillId} review={review} canModerate={canModerate} />
          ))}
        </div>
      ) : (
        <div className="py-10 text-center text-sm text-muted-foreground">
          {t('skillReviews.empty')}
        </div>
      )}

      {page > 0 || totalPages > 1 ? (
        <div className="flex items-center justify-end gap-2 border-t border-border/50 pt-4">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>
            {t('skillReviews.previous')}
          </Button>
          <span className="text-xs text-muted-foreground">{page + 1}/{Math.max(totalPages, 1)}</span>
          <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)}>
            {t('skillReviews.next')}
          </Button>
        </div>
      ) : null}
    </Card>
  )
}
