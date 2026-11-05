import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { ReviewSkillDetail, ReviewTask } from '@/api/types'
import { SkillAvatar } from '@/features/skill/skill-avatar'

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

export function ReviewTaskHeader({ review, detail, title, hasBaseline, submittedAt, resourceVisibility, children }: {
  review: ReviewTask
  detail?: ReviewSkillDetail
  title: string
  hasBaseline: boolean
  submittedAt: string
  resourceVisibility?: string | null
  children?: ReactNode
}) {
  const { t } = useTranslation()
  const kind = reviewRequestKey(review, hasBaseline)
  const metadataOnly = review.subjectType === 'SKILL_METADATA' && !review.requestedVisibility
  const version = metadataOnly ? null : detail?.activeVersion ?? review.version
  const visibility = review.requestedVisibility
    ? `${t(`skillVisibility.${review.previousVisibility}`)} → ${t(`skillVisibility.${review.requestedVisibility}`)}`
    : (resourceVisibility || detail?.skill.visibility) ? t(`skillVisibility.${resourceVisibility || detail?.skill.visibility}`) : null
  const rows = [
    { label: t('reviewWorkbench.request'), value: t(`reviewWorkbench.kind.${kind}`) },
    ...(version ? [{ label: t(review.requestedVisibility ? 'reviewWorkbench.boundVersion' : 'review.version'), value: version }] : []),
    ...(visibility ? [{ label: t('skillVisibility.title'), value: visibility }] : []),
    { label: t('review.submitter'), value: review.submittedByName || review.submittedBy },
    { label: t('review.submitTime'), value: submittedAt },
  ]
  return (
    <header data-testid="review-task-header" className="min-w-0 space-y-5 border-b border-border/60 pb-5">
      <div className="flex min-w-0 items-start gap-4">
        <SkillAvatar name={title} url={detail?.skill.avatarUrl} seed={`${review.namespace}/${review.subjectSlug || review.skillSlug}`} size="h-11 w-11 text-lg" />
        <div className="min-w-0 flex-1 space-y-2">
          <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
            <h1 className="min-w-0 break-words text-[27px] font-medium leading-9 tracking-[-0.8px]">{title}</h1>
            <ReviewTaskStatus status={review.status} />
          </div>
          <p className="break-all text-xs leading-5 text-muted-foreground">@{review.namespace}/{review.subjectSlug || review.skillSlug}</p>
        </div>
      </div>
      <dl className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,9rem),1fr))] gap-x-6 gap-y-4">
        {rows.map(({ label, value }) => <div key={label} className="min-w-0 space-y-1.5">
          <dt className="text-xs leading-5 text-muted-foreground">{label}</dt>
          <dd className="break-words text-sm leading-5 [overflow-wrap:anywhere]">{value}</dd>
        </div>)}
      </dl>
      <div className="flex min-w-0 flex-wrap items-center justify-between gap-x-6 gap-y-3">
        {children}
        <span className="ml-auto shrink-0 text-xs text-muted-foreground">{t('review.id')} #{review.id}</span>
      </div>
    </header>
  )
}
