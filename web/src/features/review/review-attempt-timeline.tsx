import { useTranslation } from 'react-i18next'
import type { ReviewTask } from '@/api/types'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { cn } from '@/shared/lib/utils'

const statusClassNames: Record<ReviewTask['status'], string> = {
  PENDING: 'border-amber-500/25 bg-amber-500/10 text-amber-800 dark:text-amber-300',
  APPROVED: 'border-emerald-500/25 bg-emerald-500/10 text-emerald-800 dark:text-emerald-300',
  REJECTED: 'border-red-500/25 bg-red-500/10 text-red-800 dark:text-red-300',
}

function statusKey(status: ReviewTask['status']) {
  if (status === 'PENDING') return 'reviewProgress.statusPending'
  if (status === 'APPROVED') return 'reviewProgress.statusApproved'
  return 'reviewProgress.statusRejected'
}

export function ReviewAttemptTimeline({
  attempts,
  locale,
}: {
  attempts: ReviewTask[]
  locale: string
}) {
  const { t } = useTranslation()

  return (
    <ol className="space-y-3">
      {attempts.map((attempt, index) => (
        <li
          key={attempt.id}
          className="grid gap-3 rounded-lg border border-border/60 bg-background/70 p-3 text-sm md:grid-cols-[auto_1fr_auto] md:items-start"
        >
          <span className="font-medium text-foreground">
            {t('reviewProgress.attemptNumber', { number: attempts.length - index })}
          </span>
          <div className="min-w-0">
            <span className={cn(
              'inline-flex rounded-full border px-2 py-0.5 text-xs font-medium',
              statusClassNames[attempt.status],
            )}>
              {t(statusKey(attempt.status))}
            </span>
            {attempt.reviewComment ? (
              <p className="mt-2 whitespace-pre-wrap text-foreground/85">{attempt.reviewComment}</p>
            ) : null}
            {attempt.reviewedBy ? (
              <p className="mt-2 text-xs text-muted-foreground">
                {t('reviewProgress.reviewedBy', {
                  reviewer: attempt.reviewedByName || attempt.reviewedBy,
                })}
              </p>
            ) : null}
          </div>
          <div className="space-y-1 text-xs text-muted-foreground md:text-right">
            <time dateTime={attempt.submittedAt}>
              {t('reviewProgress.submittedAt', {
                time: formatLocalDateTime(attempt.submittedAt, locale),
              })}
            </time>
            {attempt.reviewedAt ? (
              <time className="block" dateTime={attempt.reviewedAt}>
                {t('reviewProgress.reviewedAt', {
                  time: formatLocalDateTime(attempt.reviewedAt, locale),
                })}
              </time>
            ) : null}
          </div>
        </li>
      ))}
    </ol>
  )
}
