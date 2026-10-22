import { useEffect, useState } from 'react'
import { useNavigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import {
  buildGlobalReviewsPath,
  buildNamespaceReviewDetailPath,
  buildNamespaceReviewsPath,
  canAccessGlobalReviewCenter,
} from '@/features/review/review-paths'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Textarea } from '@/shared/ui/textarea'
import { Label } from '@/shared/ui/label'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { toast } from '@/shared/lib/toast'
import { resolveReviewActionErrorDescription } from '@/features/review/review-error'
import { ReviewAvatarChangeSection } from '@/features/review/review-avatar-change-section'
import { ReviewSkillChangesSection, ReviewSkillDetailSection } from '@/features/review/review-skill-detail-section'
import { ReviewAttemptTimeline } from '@/features/review/review-attempt-timeline'
import { useReviewFile } from '@/features/review/use-review-file'
import { GithubSyncReviewDiff, GithubSyncReviewEvidence } from '@/features/review/github-sync-review-source'
import { SecurityAuditSection } from '@/features/security-audit/security-audit-section'
import { useSecurityAudits } from '@/features/security-audit/use-security-audit'
import { getSecurityAuditDisplayState } from '@/features/security-audit/display-state'
import { VerdictBadge } from '@/features/security-audit/verdict-badge'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { useSuiteDetail } from '@/shared/hooks/use-suite-queries'
import { suiteBlockingReasonLabel } from '@/features/suite/suite-labels'
import {
  useReviewAttempts,
  useReviewDetail,
  useReviewSkillDetail,
  useGithubSyncReviewSource,
  useApproveReview,
  useRejectReview,
} from '@/features/review/use-review-detail'

/**
 * Review task detail page for moderators. The route owns the approve/reject
 * interaction state because both actions depend on route-local confirmation
 * dialogs, comment input, and redirect behavior after completion.
 */
function ReviewDetailScreen({
  taskId,
  backTo,
  namespaceSlug,
}: {
  taskId: number
  backTo: string
  namespaceSlug?: string
}) {
  const navigate = useNavigate()
  const { t, i18n } = useTranslation()
  const { user } = useAuth()

  const { data: review, isLoading } = useReviewDetail(taskId)
  const isSuiteReview = review?.subjectType === 'SUITE_VERSION'
  const isMetadataReview = review?.subjectType === 'SKILL_METADATA' && !review.requestedVisibility
  const isGithubSyncReview = review?.originType === 'GITHUB_SYNC'
  const returnTo = isGithubSyncReview
    ? `${backTo}?${namespaceSlug ? 'queue' : 'type'}=sync` : backTo
  const { data: githubSource, isLoading: isLoadingGithubSource, isError: isGithubSourceError } =
    useGithubSyncReviewSource(taskId, isGithubSyncReview)
  const [selectedTab, setSelectedTab] = useState<string | null>(null)
  const activeTab = selectedTab ?? (isGithubSyncReview && githubSource?.baseVersionId ? 'changes' : 'content')
  const {
    data: reviewAttempts,
    isLoading: isLoadingReviewAttempts,
    isError: isReviewAttemptsError,
  } = useReviewAttempts(taskId, activeTab === 'history')
  const needsLicenseBasis = isGithubSyncReview && review?.namespace === 'github'
    && Boolean(githubSource) && githubSource?.evidence.status !== 'RECOGNIZED'
  const {
    data: reviewSkillDetail,
    isLoading: isLoadingReviewSkillDetail,
    error: reviewSkillDetailError,
  } = useReviewSkillDetail(taskId, Boolean(review) && !isSuiteReview, false)
  const documentationFile = reviewSkillDetail?.files.find(
    (file) => file.filePath === reviewSkillDetail.documentationPath,
  )
  const documentationTooLarge = Boolean(documentationFile && documentationFile.fileSize > 256 * 1024)
  const shouldLoadDocumentation = !isMetadataReview && activeTab === 'content' && Boolean(reviewSkillDetail?.documentationPath)
    && !documentationTooLarge && (!isGithubSyncReview || Boolean(githubSource) || isGithubSourceError)
  const {
    data: documentationContent,
    isLoading: isLoadingDocumentation,
    error: documentationError,
  } = useReviewFile(taskId, reviewSkillDetail?.documentationPath ?? null, shouldLoadDocumentation)
  const contentDetail = reviewSkillDetail && documentationContent !== undefined
    ? { ...reviewSkillDetail, documentationContent }
    : reviewSkillDetail
  const activeReviewVersion = reviewSkillDetail?.versions?.find(
    (version) => version.version === reviewSkillDetail.activeVersion
  )
  const isApprovalBlockedByScanning = !isMetadataReview && activeReviewVersion?.status === 'SCANNING'
  const skillId = reviewSkillDetail?.skill?.id
  const versionId = reviewSkillDetail?.versions?.find((version) => version.version === review?.version)?.id
    ?? review?.skillVersionId ?? undefined
  const { data: securityAudits, isLoading: isLoadingSecurityAudits, isError: isSecurityAuditsError } =
    useSecurityAudits(isMetadataReview ? undefined : skillId, isMetadataReview ? undefined : versionId)
  const { data: reviewSuiteDetail, isLoading: isLoadingReviewSuiteDetail, refetch: refetchReviewSuiteDetail } = useSuiteDetail(
    review?.namespace || '',
    review?.subjectSlug || '',
    review?.version ?? undefined,
    isSuiteReview,
  )
  const approveMutation = useApproveReview({
    onSuccess: () => {
      toast.success(t('review.approveSuccess'))
      navigate({ to: returnTo })
    },
    onError: (error) => {
      toast.error(t('review.approveFailed'), resolveReviewActionErrorDescription(error))
    },
  })
  const rejectMutation = useRejectReview({
    onSuccess: () => {
      toast.success(t('review.rejectSuccess'))
      navigate({ to: returnTo })
    },
    onError: (error) => {
      toast.error(t('review.rejectFailed'), resolveReviewActionErrorDescription(error))
    },
  })

  const [comment, setComment] = useState('')
  const [showRejectForm, setShowRejectForm] = useState(false)
  const [approveDialog, setApproveDialog] = useState(false)
  const [rejectDialog, setRejectDialog] = useState(false)
  useEffect(() => setSelectedTab(null), [taskId])
  const hasGlobalReviewAccess = canAccessGlobalReviewCenter(user?.platformRoles)
  const shouldRedirectToNamespaceRoute =
    !namespaceSlug &&
    !!review &&
    review.namespace !== 'global' &&
    !hasGlobalReviewAccess

  const formatDate = (dateString: string) => {
    return formatLocalDateTime(dateString, i18n.language)
  }

  useEffect(() => {
    if (!shouldRedirectToNamespaceRoute || !review) {
      return
    }

    void navigate({
      to: buildNamespaceReviewDetailPath(review.namespace, review.id),
      replace: true,
    })
  }, [navigate, review, shouldRedirectToNamespaceRoute])

  const isApprovalBlockedByAvatar = reviewSkillDetail?.avatarChange?.readyForApproval === false || reviewSkillDetail?.visibilityChange?.readyForApproval === false
  const isAvatarOverrideReasonRequired = reviewSkillDetail?.avatarChange?.machineStatus === 'BLOCKED' && !comment.trim()
  const isApprovalBlocked = isApprovalBlockedByScanning || isApprovalBlockedByAvatar || isAvatarOverrideReasonRequired
    || (isSuiteReview ? !reviewSuiteDetail : isLoadingReviewSkillDetail || !!reviewSkillDetailError || !reviewSkillDetail)
    || (isGithubSyncReview && !githubSource)

  const handleApprove = async () => {
    if (isApprovalBlocked) {
      return
    }
    if (needsLicenseBasis && !comment.trim()) {
      toast.error(t('review.githubLicenseBasisRequired'))
      return
    }
    approveMutation.mutate({ taskId, comment: comment || undefined })
  }

  const handleReject = async () => {
    if (!comment.trim()) {
      toast.error(t('review.rejectReasonRequired'))
      return
    }
    rejectMutation.mutate({ taskId, comment })
  }

  if (isLoading) {
    return (
      <div className="space-y-6 max-w-3xl animate-fade-up">
        <div className="h-10 w-48 animate-shimmer rounded-lg" />
        <div className="h-64 animate-shimmer rounded-xl" />
      </div>
    )
  }

  if (shouldRedirectToNamespaceRoute) {
    return null
  }

  if (!review) {
    return (
      <div className="text-center py-20 animate-fade-up">
        <h2 className="text-2xl font-bold font-heading mb-2">{t('review.notFound')}</h2>
      </div>
    )
  }

  const hasNamespaceMismatch = Boolean(namespaceSlug && review.namespace !== namespaceSlug)

  if (hasNamespaceMismatch) {
    return (
      <div className="space-y-6 max-w-3xl animate-fade-up">
        <div className="text-center py-20">
          <h2 className="text-2xl font-bold font-heading mb-2">{t('review.notFound')}</h2>
        </div>
        <div className="flex justify-center">
          <Button data-analytics-id="review.list.open" variant="outline" onClick={() => navigate({ to: returnTo })}>
            {t('review.backToList')}
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto flex min-h-[calc(100dvh-5.25rem)] w-full max-w-[1440px] flex-col gap-4 animate-fade-up">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="mb-1 text-xs text-muted-foreground">
            {t(isSuiteReview ? 'suite.resourceTypeSuite' : 'suite.resourceTypeSkill')} · {t('review.detail')} · {t('review.id')}: {review.id}
          </p>
          <h1 className="break-words text-xl font-semibold tracking-tight md:text-2xl">
            {reviewSkillDetail?.skill.displayName || review.subjectSlug || review.skillSlug || t('review.detail')}
          </h1>
          <p className="mt-1 break-all text-xs text-muted-foreground">
            {review.namespace}/{review.subjectSlug || review.skillSlug}
          </p>
        </div>
        <Button data-analytics-id="review.list.open" variant="outline" size="sm" className="px-2.5" onClick={() => navigate({ to: returnTo })}>
          {t('review.backToList')}
        </Button>
      </div>

      <Card className="space-y-3 p-4">
        {reviewSkillDetail?.skill.summary && (
          <p className="max-w-3xl whitespace-pre-wrap text-sm leading-6 text-foreground/80">{reviewSkillDetail.skill.summary}</p>
        )}
        <dl className={`grid gap-x-4 gap-y-3 text-xs sm:grid-cols-2 ${isGithubSyncReview ? 'lg:grid-cols-5' : 'lg:grid-cols-4'}`}>
          <div className="min-w-0">
            <dt className="text-muted-foreground">{t('review.status')}</dt>
            <dd className="mt-1 font-semibold">
              <span className={review.status === 'APPROVED' ? 'text-emerald-600' : review.status === 'REJECTED' ? 'text-destructive' : 'text-amber-600'}>
                {t(review.status === 'PENDING' ? 'review.statusPending' : review.status === 'APPROVED' ? 'review.statusApproved' : review.status === 'REJECTED' ? 'review.statusRejected' : 'review.statusSuperseded')}
              </span>
            </dd>
          </div>
          {isGithubSyncReview && (
            <div className="min-w-0">
              <dt className="text-muted-foreground">{t('review.githubRevision')}</dt>
              <dd className="mt-1 font-semibold">{githubSource ? t('review.githubRevisionValue', { count: githubSource.syncRevision }) : '—'}</dd>
            </div>
          )}
          <div className="min-w-0">
            <dt className="text-muted-foreground">{isGithubSyncReview ? t('review.githubRepository') : t('review.version')}</dt>
            <dd className="mt-1 break-all font-medium">
              {isGithubSyncReview ? githubSource ? `${githubSource.ownerLogin}/${githubSource.repositoryName}` : '—' : review.requestedVisibility ? t('skillVisibility.title') : isMetadataReview ? t('review.metadataChange') : review.version}
            </dd>
          </div>
          <div className="min-w-0">
            <dt className="text-muted-foreground">{isGithubSyncReview ? t('review.githubCommit') : t('review.submitter')}</dt>
            <dd className="mt-1 break-all font-medium">
              {isGithubSyncReview ? githubSource?.commitSha.slice(0, 10) || '—' : review.submittedByName || review.submittedBy}
            </dd>
          </div>
          <div className="min-w-0">
            <dt className="text-muted-foreground">{t('review.submitTime')}</dt>
            <dd className="mt-1 font-medium">{formatDate(review.submittedAt)}</dd>
          </div>
        </dl>
        {!isSuiteReview && !isMetadataReview && (
          <div className="flex flex-wrap gap-2 border-t pt-3 text-xs">
            <button data-analytics-id="review.security.open" type="button" onClick={() => setSelectedTab('source')}
              className="inline-flex min-h-8 flex-wrap items-center gap-2 rounded border border-border bg-muted/20 px-2.5 py-1 text-left hover:bg-muted/50">
              <span className="font-medium">{t('review.scanSummary')}</span>
              {isLoadingReviewSkillDetail || (skillId && versionId && isLoadingSecurityAudits)
                ? <span className="text-muted-foreground">{t('review.scanLoading')}</span>
                : reviewSkillDetailError || isSecurityAuditsError
                  ? <span className="text-destructive">{t('review.scanLoadFailed')}</span>
                  : !skillId || !versionId ? <span className="text-muted-foreground">{t('review.scanUnavailable')}</span>
                  : securityAudits?.length ? securityAudits.map((audit) => (
                    <span key={audit.id} className="inline-flex items-center gap-1">
                      <span className="font-mono text-muted-foreground">{audit.scannerType}</span>
                      <VerdictBadge displayState={getSecurityAuditDisplayState(audit, activeReviewVersion?.status)} compact />
                    </span>
                  )) : <span className="text-muted-foreground">{t(activeReviewVersion?.status === 'SCANNING' ? 'review.scanPending'
                    : activeReviewVersion?.status === 'SCAN_FAILED' ? 'review.scanFailed' : 'review.scanUnavailable')}</span>}
            </button>
            {isGithubSyncReview && (
              <button data-analytics-id="review.security.open" type="button" onClick={() => setSelectedTab('source')}
                className="inline-flex min-h-8 items-center gap-2 rounded border border-border bg-muted/20 px-2.5 py-1 text-left hover:bg-muted/50">
                <span className="font-medium">{t('review.githubLicenseStatus')}</span>
                <span className={githubSource?.evidence.status !== 'RECOGNIZED' ? 'text-amber-700 dark:text-amber-400' : 'text-emerald-700 dark:text-emerald-400'}>
                  {githubSource ? t(`review.githubLicense${githubSource.evidence.status}`, { defaultValue: githubSource.evidence.status })
                    : t(isGithubSourceError ? 'review.githubSourceError' : 'review.scanLoading')}
                </span>
              </button>
            )}
          </div>
        )}
        {isGithubSyncReview && (
          <details className="border-t pt-4 text-sm">
            <summary className="cursor-pointer font-medium">{t('review.technicalDetails')}</summary>
            <dl className="mt-3 grid gap-3 sm:grid-cols-2">
              <div><dt className="text-muted-foreground">{t('review.version')}</dt><dd className="break-all font-mono">{review.version}</dd></div>
              <div><dt className="text-muted-foreground">{t('review.submitter')}</dt><dd className="break-all">{review.submittedByName || review.submittedBy}</dd></div>
            </dl>
          </details>
        )}
      </Card>

      <Tabs value={activeTab} onValueChange={setSelectedTab} className="min-w-0 space-y-4">
        <TabsList className="flex w-full gap-4 overflow-x-auto">
          <TabsTrigger data-analytics-id="review.content.tab" value="content" className="py-2 text-xs">{t('review.tabContent')}</TabsTrigger>
          {!isSuiteReview && !isMetadataReview && <TabsTrigger data-analytics-id="review.changes.tab" value="changes" className="py-2 text-xs">{t('review.tabChanges')}</TabsTrigger>}
          {!isSuiteReview && !isMetadataReview && <TabsTrigger data-analytics-id="review.source.tab" value="source" className="py-2 text-xs">{t('review.tabSourceSecurity')}</TabsTrigger>}
          <TabsTrigger data-analytics-id="review.history.tab" value="history" className="py-2 text-xs">{t('review.tabHistory')}</TabsTrigger>
        </TabsList>
        <TabsContent value="content" className="space-y-4">
          {review.requestedVisibility && <section className="rounded-xl border p-4 space-y-2">
            <h2 className="font-semibold">{t('skillVisibility.title')}</h2>
            <p>{t(`skillVisibility.${review.previousVisibility}`)} → {t(`skillVisibility.${review.requestedVisibility}`)}</p>
            {reviewSkillDetail?.visibilityChange && <p>{t(`skillVisibility.scan${reviewSkillDetail.visibilityChange.scanStatus}`)}</p>}
            <p className="text-sm text-muted-foreground">{t('skillVisibility.expanding')}</p>
          </section>}
          {reviewSkillDetail?.avatarChange && <ReviewAvatarChangeSection change={reviewSkillDetail.avatarChange} name={reviewSkillDetail.skill.displayName || review.skillSlug || ''} />}

          {isSuiteReview && (
            <Card className="space-y-4 p-4">
              <div>
                <h2 className="text-base font-semibold">{t('suite.reviewSnapshot')}</h2>
                <p className="mt-1 text-sm text-muted-foreground">{t('suite.reviewSnapshotDescription')}</p>
              </div>
              {isLoadingReviewSuiteDetail ? (
                <div className="h-24 animate-shimmer rounded-lg" />
              ) : reviewSuiteDetail ? (
                <div className="space-y-5">
                  {reviewSuiteDetail.overview ? (
                    <div className="rounded-xl border p-5">
                      <h3 className="font-semibold">{t('suite.overviewTitle')}</h3>
                      <MarkdownRenderer content={reviewSuiteDetail.overview} className="mt-3" />
                    </div>
                  ) : null}
                  <div className="divide-y divide-border rounded-xl border">
                    {reviewSuiteDetail.members.map((member) => (
                      <div key={`${member.namespace}/${member.slug}@${member.version}`} className="flex flex-wrap items-center justify-between gap-4 p-4">
                        <div className="min-w-0">
                          <p className="break-words font-medium">{member.displayName || `@${member.namespace}/${member.slug}`}</p>
                          <p className="break-all text-xs text-muted-foreground">@{member.namespace}/{member.slug} · {t('suite.pinnedVersion', { version: member.version })}{member.entry ? ` · ${t('suite.entrySkill')}` : ''}</p>
                        </div>
                        {member.blockingReason ? <span className="text-sm text-destructive">{suiteBlockingReasonLabel(t, member.blockingReason)}</span> : <span className="text-sm text-emerald-600">{t('suite.publishable')}</span>}
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="flex flex-wrap items-center gap-3">
                  <p className="text-sm text-destructive">{t('suite.reviewSnapshotFailed')}</p>
                  <Button data-analytics-id="review.suite.retry" type="button" size="sm" variant="outline" onClick={() => void refetchReviewSuiteDetail()}>
                    {t('suite.reviewSnapshotRetry')}
                  </Button>
                </div>
              )}
            </Card>
          )}

          {!isSuiteReview && !isMetadataReview && (
            <ReviewSkillDetailSection detail={contentDetail}
              isLoading={isLoadingReviewSkillDetail}
              hasError={Boolean(reviewSkillDetailError)}
              documentationLoading={shouldLoadDocumentation && isLoadingDocumentation}
              documentationError={Boolean(shouldLoadDocumentation && documentationError)}
              documentationTooLarge={documentationTooLarge} reviewId={taskId} />
          )}
        </TabsContent>
        <TabsContent value="changes" className="space-y-4">
          {isGithubSyncReview && (
            isLoadingGithubSource ? <Card className="h-32 animate-shimmer" />
              : githubSource ? <GithubSyncReviewDiff source={githubSource} reviewId={taskId} />
                : isGithubSourceError ? <Card className="p-4 text-sm text-destructive">{t('review.githubSourceError')}</Card> : null
          )}
          {!isSuiteReview && !isMetadataReview && (
            isGithubSyncReview ? (
              <details className="rounded border bg-card p-3 text-sm">
                <summary className="cursor-pointer font-medium">{t('review.complianceChanges')}</summary>
                <div className="mt-3"><ReviewSkillChangesSection detail={reviewSkillDetail} isLoading={isLoadingReviewSkillDetail}
                  hasError={Boolean(reviewSkillDetailError)} concealTechnicalVersions /></div>
              </details>
            ) : <ReviewSkillChangesSection detail={reviewSkillDetail} isLoading={isLoadingReviewSkillDetail}
              hasError={Boolean(reviewSkillDetailError)} />
          )}
        </TabsContent>
        {!isSuiteReview && !isMetadataReview && <TabsContent value="source" className="space-y-4">
          {skillId && versionId ? <SecurityAuditSection skillId={skillId} versionId={versionId}
            versionStatus={activeReviewVersion?.status} showEmptyState compact />
            : <Card className="p-4 text-sm text-muted-foreground">{t('review.scanUnavailable')}</Card>}
          {isGithubSyncReview && (isLoadingGithubSource ? <Card className="h-32 animate-shimmer" />
            : githubSource ? <GithubSyncReviewEvidence source={githubSource} />
              : <Card className="p-4 text-sm text-destructive">{t('review.githubSourceError')}</Card>)}
        </TabsContent>}
        <TabsContent value="history" className="space-y-4">
          <Card className="space-y-3 p-4">
            <h2 className="text-base font-semibold">{t('review.attemptHistory')}</h2>
            {isLoadingReviewAttempts ? <div className="h-20 animate-shimmer rounded-lg" />
              : isReviewAttemptsError ? <p className="text-sm text-destructive">{t('review.attemptHistoryError')}</p>
                : <ReviewAttemptTimeline attempts={reviewAttempts ?? []} locale={i18n.language} compact />}
          </Card>
          {(review.reviewedBy || review.reviewComment) && (
            <Card className="space-y-3 p-4">
              <h2 className="text-base font-semibold">{t('review.reviewResult')}</h2>
              {review.reviewedBy && <p className="text-sm">{t('review.reviewer')}: {review.reviewedByName || review.reviewedBy}</p>}
              {review.reviewedAt && <p className="text-sm text-muted-foreground">{t('review.reviewTime')}: {formatDate(review.reviewedAt)}</p>}
              {review.reviewComment && <p className="whitespace-pre-wrap rounded-lg bg-muted/40 p-4 text-sm">{review.reviewComment}</p>}
            </Card>
          )}
        </TabsContent>
      </Tabs>

      <div className="sticky bottom-0 z-20 -mx-3 mt-auto border-t border-border bg-background px-3 py-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))] shadow-[0_-5px_16px_-12px_rgb(0_0_0/0.28)] sm:-mx-4 sm:px-4">
        {review.status === 'PENDING' ? (
          <div className="flex flex-col gap-2 lg:flex-row lg:items-end">
            <div className="min-w-0 flex-1 space-y-1">
              <Label htmlFor="comment" className="text-xs font-medium">
                {t(needsLicenseBasis ? 'review.githubLicenseBasisLabel' : 'review.commentLabel')}
              </Label>
              <Textarea
                id="comment"
                placeholder={t('review.commentPlaceholder')}
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                rows={1}
                className="min-h-9 resize-y text-xs"
              />
            </div>
            <div className="flex shrink-0 flex-wrap gap-2">
            <Button data-analytics-id="review.approve.request"
              size="sm"
              className="px-2.5"
              onClick={() => {
                if (isApprovalBlocked) {
                  return
                }
                setApproveDialog(true)
              }}
              disabled={approveMutation.isPending || rejectMutation.isPending || isApprovalBlocked}
            >
              {t('review.approve')}
            </Button>
            {!showRejectForm ? (
              <Button data-analytics-id="review.reject.open"
                size="sm"
                className="px-2.5"
                variant="destructive"
                onClick={() => setShowRejectForm(true)}
                disabled={approveMutation.isPending || rejectMutation.isPending}
              >
                {t('review.reject')}
              </Button>
            ) : (
              <>
                <Button data-analytics-id="review.reject.request"
                  size="sm"
                  className="px-2.5"
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
                <Button data-analytics-id="review.reject.cancel"
                  size="sm"
                  className="px-2.5"
                  variant="outline"
                  onClick={() => setShowRejectForm(false)}
                  disabled={approveMutation.isPending || rejectMutation.isPending}
                >
                  {t('review.cancelReject')}
                </Button>
              </>
            )}
            </div>
            {(isApprovalBlockedByScanning || isApprovalBlockedByAvatar || isAvatarOverrideReasonRequired || needsLicenseBasis || (showRejectForm && !comment.trim())) && (
              <div className="text-xs lg:max-w-52" role="status">
                {isApprovalBlockedByScanning && <p className="text-muted-foreground">{t('review.approveDisabledScanning')}</p>}
                {isAvatarOverrideReasonRequired && <p className="text-muted-foreground">{t('review.avatarOverrideReason')}</p>}
                {isApprovalBlockedByAvatar && <p className="text-muted-foreground">{t('review.approveDisabledAvatar')}</p>}
                {needsLicenseBasis && <p className="text-amber-700 dark:text-amber-400">{t('review.githubLicenseBasisRequired')}</p>}
                {showRejectForm && !comment.trim() && <p className="text-destructive">{t('review.rejectReasonRequired')}</p>}
              </div>
            )}
          </div>
        ) : <div className="flex flex-wrap items-center gap-2 text-xs">
          <span className="font-semibold">{t('review.reviewResult')}:</span>
          <span>{t(review.status === 'APPROVED' ? 'review.statusApproved' : review.status === 'REJECTED' ? 'review.statusRejected' : 'review.statusSuperseded')}</span>
          {review.reviewComment && <span className="min-w-0 break-words text-muted-foreground">{review.reviewComment}</span>}
        </div>}
      </div>

      <ConfirmDialog confirmAnalyticsId="review.approve.confirm" cancelAnalyticsId="review.approve.cancel"
        open={approveDialog}
        onOpenChange={setApproveDialog}
        title={t('review.approveTitle')}
        description={t('review.approveDescription')}
        confirmText={t('review.approveConfirm')}
        onConfirm={handleApprove}
      />

      <ConfirmDialog confirmAnalyticsId="review.reject.confirm" cancelAnalyticsId="review.reject.cancel"
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

export function ReviewDetailPage() {
  const { id } = useParams({ from: '/dashboard/reviews/$id' })

  return (
    <ReviewDetailScreen
      key={id}
      taskId={Number(id)}
      backTo={buildGlobalReviewsPath()}
    />
  )
}

export function NamespaceReviewDetailPage() {
  const { id, slug } = useParams({ from: '/dashboard/namespaces/$slug/reviews/$id' })

  return (
    <ReviewDetailScreen
      key={`${slug}/${id}`}
      taskId={Number(id)}
      backTo={buildNamespaceReviewsPath(slug)}
      namespaceSlug={slug}
    />
  )
}
