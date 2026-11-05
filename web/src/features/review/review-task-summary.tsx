import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { ReviewSkillDetail, ReviewTask } from '@/api/types'
import { Card } from '@/shared/ui/card'

export function reviewRequestKey(review: ReviewTask, hasBaseline: boolean) {
  if (review.subjectType === 'SUITE_VERSION') return 'suite'
  if (review.requestedVisibility) return 'visibility'
  if (review.subjectType === 'SKILL_METADATA') return review.avatarRemoval ? 'avatarRemoval' : 'avatar'
  if (review.originType === 'GITHUB_SYNC') return 'sync'
  if (review.reviewKind === 'VERSION_UPDATE') return 'update'
  if (review.reviewKind === 'FIRST_PUBLISH') return 'publish'
  return hasBaseline ? 'update' : 'publish'
}

export function ReviewTaskStatus({ status }: { status: ReviewTask['status'] }) {
  const { t } = useTranslation()
  const color = status === 'APPROVED' ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400'
    : status === 'REJECTED' ? 'border-red-500/30 bg-red-500/10 text-red-700 dark:text-red-400'
      : status === 'PENDING' ? 'border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-400'
        : 'border-border/60 bg-secondary text-muted-foreground'
  return <span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-medium ${color}`}>{t(status === 'PENDING' ? 'review.statusPending' : status === 'APPROVED' ? 'review.statusApproved' : status === 'REJECTED' ? 'review.statusRejected' : 'review.statusSuperseded')}</span>
}

export function ReviewTaskSummary({ review, detail, hasBaseline, submittedAt, children }: {
  review: ReviewTask
  detail?: ReviewSkillDetail
  hasBaseline: boolean
  submittedAt: string
  children?: ReactNode
}) {
  const { t } = useTranslation()
  const kind = reviewRequestKey(review, hasBaseline)
  const metadataOnly = review.subjectType === 'SKILL_METADATA' && !review.requestedVisibility
  const rows = [
    { label: t('reviewWorkbench.request'), value: t(`reviewWorkbench.kind.${kind}`) },
    ...(!metadataOnly ? [{ label: t(review.requestedVisibility ? 'reviewWorkbench.boundVersion' : 'review.version'), value: detail?.activeVersion ?? review.version ?? '—' }] : []),
    ...(review.requestedVisibility ? [{ label: t('skillVisibility.title'), value: `${t(`skillVisibility.${review.previousVisibility}`)} → ${t(`skillVisibility.${review.requestedVisibility}`)}` }]
      : detail ? [{ label: t('skillVisibility.title'), value: t(`skillVisibility.${detail.skill.visibility}`) }] : []),
    { label: t('review.submitter'), value: review.submittedByName || review.submittedBy },
    { label: t('review.submitTime'), value: submittedAt },
  ]
  return (
    <aside className="min-w-0 self-start xl:sticky xl:top-20" aria-label={t('reviewWorkbench.requestSummary')}>
      <Card className="space-y-5 p-5">
        <h2 className="text-lg font-semibold">{t('reviewWorkbench.requestSummary')}</h2>
        <dl className="space-y-4">
          {rows.map(({ label, value }) => <div key={label} className="grid grid-cols-[4rem_minmax(0,1fr)] gap-3 text-sm">
            <dt className="text-xs leading-5 text-muted-foreground">{label}</dt>
            <dd className="min-w-0 break-words leading-5 [overflow-wrap:anywhere]">{value}</dd>
          </div>)}
        </dl>
        {children && <div className="space-y-4 border-t border-border/60 pt-5">{children}</div>}
      </Card>
    </aside>
  )
}
