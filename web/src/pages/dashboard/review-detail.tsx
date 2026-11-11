import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { buildReviewReturnPath } from '@/features/review/review-route-search'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { ArrowLeft, ArrowRight, ShieldCheck } from 'lucide-react'
import { ReviewTaskHeader, reviewRequestKey } from '@/features/review/review-task-summary'
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

  const { data: review, isLoading } = useReviewDetail(taskId)
  const isSuiteReview = review?.subjectType === 'SUITE_VERSION'
  const isMetadataReview = review?.subjectType === 'SKILL_METADATA' && !review.requestedVisibility
  const isGithubSyncReview = review?.originType === 'GITHUB_SYNC'
  const returnTo = backTo
  const { data: githubSource, isLoading: isLoadingGithubSource, isError: isGithubSourceError } =
    useGithubSyncReviewSource(taskId, isGithubSyncReview)
  const [selectedTab, setSelectedTab] = useState<string | null>(null)
  const tabsRef = useRef<HTMLDivElement>(null)
  const [tabNavigation, setTabNavigation] = useState(0)
  const focusTabAfterNavigation = useRef(false)
  const selectTab = (tab: string, fromShortcut = false) => {
    focusTabAfterNavigation.current = fromShortcut
    setSelectedTab(tab)
    setTabNavigation((request) => request + 1)
  }
  // Scroll after the new panel commits, before paint: a shorter panel can clamp
  // the old document scroll position. Query completion must never repeat this.
  useLayoutEffect(() => {
    if (tabNavigation === 0) return
    if (focusTabAfterNavigation.current) {
      tabsRef.current?.querySelector<HTMLElement>('[role="tab"][aria-selected="true"]')?.focus({ preventScroll: true })
      focusTabAfterNavigation.current = false
    }
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    window.scrollTo({ top: 0, behavior: reducedMotion ? 'instant' : 'smooth' })
  }, [tabNavigation])
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
    refetch: refetchReviewSkillDetail,
  } = useReviewSkillDetail(taskId, Boolean(review) && !isSuiteReview, false)
  const hasBaseline = Boolean(!isMetadataReview && !review?.requestedVisibility && !isSuiteReview
    && (isGithubSyncReview ? githubSource?.baseVersionId : reviewSkillDetail?.comparison))
  const availableTab = activeTab === 'changes' && !hasBaseline ? 'content' : activeTab
  const comparisonQuery = useReviewSkillDetail(taskId, Boolean(review) && hasBaseline && availableTab === 'changes', true)
  const overviewNeedsDocumentation = !reviewSkillDetail?.presentation || ['README', 'SKILL_MD'].includes(reviewSkillDetail.presentation.overviewSource ?? 'NONE')
  const documentationFile = reviewSkillDetail?.files.find(
    (file) => file.filePath === reviewSkillDetail.documentationPath,
  )
  const documentationTooLarge = Boolean(documentationFile && documentationFile.fileSize > 256 * 1024)
  const shouldLoadDocumentation = !isMetadataReview && overviewNeedsDocumentation && availableTab === 'content' && Boolean(reviewSkillDetail?.documentationPath)
    && !documentationTooLarge && (!isGithubSyncReview || Boolean(githubSource) || isGithubSourceError)
  const {
    data: documentationContent,
    isLoading: isLoadingDocumentation,
    error: documentationError,
    refetch: refetchDocumentation,
  } = useReviewFile(taskId, reviewSkillDetail?.documentationPath ?? null, shouldLoadDocumentation)
  const contentDetail = reviewSkillDetail && typeof documentationContent === 'string'
    ? { ...reviewSkillDetail, documentationContent }
    : reviewSkillDetail
  const activeReviewVersion = reviewSkillDetail?.versions?.find(
    (version) => version.version === reviewSkillDetail.activeVersion
  )
  const isApprovalBlockedByScanning = !isMetadataReview && activeReviewVersion?.status === 'SCANNING'
  const skillId = reviewSkillDetail?.skill?.id
  const versionId = review?.skillVersionId ?? activeReviewVersion?.id
  const { data: securityAudits, isLoading: isLoadingSecurityAudits, isError: isSecurityAuditsError } =
    useSecurityAudits(isMetadataReview || review?.requestedVisibility ? undefined : skillId, isMetadataReview || review?.requestedVisibility ? undefined : versionId)
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
  const formatDate = (dateString: string) => {
    return formatLocalDateTime(dateString, i18n.language)
  }

  const isApprovalBlockedByAvatar = reviewSkillDetail?.avatarChange?.readyForApproval === false || reviewSkillDetail?.visibilityChange?.readyForApproval === false
  const isAvatarOverrideReasonRequired = reviewSkillDetail?.avatarChange?.machineStatus === 'BLOCKED' && !comment.trim()
  const requiresDocumentationVerification = !isMetadataReview && overviewNeedsDocumentation && Boolean(reviewSkillDetail?.documentationPath)
  const isDocumentationUnverified = requiresDocumentationVerification && (documentationTooLarge || isLoadingDocumentation || Boolean(documentationError)
    || (typeof documentationContent !== 'string' && typeof reviewSkillDetail?.documentationContent !== 'string'))
  const isComparisonUnverified = hasBaseline && (comparisonQuery.isLoading || Boolean(comparisonQuery.error))
  const isApprovalBlocked = isDocumentationUnverified || isComparisonUnverified || isApprovalBlockedByScanning || isApprovalBlockedByAvatar || isAvatarOverrideReasonRequired
    || (isSuiteReview ? !reviewSuiteDetail : isLoadingReviewSkillDetail || !!reviewSkillDetailError || !reviewSkillDetail)
    || (isGithubSyncReview && !githubSource)

  const handleApprove = async () => {
    if (isApprovalBlocked || review?.canReview !== true || review.status !== 'PENDING') {
      return
    }
    if (needsLicenseBasis && !comment.trim()) {
      toast.error(t('review.githubLicenseBasisRequired'))
      return
    }
    approveMutation.mutate({ taskId, comment: comment || undefined })
  }

  const handleReject = async () => {
    if (review?.canReview !== true || review.status !== 'PENDING') return
    if (!comment.trim()) {
      toast.error(t('review.rejectReasonRequired'))
      return
    }
    rejectMutation.mutate({ taskId, comment })
  }

  if (isLoading) {
    return (
      <div className="space-y-6 animate-fade-up">
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

  const hasNamespaceMismatch = Boolean(namespaceSlug && review.namespace !== namespaceSlug)

  if (hasNamespaceMismatch) {
    return (
      <div className="space-y-6 animate-fade-up">
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


  const title = isSuiteReview
    ? reviewSuiteDetail?.displayName || review.displayName || review.subjectSlug || t('review.detail')
    : reviewSkillDetail?.presentation?.displayName || reviewSkillDetail?.skill.displayName || review.displayName || review.subjectSlug || review.skillSlug || t('review.detail')

  return (
    <div className="flex min-h-[calc(100dvh-5.25rem)] w-full flex-col gap-4 animate-fade-up">
      <div className="space-y-4">
        <Button data-analytics-id="review.list.open" variant="link" size="sm" className="h-auto px-0 py-0 text-xs" onClick={() => navigate({ to: returnTo })}>
          <ArrowLeft className="h-3.5 w-3.5" />{t('review.backToList')}
        </Button>
        <ReviewTaskHeader review={review} detail={isSuiteReview ? undefined : reviewSkillDetail}
          title={title} resourceVisibility={isSuiteReview ? reviewSuiteDetail?.visibility : undefined}
          hasBaseline={hasBaseline} submittedAt={formatDate(review.submittedAt)}>
          {!isSuiteReview && !isMetadataReview && <div className="flex min-w-0 flex-wrap items-center gap-x-6 gap-y-2">
            <button data-analytics-id="review.security.open" type="button" onClick={() => selectTab('source', true)} className="inline-flex min-h-9 min-w-0 flex-wrap items-center gap-x-3 gap-y-2 rounded-sm text-left text-sm underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
              <ShieldCheck className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
              <span className="font-medium">{t('review.scanSummary')}</span>
              {review.requestedVisibility
                ? <span className="text-xs text-muted-foreground">{reviewSkillDetail?.visibilityChange ? t(`skillVisibility.scan${reviewSkillDetail.visibilityChange.scanStatus}`) : t('review.scanLoadFailed')}</span>
                : isLoadingReviewSkillDetail || (skillId && versionId && isLoadingSecurityAudits) ? <span className="text-xs text-muted-foreground">{t('review.scanLoading')}</span>
                  : reviewSkillDetailError || isSecurityAuditsError ? <span className="text-xs text-destructive">{t('review.scanLoadFailed')}</span>
                    : securityAudits?.length ? <span className="inline-flex flex-wrap gap-2">{securityAudits.map((audit) => <VerdictBadge key={audit.id} displayState={getSecurityAuditDisplayState(audit, activeReviewVersion?.status)} compact />)}</span>
                      : <span className="text-xs text-muted-foreground">{t(activeReviewVersion?.status === 'SCANNING' ? 'review.scanPending' : activeReviewVersion?.status === 'SCAN_FAILED' ? 'review.scanFailed' : 'review.scanUnavailable')}</span>}
              <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">{t('reviewWorkbench.viewScanEvidence')}<ArrowRight className="h-3 w-3" aria-hidden="true" /></span>
            </button>
            {isGithubSyncReview && <button type="button" data-analytics-id="review.source.open" onClick={() => selectTab('source', true)} className="inline-flex min-h-9 flex-wrap items-center gap-2 rounded-sm text-left text-sm underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><span className="font-medium">{t('review.githubLicenseStatus')}</span><span className="text-xs text-muted-foreground">{githubSource ? t(`review.githubLicense${githubSource.evidence.status}`, { defaultValue: githubSource.evidence.status }) : t(isGithubSourceError ? 'review.githubSourceError' : 'review.scanLoading')}</span><ArrowRight className="h-3 w-3" aria-hidden="true" /></button>}
          </div>}
        </ReviewTaskHeader>
      </div>
      <div data-testid="review-detail-layout" className="min-w-0">
      <div className="min-w-0">
      <Tabs data-testid="review-detail-workbench" ref={tabsRef} value={availableTab} onValueChange={selectTab} className="min-h-[28rem] min-w-0 rounded-xl border border-border/60 bg-card text-card-foreground [overflow-anchor:none]">
        <div className="border-b border-border/60 p-3 sm:px-5 sm:py-4">
        <TabsList className="grid w-full grid-cols-2 gap-1 rounded-2xl border-0 bg-secondary/70 p-1 sm:inline-flex sm:w-auto sm:flex-wrap sm:rounded-full" aria-label={t('reviewWorkbench.contentNavigation')}>
          <TabsTrigger data-analytics-id="review.content.tab" value="content" className="mb-0 min-h-9 rounded-xl border-0 px-4 py-2 font-normal data-[state=active]:bg-background data-[state=active]:font-medium data-[state=active]:shadow-sm sm:rounded-full">{t('review.tabContent')}</TabsTrigger>
          {hasBaseline && <TabsTrigger data-analytics-id="review.changes.tab" value="changes" className="mb-0 min-h-9 rounded-xl border-0 px-4 py-2 font-normal data-[state=active]:bg-background data-[state=active]:font-medium data-[state=active]:shadow-sm sm:rounded-full">{t('review.tabChanges')}</TabsTrigger>}
          {!isSuiteReview && !isMetadataReview && <TabsTrigger data-analytics-id="review.source.tab" value="source" className="mb-0 min-h-9 rounded-xl border-0 px-4 py-2 font-normal data-[state=active]:bg-background data-[state=active]:font-medium data-[state=active]:shadow-sm sm:rounded-full">{t('review.tabSourceSecurity')}</TabsTrigger>}
          <TabsTrigger data-analytics-id="review.history.tab" value="history" className="mb-0 min-h-9 rounded-xl border-0 px-4 py-2 font-normal data-[state=active]:bg-background data-[state=active]:font-medium data-[state=active]:shadow-sm sm:rounded-full">{t('review.tabHistory')}</TabsTrigger>
        </TabsList>
        </div>
        <div className="min-w-0 p-4 sm:p-6">
        <TabsContent value="content" className="space-y-4 motion-reduce:animate-none">
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
              documentationTooLarge={documentationTooLarge} reviewId={taskId}
              onRetry={() => { void refetchReviewSkillDetail?.(); void refetchDocumentation?.() }} />
          )}
        </TabsContent>
        <TabsContent value="changes" className="space-y-4 motion-reduce:animate-none">
          {isGithubSyncReview && (
            isLoadingGithubSource ? <Card className="h-32 animate-shimmer" />
              : githubSource ? <GithubSyncReviewDiff source={githubSource} reviewId={taskId} />
                : isGithubSourceError ? <Card className="p-4 text-sm text-destructive">{t('review.githubSourceError')}</Card> : null
          )}
          {!isSuiteReview && !isMetadataReview && (
            isGithubSyncReview ? (
              <details className="rounded border bg-card p-3 text-sm">
                <summary className="cursor-pointer font-medium">{t('reviewWorkbench.presentationChanges')}</summary>
                <div className="mt-3"><ReviewSkillChangesSection detail={comparisonQuery.data ?? reviewSkillDetail} isLoading={comparisonQuery.isLoading}
                  hasError={Boolean(comparisonQuery.error)} onRetry={() => void comparisonQuery.refetch?.()} concealTechnicalVersions /></div>
              </details>
            ) : <ReviewSkillChangesSection detail={comparisonQuery.data ?? reviewSkillDetail} isLoading={comparisonQuery.isLoading}
              hasError={Boolean(comparisonQuery.error)} onRetry={() => void comparisonQuery.refetch?.()} />
          )}
        </TabsContent>
        {!isSuiteReview && !isMetadataReview && <TabsContent value="source" className="space-y-4 motion-reduce:animate-none">
          {review.requestedVisibility ? <Card className="space-y-3 p-5"><h2 className="text-lg font-semibold">{t('reviewWorkbench.boundScan')}</h2><p className="text-sm">{reviewSkillDetail?.visibilityChange ? t(`skillVisibility.scan${reviewSkillDetail.visibilityChange.scanStatus}`) : t('review.scanLoadFailed')}</p><p className="text-xs text-muted-foreground">{t('reviewWorkbench.boundScanDescription')}</p></Card>
            : skillId && versionId ? <SecurityAuditSection skillId={skillId} versionId={versionId}
            versionStatus={activeReviewVersion?.status} showEmptyState compact />
            : <Card className="p-4 text-sm text-muted-foreground">{t('review.scanUnavailable')}</Card>}
          {isGithubSyncReview && (isLoadingGithubSource ? <Card className="h-32 animate-shimmer" />
            : githubSource ? <GithubSyncReviewEvidence source={githubSource} />
              : <Card className="p-4 text-sm text-destructive">{t('review.githubSourceError')}</Card>)}
        </TabsContent>}
        <TabsContent value="history" className="space-y-4 motion-reduce:animate-none">
          <section className="space-y-3">
            <h2 className="text-base font-semibold">{t('review.attemptHistory')}</h2>
            {isLoadingReviewAttempts ? <div className="h-20 animate-shimmer rounded-lg" />
              : isReviewAttemptsError ? <p className="text-sm text-destructive">{t('review.attemptHistoryError')}</p>
                : <ReviewAttemptTimeline attempts={reviewAttempts ?? []} locale={i18n.language} compact />}
          </section>
          {(review.reviewedBy || review.reviewComment) && (
            <Card className="space-y-3 p-4">
              <h2 className="text-base font-semibold">{t('review.reviewResult')}</h2>
              {review.reviewedBy && <p className="text-sm">{t('review.reviewer')}: {review.reviewedByName || review.reviewedBy}</p>}
              {review.reviewedAt && <p className="text-sm text-muted-foreground">{t('review.reviewTime')}: {formatDate(review.reviewedAt)}</p>}
              {review.reviewComment && <p className="whitespace-pre-wrap rounded-lg bg-muted/40 p-4 text-sm">{review.reviewComment}</p>}
            </Card>
          )}
        </TabsContent>
        </div>
      </Tabs>
      </div>
      </div>

      <div className="sticky bottom-0 z-20 -mx-3 mt-auto border-t border-border bg-background px-3 py-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))] shadow-[0_-5px_16px_-12px_rgb(0_0_0/0.28)] sm:-mx-4 sm:px-4">
        {review.status === 'PENDING' && review.canReview === true ? (
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
                rows={2}
                className="min-h-[64px] resize-y text-sm"
              />
            </div>
            <div className="flex shrink-0 flex-wrap gap-2">

            {!showRejectForm ? (
              <Button data-analytics-id="review.reject.open"
                size="sm"
                className="px-2.5"
                variant="outline"
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
              {t(`reviewWorkbench.approve.${reviewRequestKey(review, hasBaseline)}`)}
            </Button>
            </div>
            {(isDocumentationUnverified || isComparisonUnverified || isApprovalBlockedByScanning || isApprovalBlockedByAvatar || isAvatarOverrideReasonRequired || needsLicenseBasis || (showRejectForm && !comment.trim())) && (
              <div className="text-xs lg:max-w-52" role="status">
                {(isDocumentationUnverified || isComparisonUnverified) && <p className="text-muted-foreground">{t('reviewWorkbench.approveContentUnavailable')}</p>}
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
          <span>{review.status === 'PENDING' ? t('reviewWorkbench.viewOnly') : t(review.status === 'APPROVED' ? 'review.statusApproved' : review.status === 'REJECTED' ? 'review.statusRejected' : 'review.statusSuperseded')}</span>
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
  const search = useSearch({ from: '/dashboard/reviews/$id' })

  return (
    <ReviewDetailScreen
      key={id}
      taskId={Number(id)}
      backTo={buildReviewReturnPath(search)}
    />
  )
}

export function NamespaceReviewDetailPage() {
  const { id, slug } = useParams({ from: '/dashboard/namespaces/$slug/reviews/$id' })
  const search = useSearch({ from: '/dashboard/namespaces/$slug/reviews/$id' })

  return (
    <ReviewDetailScreen
      key={`${slug}/${id}`}
      taskId={Number(id)}
      backTo={buildReviewReturnPath(search, slug)}
      namespaceSlug={slug}
    />
  )
}
