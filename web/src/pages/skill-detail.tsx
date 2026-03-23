import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams, useNavigate, useRouterState, useSearch } from '@tanstack/react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ChevronDown, ChevronUp, User } from 'lucide-react'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { FileTree } from '@/features/skill/file-tree'
import { InstallCommand } from '@/features/skill/install-command'
import { SkillLabelPanel } from '@/features/skill/skill-label-panel'
import {
  getOverviewCollapseMaxHeight,
  OVERVIEW_COLLAPSE_DESKTOP_MAX_HEIGHT,
  shouldCollapseOverview,
} from '@/features/skill/overview-collapse'
import { resolveSkillActionErrorTitle } from '@/features/skill/skill-action-error'
import { isDeleteSlugConfirmationValid, resolveDeletedSkillReturnTo } from '@/features/skill/skill-delete-flow'
import { RatingInput } from '@/features/social/rating-input'
import { StarButton } from '@/features/social/star-button'
import { useAuth } from '@/features/auth/use-auth'
import { adminApi, ApiError, buildApiUrl, WEB_API_PREFIX } from '@/api/client'
import { useSubmitSkillReport } from '@/features/report/use-skill-reports'
import { SecurityAuditSummary } from '@/features/security-audit/security-audit-summary'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { incrementSkillDownloadCount } from '@/shared/lib/skill-download-cache'
import { getSkillSquareSearch, normalizeSkillDetailReturnTo } from '@/shared/lib/skill-navigation'
import { formatCompactCount } from '@/shared/lib/number-format'
import { resolveDocumentationFilePath } from '@/shared/lib/skill-documentation'
import { getHeadlineVersion, getOwnerPreviewVersion, getPublishedVersion, isOwnerPreviewResolution } from '@/shared/lib/skill-lifecycle'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/shared/ui/tabs'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Textarea } from '@/shared/ui/textarea'
import { toast } from '@/shared/lib/toast'
import { cn } from '@/shared/lib/utils'
import {
  useSkillDetail,
  useSkillVersions,
  useSkillVersionDetail,
  useSkillFiles,
  useSkillReadme,
  useArchiveSkill,
  useDeleteSkill,
  useDeleteSkillVersion,
  useRereleaseSkillVersion,
  useUnarchiveSkill,
  useWithdrawSkillReview,
} from '@/shared/hooks/use-skill-queries'
import { useSubmitPromotion } from '@/shared/hooks/use-user-queries'

/**
 * Detail page for one skill and its version history.
 *
 * This page coordinates documentation rendering, file browsing, downloads, lifecycle actions,
 * promotion/report dialogs, and social interactions for the selected skill.
 */
function suggestNextVersion(version: string) {
  const semverMatch = version.match(/^(\d+)\.(\d+)\.(\d+)$/)
  if (semverMatch) {
    const [, major, minor, patch] = semverMatch
    return `${major}.${minor}.${Number.parseInt(patch, 10) + 1}`
  }
  return `${version}.1`
}

function parseMetadataJson(parsed?: string) {
  if (!parsed) {
    return {}
  }
  try {
    const value = JSON.parse(parsed)
    return typeof value === 'object' && value !== null ? value : {}
  } catch {
    return {}
  }
}

function getPromotionConflictKey(error: ApiError): 'promotion.duplicate_pending' | 'promotion.already_promoted' | null {
  if (error.serverMessageKey === 'promotion.duplicate_pending') {
    return 'promotion.duplicate_pending'
  }
  if (error.serverMessageKey === 'promotion.already_promoted') {
    return 'promotion.already_promoted'
  }
  return null
}

export function SkillDetailPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const location = useRouterState({ select: (s) => s.location })
  const search = useSearch({ from: '/space/$namespace/$slug' })
  const queryClient = useQueryClient()
  const [reportDialogOpen, setReportDialogOpen] = useState(false)
  const [reportReason, setReportReason] = useState('')
  const [reportDetails, setReportDetails] = useState('')
  const [hasReported, setHasReported] = useState(false)
  const [archiveConfirmOpen, setArchiveConfirmOpen] = useState(false)
  const [unarchiveConfirmOpen, setUnarchiveConfirmOpen] = useState(false)
  const [promotionConfirmOpen, setPromotionConfirmOpen] = useState(false)
  const [deleteSkillConfirmOpen, setDeleteSkillConfirmOpen] = useState(false)
  const [deleteSkillInputOpen, setDeleteSkillInputOpen] = useState(false)
  const [deleteSkillInput, setDeleteSkillInput] = useState('')
  const [deleteVersionTarget, setDeleteVersionTarget] = useState<string | null>(null)
  const [withdrawVersionTarget, setWithdrawVersionTarget] = useState<string | null>(null)
  const [rereleaseTarget, setRereleaseTarget] = useState<string | null>(null)
  const [targetVersionInput, setTargetVersionInput] = useState('')
  const [diffSourceVersion, setDiffSourceVersion] = useState<string | null>(null)
  const [diffCompareVersion, setDiffCompareVersion] = useState<string | null>(null)
  const [isOverviewExpanded, setIsOverviewExpanded] = useState(false)
  const [isOverviewCollapsible, setIsOverviewCollapsible] = useState(false)
  const [overviewMaxHeight, setOverviewMaxHeight] = useState(OVERVIEW_COLLAPSE_DESKTOP_MAX_HEIGHT)
  const overviewContentRef = useRef<HTMLDivElement | null>(null)
  const overviewSectionRef = useRef<HTMLDivElement | null>(null)
  const { namespace, slug } = useParams({ from: '/space/$namespace/$slug' })
  const { user, hasRole } = useAuth()

  const { data: skill, isLoading: isLoadingSkill, error: skillError } = useSkillDetail(namespace, slug)
  const { data: versions } = useSkillVersions(namespace, slug)
  const headlineVersion = skill ? getHeadlineVersion(skill) : null
  const publishedVersion = skill ? getPublishedVersion(skill) : null
  const ownerPreviewVersion = skill ? getOwnerPreviewVersion(skill) : null
  const selectedVersion = headlineVersion?.version ?? versions?.[0]?.version
  const selectedVersionEntry = versions?.find((version) => version.version === selectedVersion) ?? versions?.[0]
  const { data: files } = useSkillFiles(namespace, slug, selectedVersion)
  const documentationPath = resolveDocumentationFilePath(files)
  const { data: readme, error: readmeError } = useSkillReadme(namespace, slug, selectedVersion, documentationPath)
  const { data: diffSourceDetail } = useSkillVersionDetail(namespace, slug, diffSourceVersion ?? undefined)
  const { data: diffCompareDetail } = useSkillVersionDetail(namespace, slug, diffCompareVersion ?? undefined)
  const { data: diffSourceFiles } = useSkillFiles(namespace, slug, diffSourceVersion ?? undefined)
  const { data: diffCompareFiles } = useSkillFiles(namespace, slug, diffCompareVersion ?? undefined)
  const diffSourceDocumentationPath = resolveDocumentationFilePath(diffSourceFiles)
  const diffCompareDocumentationPath = resolveDocumentationFilePath(diffCompareFiles)
  const { data: diffSourceReadme } = useSkillReadme(namespace, slug, diffSourceVersion ?? undefined, diffSourceDocumentationPath)
  const { data: diffCompareReadme } = useSkillReadme(namespace, slug, diffCompareVersion ?? undefined, diffCompareDocumentationPath)
  const governanceVisible = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')
  const canHideSkill = hasRole('SUPER_ADMIN')
  const isPendingPreview = skill ? isOwnerPreviewResolution(skill) : false
  const hasPendingOwnerPreview = ownerPreviewVersion?.status === 'PENDING_REVIEW'
  const hasRejectedVersion = versions?.some((v) => v.status === 'REJECTED') ?? false
  const hasPublishedPendingReview = Boolean(publishedVersion && hasPendingOwnerPreview)
  const canInteract = skill?.canInteract ?? true
  const canReport = skill?.canReport ?? true
  const canHardDeleteSkill = Boolean(skill && user && (skill.ownerId === user.userId || hasRole('SUPER_ADMIN')))
  const canManageLabels = Boolean(skill && user && (skill.canManageLifecycle || hasRole('SUPER_ADMIN')))
  const isVersionDownloadable = selectedVersionEntry?.status === 'PUBLISHED' && (selectedVersionEntry?.downloadAvailable ?? false)

  useEffect(() => {
    // Recompute collapse rules whenever rendered documentation height changes so the page can keep
    // a readable summary section on different screen sizes.
    if (!readme || typeof window === 'undefined') {
      setIsOverviewCollapsible(false)
      setIsOverviewExpanded(false)
      return
    }

    const updateOverviewState = () => {
      if (!overviewContentRef.current) {
        return
      }

      const nextMaxHeight = getOverviewCollapseMaxHeight(window.innerWidth, window.innerHeight)
      const nextCollapsible = shouldCollapseOverview(
        overviewContentRef.current.scrollHeight,
        window.innerWidth,
        window.innerHeight,
      )

      setOverviewMaxHeight(nextMaxHeight)
      setIsOverviewCollapsible(nextCollapsible)

      if (!nextCollapsible) {
        setIsOverviewExpanded(false)
      }
    }

    updateOverviewState()

    window.addEventListener('resize', updateOverviewState)
    const resizeObserver = typeof ResizeObserver !== 'undefined'
      ? new ResizeObserver(() => updateOverviewState())
      : null

    if (resizeObserver && overviewContentRef.current) {
      resizeObserver.observe(overviewContentRef.current)
    }

    return () => {
      window.removeEventListener('resize', updateOverviewState)
      resizeObserver?.disconnect()
    }
  }, [readme])

  const handleToggleOverview = () => {
    if (!isOverviewExpanded) {
      setIsOverviewExpanded(true)
      return
    }

    setIsOverviewExpanded(false)
    requestAnimationFrame(() => {
      overviewSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }

  const refreshSkill = () => {
    // Several actions mutate derived lifecycle state; refresh the shared skill detail and common
    // list caches together to keep the rest of the app consistent.
    queryClient.invalidateQueries({ queryKey: ['skills', namespace, slug] })
    queryClient.invalidateQueries({ queryKey: ['skills', namespace, slug, 'versions'] })
    queryClient.invalidateQueries({ queryKey: ['skills'] })
  }

  const hideMutation = useMutation({
    mutationFn: () => adminApi.hideSkill(skill!.id),
    onSuccess: refreshSkill,
  })

  const unhideMutation = useMutation({
    mutationFn: () => adminApi.unhideSkill(skill!.id),
    onSuccess: refreshSkill,
  })

  const yankMutation = useMutation({
    mutationFn: () => adminApi.yankVersion(selectedVersionEntry!.id),
    onSuccess: refreshSkill,
  })
  const archiveMutation = useArchiveSkill()
  const unarchiveMutation = useUnarchiveSkill()
  const deleteSkillMutation = useDeleteSkill()
  const deleteVersionMutation = useDeleteSkillVersion()
  const withdrawReviewMutation = useWithdrawSkillReview()
  const rereleaseVersionMutation = useRereleaseSkillVersion()
  const submitPromotionMutation = useSubmitPromotion()
  const reportMutation = useSubmitSkillReport(namespace, slug)

  const triggerBrowserDownload = (url: string) => {
    const link = document.createElement('a')
    link.href = url
    document.body.appendChild(link)
    link.click()
    link.remove()
  }

  const handleDownload = async () => {
    if (!user) {
      requireLogin()
      return
    }
    if (!selectedVersionEntry || isPendingPreview) {
      return
    }

    try {
      const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
      triggerBrowserDownload(
        buildApiUrl(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${slug}/versions/${selectedVersionEntry.version}/download`),
      )
      incrementSkillDownloadCount(queryClient, { namespace, slug })
      queryClient.invalidateQueries({ queryKey: ['skills', namespace, slug] })
      queryClient.invalidateQueries({ queryKey: ['skills', 'my'] })
      queryClient.invalidateQueries({ queryKey: ['skills', 'stars'] })
      queryClient.invalidateQueries({ queryKey: ['skills', 'search'] })
    } catch (error) {
      toast.error(t(resolveSkillActionErrorTitle('download')), error instanceof Error ? error.message : '')
    }
  }

  const requireLogin = () => {
    navigate({
      to: '/login',
      search: {
        returnTo: `${location.pathname}${location.searchStr}${location.hash}`,
      },
    })
  }

  const handleOpenReport = () => {
    if (!user) {
      requireLogin()
      return
    }
    setReportDialogOpen(true)
  }

  const handleSubmitReport = async () => {
    if (!reportReason.trim()) {
      toast.error(t('skillDetail.reportReasonRequired'))
      return
    }

    try {
      await reportMutation.mutateAsync({
        reason: reportReason.trim(),
        details: reportDetails.trim() || undefined,
      })
      setReportDialogOpen(false)
      setReportReason('')
      setReportDetails('')
      setHasReported(true)
      toast.success(t('skillDetail.reportSuccessTitle'), t('skillDetail.reportSuccessDescription'))
    } catch (error) {
      toast.error(t(resolveSkillActionErrorTitle('report')), error instanceof Error ? error.message : '')
    }
  }

  const handleBack = () => {
    const returnTo = normalizeSkillDetailReturnTo(search.returnTo)
    if (returnTo) {
      navigate({ to: returnTo })
      return
    }
    navigate({ to: '/search', search: getSkillSquareSearch() })
  }

  const resolveSkillStatusLabel = (status?: string) => {
    if (status === 'ARCHIVED') {
      return t('skillDetail.statusArchived')
    }
    if (status === 'ACTIVE') {
      return t('skillDetail.statusActive')
    }
    if (status === 'HIDDEN') {
      return t('skillDetail.statusHidden')
    }
    return status ?? ''
  }

  const canDeleteVersion = (status?: string) => status === 'DRAFT' || status === 'REJECTED'
  const isLastVersion = versions?.length === 1
  const canWithdrawVersion = (status?: string) => status === 'PENDING_REVIEW'
  const canRereleaseVersion = (status?: string) => status === 'PUBLISHED'

  const metadataDiffEntries = (() => {
    const source = parseMetadataJson(diffSourceDetail?.parsedMetadataJson)
    const compare = parseMetadataJson(diffCompareDetail?.parsedMetadataJson)
    const keys = Array.from(new Set([...Object.keys(source), ...Object.keys(compare)])).sort()
    return keys
      .filter((key) => JSON.stringify(source[key]) !== JSON.stringify(compare[key]))
      .map((key) => ({
        key,
        source: source[key],
        target: compare[key],
      }))
  })()

  const fileDiffSummary = (() => {
    const sourceMap = new Map((diffSourceFiles ?? []).map((file) => [file.filePath, file.sha256]))
    const compareMap = new Map((diffCompareFiles ?? []).map((file) => [file.filePath, file.sha256]))
    const added: string[] = []
    const removed: string[] = []
    const changed: string[] = []

    for (const [path, hash] of sourceMap.entries()) {
      if (!compareMap.has(path)) {
        removed.push(path)
      } else if (compareMap.get(path) !== hash) {
        changed.push(path)
      }
    }
    for (const path of compareMap.keys()) {
      if (!sourceMap.has(path)) {
        added.push(path)
      }
    }

    return { added, removed, changed }
  })()

  const handleArchive = async () => {
    try {
      await archiveMutation.mutateAsync({ namespace, slug })
      toast.success(
        t('skillDetail.archiveSuccessTitle'),
        t('skillDetail.archiveSuccessDescription', { skill: skill?.displayName ?? slug }),
      )
      setArchiveConfirmOpen(false)
    } catch (error) {
      toast.error(t('skillDetail.archiveErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  const handleUnarchive = async () => {
    try {
      await unarchiveMutation.mutateAsync({ namespace, slug })
      toast.success(
        t('skillDetail.unarchiveSuccessTitle'),
        t('skillDetail.unarchiveSuccessDescription', { skill: skill?.displayName ?? slug }),
      )
      setUnarchiveConfirmOpen(false)
    } catch (error) {
      toast.error(t('skillDetail.unarchiveErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  const handleOpenDeleteSkillInput = async () => {
    setDeleteSkillConfirmOpen(false)
    setDeleteSkillInput('')
    setDeleteSkillInputOpen(true)
  }

  const handleDeleteSkill = async () => {
    if (!skill || !isDeleteSlugConfirmationValid(skill.slug, deleteSkillInput)) {
      return
    }
    try {
      await deleteSkillMutation.mutateAsync({ namespace, slug })
      toast.success(
        t('skillDetail.deleteSkillSuccessTitle'),
        t('skillDetail.deleteSkillSuccessDescription', { skill: skill.displayName }),
      )
      setDeleteSkillInputOpen(false)
      navigate({ to: resolveDeletedSkillReturnTo(search.returnTo) })
    } catch (error) {
      toast.error(t('skillDetail.deleteSkillErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  const handleDeleteVersion = async () => {
    if (!deleteVersionTarget) {
      return
    }
    try {
      await deleteVersionMutation.mutateAsync({ namespace, slug, version: deleteVersionTarget })
      toast.success(
        t('skillDetail.deleteVersionSuccessTitle'),
        t('skillDetail.deleteVersionSuccessDescription', { version: deleteVersionTarget }),
      )
      setDeleteVersionTarget(null)
    } catch (error) {
      toast.error(t('skillDetail.deleteVersionErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  const handleWithdrawVersion = async () => {
    if (!withdrawVersionTarget) {
      return
    }
    try {
      await withdrawReviewMutation.mutateAsync({ namespace, slug, version: withdrawVersionTarget })
      toast.success(
        t('skillDetail.withdrawReviewSuccessTitle'),
        t('skillDetail.withdrawReviewSuccessDescription', { version: withdrawVersionTarget }),
      )
      setWithdrawVersionTarget(null)
      navigate({ to: '/dashboard/skills' })
    } catch (error) {
      toast.error(t('skillDetail.withdrawReviewErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  const handleOpenRerelease = (version: string) => {
    setRereleaseTarget(version)
    setTargetVersionInput(suggestNextVersion(version))
  }

  const handleRereleaseVersion = async () => {
    if (!rereleaseTarget || !targetVersionInput.trim()) {
      return
    }
    try {
      await rereleaseVersionMutation.mutateAsync({
        namespace,
        slug,
        version: rereleaseTarget,
        targetVersion: targetVersionInput.trim(),
      })
      toast.success(
        t('skillDetail.rereleaseSuccessTitle'),
        t('skillDetail.rereleaseSuccessDescription', { source: rereleaseTarget, target: targetVersionInput.trim() }),
      )
      setRereleaseTarget(null)
      setTargetVersionInput('')
    } catch (error) {
      toast.error(t('skillDetail.rereleaseErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  const handleOpenDiff = (version: string) => {
    const publishedVersions = versions?.filter((item) => item.status === 'PUBLISHED') ?? []
    const compareVersion = publishedVersions.find((item) => item.version !== version)?.version ?? null
    if (!compareVersion) {
      toast.error(t('skillDetail.versionCompareUnavailableTitle'), t('skillDetail.versionCompareUnavailableDescription'))
      return
    }
    setDiffSourceVersion(version)
    setDiffCompareVersion(compareVersion)
  }

  const handleSubmitPromotion = async () => {
    if (!skill || !publishedVersion) {
      return
    }
    try {
      await submitPromotionMutation.mutateAsync({
        sourceSkillId: skill.id,
        sourceVersionId: publishedVersion.id,
      })
      toast.success(
        t('skillDetail.promotionSuccessTitle'),
        t('skillDetail.promotionSuccessDescription', { skill: skill.displayName, version: publishedVersion.version }),
      )
      setPromotionConfirmOpen(false)
    } catch (error) {
      if (error instanceof ApiError) {
        const conflictKey = getPromotionConflictKey(error)
        if (conflictKey === 'promotion.duplicate_pending') {
          toast.error(t('skillDetail.promotionDuplicateTitle'), t('skillDetail.promotionDuplicateDescription'))
          return
        }
        if (conflictKey === 'promotion.already_promoted') {
          toast.error(t('skillDetail.promotionAlreadyPromotedTitle'), t('skillDetail.promotionAlreadyPromotedDescription'))
          return
        }
      }
      toast.error(t('skillDetail.promotionErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  if (isLoadingSkill) {
    return (
      <div className="space-y-6 animate-fade-up">
        <div className="h-10 w-64 animate-shimmer rounded-lg" />
        <div className="h-5 w-96 animate-shimmer rounded-md" />
        <div className="h-64 animate-shimmer rounded-xl" />
      </div>
    )
  }

  if (skillError) {
    const isForbidden = skillError instanceof Error && skillError.message.includes('403')

    if (isForbidden && !user) {
      return (
        <div className="text-center py-20 animate-fade-up">
          <h2 className="text-2xl font-bold font-heading mb-2">{t('skillDetail.loginRequired')}</h2>
          <p className="text-muted-foreground mb-6">{t('skillDetail.loginRequiredDesc')}</p>
          <Button onClick={requireLogin}>{t('common.login')}</Button>
        </div>
      )
    }

    return (
      <div className="text-center py-20 animate-fade-up">
        <h2 className="text-2xl font-bold font-heading mb-2">{t('skillDetail.accessDenied')}</h2>
        <p className="text-muted-foreground">{t('skillDetail.accessDeniedDesc')}</p>
      </div>
    )
  }

  if (!skill) {
    return (
      <div className="text-center py-20 animate-fade-up">
        <h2 className="text-2xl font-bold font-heading mb-2">{t('skillDetail.notFound')}</h2>
        <p className="text-muted-foreground">{t('skillDetail.notFoundDesc')}</p>
      </div>
    )
  }

  return (
    <div className="max-w-6xl mx-auto flex flex-col lg:flex-row gap-8 animate-fade-up">
      {/* Main Content */}
      <div className="flex-1 min-w-0 space-y-8">
        <div className="space-y-3">
          <Button
            variant="ghost"
            size="sm"
            className="gap-2 px-0 text-muted-foreground hover:text-foreground"
            onClick={handleBack}
          >
            <ArrowLeft className="h-4 w-4" />
            {t('skillDetail.back')}
          </Button>
          <div className="flex items-center gap-3 mb-1">
            <NamespaceBadge type="GLOBAL" name={namespace} />
            {skill.status && (
              <span className="badge-soft badge-soft-blue">
                {resolveSkillStatusLabel(skill.status)}
              </span>
            )}
            {isPendingPreview && (
              <span className="badge-soft" style={{ background: '#fef3c7', color: '#92400e' }}>
                {t('skillDetail.pendingPreviewBadge')}
              </span>
            )}
            {!isPendingPreview && hasRejectedVersion && skill.canManageLifecycle && (
              <span className="badge-soft" style={{ background: '#fee2e2', color: '#991b1b' }}>
                {t('skillDetail.rejectedBadge')}
              </span>
            )}
          </div>
          <h1 className="text-balance text-4xl font-bold font-heading text-foreground">{skill.displayName}</h1>
          {skill.ownerDisplayName && (
            <div className="flex min-w-0">
              <div className="inline-flex max-w-full items-center gap-2 rounded-full border border-border/60 bg-background/85 px-3 py-1.5 text-sm text-muted-foreground shadow-sm backdrop-blur-sm">
                <span className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-[11px] font-semibold uppercase tracking-[0.08em] text-primary">
                  <User className="h-3.5 w-3.5" aria-hidden="true" />
                </span>
                <span className="min-w-0 truncate">{t('skillDetail.authorLabel', { name: skill.ownerDisplayName })}</span>
              </div>
            </div>
          )}
          {skill.summary && (
            <p className="text-lg text-muted-foreground leading-relaxed">{skill.summary}</p>
          )}
          {(skill.labels?.length ?? 0) > 0 && (
            <div className="flex flex-wrap gap-2">
              {skill.labels!.map((label) => (
                <span
                  key={label.slug}
                  className={cn(
                    'inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium',
                    label.type === 'PRIVILEGED'
                      ? 'border-amber-500/40 bg-amber-100 text-amber-900'
                      : 'border-slate-300 bg-slate-100 text-slate-800',
                  )}
                >
                  {label.displayName}
                </span>
              ))}
            </div>
          )}
          {isPendingPreview && (
            <Card className="border-amber-500/30 bg-amber-500/5 p-4 text-sm text-muted-foreground">
              <div className="font-medium text-foreground">{t('skillDetail.pendingPreviewTitle')}</div>
              <p className="mt-1">{t('skillDetail.pendingPreviewDescription')}</p>
            </Card>
          )}
        </div>

        <Tabs defaultValue="readme">
          <TabsList>
            <TabsTrigger value="readme">{t('skillDetail.tabOverview')}</TabsTrigger>
            <TabsTrigger value="files">{t('skillDetail.tabFiles')}</TabsTrigger>
            <TabsTrigger value="versions">{t('skillDetail.tabVersions')}</TabsTrigger>
          </TabsList>

          <TabsContent value="readme" className="mt-6">
            {readme ? (
              <Card className="p-8 space-y-4">
                {documentationPath ? (
                  <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">
                    {t('skillDetail.documentationSource', { path: documentationPath })}
                  </div>
                ) : null}
                <div ref={overviewSectionRef} className="space-y-4">
                  <div
                    className={cn(
                      'relative overflow-hidden transition-[max-height] duration-300 ease-out',
                      !isOverviewExpanded && isOverviewCollapsible && 'rounded-2xl',
                    )}
                    style={!isOverviewExpanded && isOverviewCollapsible ? { maxHeight: `${overviewMaxHeight}px` } : undefined}
                  >
                    <div ref={overviewContentRef}>
                      <MarkdownRenderer content={readme} />
                    </div>
                    {!isOverviewExpanded && isOverviewCollapsible ? (
                      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-28 bg-gradient-to-t from-card via-card/95 to-transparent" />
                    ) : null}
                  </div>
                  {isOverviewCollapsible ? (
                    <div className="flex justify-center">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="gap-2 rounded-full border-border/70 bg-background/90 px-5 shadow-sm backdrop-blur-sm"
                        aria-expanded={isOverviewExpanded}
                        onClick={handleToggleOverview}
                      >
                        {isOverviewExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                        {isOverviewExpanded ? t('skillDetail.collapseOverview') : t('skillDetail.expandOverview')}
                      </Button>
                    </div>
                  ) : null}
                </div>
              </Card>
            ) : readmeError ? (
              <Card className="p-8 text-center">
                <div className="text-base font-semibold text-foreground">{t('skillDetail.documentationUnavailableTitle')}</div>
                <p className="mt-2 text-sm text-muted-foreground">{t('skillDetail.documentationUnavailable')}</p>
              </Card>
            ) : (
              <Card className="p-8 space-y-4">
                <div>
                  <div className="text-base font-semibold text-foreground">{t('skillDetail.noDocumentationTitle')}</div>
                  <p className="mt-2 text-sm text-muted-foreground">{t('skillDetail.noDocumentationDescription')}</p>
                </div>
                {skill.summary ? (
                  <div className="rounded-xl border border-border/60 bg-secondary/20 p-4">
                    <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">
                      {t('skillDetail.summaryLabel')}
                    </div>
                    <p className="mt-2 text-sm leading-6 text-foreground">{skill.summary}</p>
                  </div>
                ) : null}
                <div className="text-sm text-muted-foreground">
                  {t('skillDetail.noDocumentationHint')}
                </div>
              </Card>
            )}
          </TabsContent>

          <TabsContent value="files" className="mt-6">
            {files && files.length > 0 ? (
              <FileTree files={files} />
            ) : (
              <Card className="p-8 text-muted-foreground text-center">
                {t('skillDetail.noFiles')}
              </Card>
            )}
          </TabsContent>

          <TabsContent value="versions" className="mt-6">
            <Card className="p-6">
              {versions && versions.length > 0 ? (
                <div className="space-y-0 divide-y divide-border/40">
                  {versions.map((version) => (
                    <div key={version.id} className="py-5 first:pt-0 last:pb-0">
                      <div className="flex items-start justify-between gap-4 mb-2">
                        <span className="font-semibold font-heading text-foreground flex items-center gap-2 flex-wrap min-w-0">
                          <span className="px-2.5 py-0.5 rounded-full bg-primary/10 text-primary text-sm font-mono">
                            v{version.version}
                          </span>
                          {version.status && (
                            <span className="rounded-full border border-border/60 bg-secondary/40 px-2.5 py-0.5 text-xs text-muted-foreground">
                              {version.status}
                            </span>
                          )}
                          {headlineVersion?.version === version.version && (
                            <span className="rounded-full bg-primary px-2.5 py-0.5 text-xs text-primary-foreground">
                              {t(hasPublishedPendingReview && publishedVersion?.version === version.version
                                ? 'skillDetail.currentPublicVersion'
                                : 'skillDetail.currentVersion')}
                            </span>
                          )}
                        </span>
                        <div className="flex items-center gap-3 flex-shrink-0">
                          <span className="text-sm text-muted-foreground">
                            {formatLocalDateTime(version.publishedAt, i18n.language)}
                          </span>
                          {skill.canManageLifecycle && canRereleaseVersion(version.status) && (
                            <>
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => handleOpenDiff(version.version)}
                              >
                                {t('skillDetail.compareVersions')}
                              </Button>
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => handleOpenRerelease(version.version)}
                              >
                                {t('skillDetail.rereleaseVersion')}
                              </Button>
                            </>
                          )}
                          {skill.canManageLifecycle && canDeleteVersion(version.status) && !isLastVersion && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => setDeleteVersionTarget(version.version)}
                            >
                              {t('skillDetail.deleteVersion')}
                            </Button>
                          )}
                          {skill.canManageLifecycle && canWithdrawVersion(version.status) && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => setWithdrawVersionTarget(version.version)}
                            >
                              {t('skillDetail.withdrawReview')}
                            </Button>
                          )}
                        </div>
                      </div>
                      {version.changelog && (
                        <p className="text-sm text-muted-foreground leading-relaxed">{version.changelog}</p>
                      )}
                      <div className="text-xs text-muted-foreground mt-2 flex items-center gap-3">
                        <span>{t('skillDetail.fileCount', { count: version.fileCount })}</span>
                        <span className="w-1 h-1 rounded-full bg-muted-foreground/40" />
                        <span>{(version.totalSize / 1024).toFixed(1)} KB</span>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-muted-foreground text-center py-8">{t('skillDetail.noVersions')}</div>
              )}
            </Card>
          </TabsContent>
        </Tabs>
      </div>

      {/* Sidebar */}
      <aside className="w-full lg:w-80 flex-shrink-0 space-y-5">
        <Card className="p-5 space-y-5">
          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">{t('skillDetail.version')}</div>
            <div className="font-semibold font-mono text-foreground">
              {headlineVersion ? `v${headlineVersion.version}` : '—'}
            </div>
          </div>

          <div className="h-px bg-border/40" />

          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">{t('skillDetail.downloads')}</div>
            <div className="font-semibold text-foreground">{formatCompactCount(skill.downloadCount)}</div>
          </div>

          <div className="h-px bg-border/40" />

          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">{t('skillDetail.rating')}</div>
            <div className="font-semibold text-foreground">
              {skill.ratingCount > 0 && skill.ratingAvg !== undefined ? `${skill.ratingAvg.toFixed(1)} / 5` : t('skillDetail.ratingNone')}
            </div>
          </div>

          <div className="h-px bg-border/40" />

          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">{t('skillDetail.namespaceLabel')}</div>
            <NamespaceBadge type="GLOBAL" name={namespace} />
          </div>

          <div className="h-px bg-border/40" />

          <div className="space-y-3">
            {canInteract ? (
              <>
                <StarButton skillId={skill.id} starCount={skill.starCount} onRequireLogin={requireLogin} />
                <RatingInput skillId={skill.id} onRequireLogin={requireLogin} />
                {canReport ? (
                  <Button variant="outline" className="w-full" onClick={handleOpenReport} disabled={hasReported || reportMutation.isPending}>
                    {hasReported ? t('skillDetail.reportedSkill') : reportMutation.isPending ? t('skillDetail.processing') : t('skillDetail.reportSkill')}
                  </Button>
                ) : null}
              </>
            ) : (
              <p className="text-sm text-muted-foreground">{t('skillDetail.pendingPreviewInteractionHint')}</p>
            )}
            {!user && canInteract && (
              <p className="text-xs text-muted-foreground">{t('skillDetail.loginToRate')}</p>
            )}
          </div>
        </Card>

        {publishedVersion && canInteract && (
          <Card className="p-5 space-y-4">
            <div className="text-sm font-semibold font-heading text-foreground">{t('skillDetail.install')}</div>
            {skill.status === 'ARCHIVED' && (
              <p className="text-sm text-muted-foreground">{t('skillDetail.archivedInstallHint')}</p>
            )}
            <InstallCommand
              namespace={namespace}
              slug={slug}
              version={publishedVersion.version}
            />
          </Card>
        )}

        {hasPublishedPendingReview && ownerPreviewVersion && (
          <Card className="border-amber-500/30 bg-amber-500/5 p-5 space-y-4">
            <div className="space-y-1">
              <div className="text-sm font-semibold font-heading text-foreground">{t('skillDetail.pendingReviewSectionTitle')}</div>
              <p className="text-sm text-muted-foreground">
                {t('skillDetail.pendingReviewSectionDescription', {
                  pendingVersion: ownerPreviewVersion.version,
                  publishedVersion: publishedVersion?.version ?? '',
                })}
              </p>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="rounded-xl border border-amber-500/20 bg-background/70 p-3">
                <div className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                  {t('skillDetail.pendingReviewVersionLabel')}
                </div>
                <div className="mt-2 font-mono text-sm font-semibold text-foreground">
                  v{ownerPreviewVersion.version}
                </div>
              </div>
              <div className="rounded-xl border border-amber-500/20 bg-background/70 p-3">
                <div className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                  {t('skillDetail.pendingReviewStatusLabel')}
                </div>
                <div className="mt-2 text-sm font-semibold text-amber-700">
                  {t('skillDetail.pendingReviewStatusValue')}
                </div>
              </div>
            </div>
            <Button
              variant="outline"
              onClick={() => setWithdrawVersionTarget(ownerPreviewVersion.version)}
              disabled={withdrawReviewMutation.isPending}
            >
              {withdrawReviewMutation.isPending ? t('skillDetail.processing') : t('skillDetail.withdrawReview')}
            </Button>
          </Card>
        )}

        <Button
          className="w-full"
          variant="outline"
          size="lg"
          onClick={handleDownload}
          disabled={!selectedVersionEntry || skill.status === 'ARCHIVED' || !isVersionDownloadable}
        >
          <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
          </svg>
          {t('skillDetail.download')}
        </Button>

        {skill.canManageLifecycle && selectedVersionEntry && (
          <SecurityAuditSummary skillId={skill.id} versionId={selectedVersionEntry.id} />
        )}

        <SkillLabelPanel
          namespace={namespace}
          slug={slug}
          initialLabels={skill.labels ?? []}
          canManage={canManageLabels}
          isSuperAdmin={hasRole('SUPER_ADMIN')}
        />

        {skill.canManageLifecycle && (
          <Card className="p-5 space-y-3">
            <div className="text-sm font-semibold font-heading text-foreground">{t('skillDetail.lifecycle')}</div>
            <p className="text-sm text-muted-foreground">
              {skill.status === 'ARCHIVED'
                ? t('skillDetail.archivedPublishHint')
                : t('skillDetail.lifecycleHint')}
            </p>
            <div className="space-y-3">
              <div className="rounded-xl border border-border/60 bg-secondary/20 p-3">
                <div className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                  {t('skillDetail.lifecyclePublicVersionLabel')}
                </div>
                <div className="mt-2 text-sm font-semibold text-foreground">
                  {publishedVersion ? `v${publishedVersion.version}` : t('skillDetail.lifecycleNoPublishedVersion')}
                </div>
              </div>
              <div className="rounded-xl border border-border/60 bg-secondary/20 p-3">
                <div className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                  {t('skillDetail.lifecyclePendingVersionLabel')}
                </div>
                <div className="mt-2 text-sm font-semibold text-foreground">
                  {hasPendingOwnerPreview && ownerPreviewVersion
                    ? t('skillDetail.lifecyclePendingVersionValue', { version: ownerPreviewVersion.version })
                    : t('skillDetail.lifecycleNoPendingVersion')}
                </div>
              </div>
              <div className="rounded-xl border border-border/60 bg-secondary/20 p-3">
                <div className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                  {t('skillDetail.lifecycleContainerStateLabel')}
                </div>
                <div className="mt-2 text-sm font-semibold text-foreground">
                  {resolveSkillStatusLabel(skill.status)}
                </div>
              </div>
            </div>
            <div className="flex flex-col gap-3 pt-3 border-t border-border/40">
              {skill.status === 'ARCHIVED' ? (
                <Button variant="outline" onClick={() => setUnarchiveConfirmOpen(true)} disabled={unarchiveMutation.isPending}>
                  {unarchiveMutation.isPending ? t('skillDetail.processing') : t('skillDetail.unarchiveSkill')}
                </Button>
              ) : (
                <Button variant="outline" onClick={() => setArchiveConfirmOpen(true)} disabled={archiveMutation.isPending}>
                  {archiveMutation.isPending ? t('skillDetail.processing') : t('skillDetail.archiveSkill')}
                </Button>
              )}
              {canHardDeleteSkill && (
                <Button
                  variant="destructive"
                  onClick={() => setDeleteSkillConfirmOpen(true)}
                  disabled={deleteSkillMutation.isPending}
                >
                  {deleteSkillMutation.isPending ? t('skillDetail.processing') : t('skillDetail.deleteSkill')}
                </Button>
              )}
            </div>
          </Card>
        )}

        {skill.canSubmitPromotion && publishedVersion && (
          <Card className="p-5 space-y-3">
            <div className="text-sm font-semibold font-heading text-foreground">{t('skillDetail.promotionSectionTitle')}</div>
            <p className="text-sm text-muted-foreground">
              {t('skillDetail.promotionSectionDescription', { version: publishedVersion.version })}
            </p>
            <Button variant="outline" onClick={() => setPromotionConfirmOpen(true)} disabled={submitPromotionMutation.isPending}>
              {submitPromotionMutation.isPending ? t('skillDetail.processing') : t('skillDetail.promoteToGlobal')}
            </Button>
          </Card>
        )}

        {governanceVisible && (
          <Card className="p-5 space-y-3">
            <div className="text-sm font-semibold font-heading text-foreground">{t('skillDetail.governance')}</div>
            <div className="flex flex-col gap-3">
              {canHideSkill ? (
                !skill.hidden ? (
                  <Button variant="outline" onClick={() => hideMutation.mutate()} disabled={hideMutation.isPending}>
                    {hideMutation.isPending ? t('skillDetail.processing') : t('skillDetail.hideSkill')}
                  </Button>
                ) : (
                  <Button variant="outline" onClick={() => unhideMutation.mutate()} disabled={unhideMutation.isPending}>
                    {unhideMutation.isPending ? t('skillDetail.processing') : t('skillDetail.unhideSkill')}
                  </Button>
                )
              ) : null}
              {selectedVersionEntry && (
                <Button variant="destructive" onClick={() => yankMutation.mutate()} disabled={yankMutation.isPending}>
                  {yankMutation.isPending ? t('skillDetail.processing') : t('skillDetail.yankVersion')}
                </Button>
              )}
            </div>
          </Card>
        )}
      </aside>

      <Dialog open={reportDialogOpen} onOpenChange={setReportDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('skillDetail.reportDialogTitle')}</DialogTitle>
            <DialogDescription>{t('skillDetail.reportDialogDescription')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <Input
              value={reportReason}
              onChange={(event) => setReportReason(event.target.value)}
              placeholder={t('skillDetail.reportReasonPlaceholder')}
              maxLength={200}
            />
            <Textarea
              value={reportDetails}
              onChange={(event) => setReportDetails(event.target.value)}
              placeholder={t('skillDetail.reportDetailsPlaceholder')}
              rows={5}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setReportDialogOpen(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button onClick={handleSubmitReport} disabled={reportMutation.isPending}>
              {reportMutation.isPending ? t('skillDetail.processing') : t('skillDetail.submitReport')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={promotionConfirmOpen}
        onOpenChange={setPromotionConfirmOpen}
        title={t('skillDetail.promotionConfirmTitle')}
        description={t('skillDetail.promotionConfirmDescription', {
          skill: skill.displayName,
          version: publishedVersion?.version ?? '',
        })}
        confirmText={t('skillDetail.promoteToGlobal')}
        onConfirm={handleSubmitPromotion}
      />

      <ConfirmDialog
        open={archiveConfirmOpen}
        onOpenChange={setArchiveConfirmOpen}
        title={t('skillDetail.archiveConfirmTitle')}
        description={t('skillDetail.archiveConfirmDescription', { skill: skill.displayName })}
        confirmText={t('skillDetail.archiveSkill')}
        onConfirm={handleArchive}
      />

      <ConfirmDialog
        open={unarchiveConfirmOpen}
        onOpenChange={setUnarchiveConfirmOpen}
        title={t('skillDetail.unarchiveConfirmTitle')}
        description={t('skillDetail.unarchiveConfirmDescription', { skill: skill.displayName })}
        confirmText={t('skillDetail.unarchiveSkill')}
        onConfirm={handleUnarchive}
      />

      <ConfirmDialog
        open={deleteSkillConfirmOpen}
        onOpenChange={setDeleteSkillConfirmOpen}
        title={t('skillDetail.deleteSkillConfirmTitle')}
        description={t('skillDetail.deleteSkillConfirmDescription', { skill: skill.displayName })}
        confirmText={t('skillDetail.deleteSkillContinue')}
        variant="destructive"
        onConfirm={handleOpenDeleteSkillInput}
      />

      <Dialog
        open={deleteSkillInputOpen}
        onOpenChange={(open) => {
          setDeleteSkillInputOpen(open)
          if (!open) {
            setDeleteSkillInput('')
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('skillDetail.deleteSkillInputTitle')}</DialogTitle>
            <DialogDescription>{t('skillDetail.deleteSkillInputDescription', { slug: skill.slug })}</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="rounded-lg border border-destructive/20 bg-destructive/5 p-3 text-sm text-muted-foreground">
              {t('skillDetail.deleteSkillWarning')}
            </div>
            <Input
              value={deleteSkillInput}
              onChange={(event) => setDeleteSkillInput(event.target.value)}
              placeholder={t('skillDetail.deleteSkillInputPlaceholder')}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteSkillInputOpen(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteSkill}
              disabled={!isDeleteSlugConfirmationValid(skill.slug, deleteSkillInput) || deleteSkillMutation.isPending}
            >
              {deleteSkillMutation.isPending ? t('skillDetail.processing') : t('skillDetail.deleteSkillFinal')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={!!deleteVersionTarget}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteVersionTarget(null)
          }
        }}
        title={t('skillDetail.deleteVersionConfirmTitle')}
        description={deleteVersionTarget ? t('skillDetail.deleteVersionConfirmDescription', { version: deleteVersionTarget }) : ''}
        confirmText={t('skillDetail.deleteVersion')}
        variant="destructive"
        onConfirm={handleDeleteVersion}
      />

      <ConfirmDialog
        open={!!withdrawVersionTarget}
        onOpenChange={(open) => {
          if (!open) {
            setWithdrawVersionTarget(null)
          }
        }}
        title={t('skillDetail.withdrawReviewConfirmTitle')}
        description={withdrawVersionTarget ? t('skillDetail.withdrawReviewConfirmDescription', { version: withdrawVersionTarget }) : ''}
        confirmText={t('skillDetail.withdrawReview')}
        onConfirm={handleWithdrawVersion}
      />

      <Dialog
        open={!!rereleaseTarget}
        onOpenChange={(open) => {
          if (!open) {
            setRereleaseTarget(null)
            setTargetVersionInput('')
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('skillDetail.rereleaseDialogTitle')}</DialogTitle>
            <DialogDescription>
              {rereleaseTarget ? t('skillDetail.rereleaseDialogDescription', { version: rereleaseTarget }) : ''}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <div className="text-sm text-muted-foreground">{t('skillDetail.rereleaseSourceVersion')}</div>
              <div className="rounded-lg border border-border/60 bg-secondary/30 px-3 py-2 font-mono text-sm text-foreground">
                {rereleaseTarget ? `v${rereleaseTarget}` : '—'}
              </div>
            </div>
            <div className="space-y-2">
              <div className="text-sm text-muted-foreground">{t('skillDetail.rereleaseTargetVersion')}</div>
              <Input value={targetVersionInput} onChange={(event) => setTargetVersionInput(event.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRereleaseTarget(null)}>
              {t('dialog.cancel')}
            </Button>
            <Button onClick={handleRereleaseVersion} disabled={rereleaseVersionMutation.isPending || !targetVersionInput.trim()}>
              {rereleaseVersionMutation.isPending ? t('skillDetail.processing') : t('skillDetail.rereleaseVersion')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={!!diffSourceVersion && !!diffCompareVersion}
        onOpenChange={(open) => {
          if (!open) {
            setDiffSourceVersion(null)
            setDiffCompareVersion(null)
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('skillDetail.compareDialogTitle')}</DialogTitle>
            <DialogDescription>
              {diffSourceVersion && diffCompareVersion
                ? t('skillDetail.compareDialogDescription', { source: diffSourceVersion, target: diffCompareVersion })
                : ''}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-5">
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div className="rounded-lg border border-border/60 p-3">
                <div className="text-muted-foreground">{t('skillDetail.compareSourceLabel')}</div>
                <div className="mt-1 font-mono text-foreground">v{diffSourceVersion}</div>
              </div>
              <div className="rounded-lg border border-border/60 p-3">
                <div className="text-muted-foreground">{t('skillDetail.compareTargetLabel')}</div>
                <div className="mt-1 font-mono text-foreground">v{diffCompareVersion}</div>
              </div>
            </div>

            <div className="space-y-2">
              <div className="text-sm font-semibold text-foreground">{t('skillDetail.metadataChanges')}</div>
              {metadataDiffEntries.length > 0 ? (
                <div className="space-y-2">
                  {metadataDiffEntries.map((entry) => (
                    <div key={entry.key} className="rounded-lg border border-border/60 p-3 text-sm">
                      <div className="font-medium text-foreground">{entry.key}</div>
                      <div className="mt-1 text-muted-foreground">
                        {String(entry.source ?? '—')} → {String(entry.target ?? '—')}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-sm text-muted-foreground">{t('skillDetail.noMetadataChanges')}</div>
              )}
            </div>

            <div className="space-y-2">
              <div className="text-sm font-semibold text-foreground">{t('skillDetail.readmeChange')}</div>
              <div className="text-sm text-muted-foreground">
                {diffSourceReadme !== diffCompareReadme ? t('skillDetail.readmeChanged') : t('skillDetail.readmeUnchanged')}
              </div>
            </div>

            <div className="space-y-2">
              <div className="text-sm font-semibold text-foreground">{t('skillDetail.fileChanges')}</div>
              <div className="grid grid-cols-3 gap-3 text-sm">
                <div className="rounded-lg border border-border/60 p-3">
                  <div className="text-muted-foreground">{t('skillDetail.filesAdded')}</div>
                  <div className="mt-1 font-semibold text-foreground">{fileDiffSummary.added.length}</div>
                </div>
                <div className="rounded-lg border border-border/60 p-3">
                  <div className="text-muted-foreground">{t('skillDetail.filesRemoved')}</div>
                  <div className="mt-1 font-semibold text-foreground">{fileDiffSummary.removed.length}</div>
                </div>
                <div className="rounded-lg border border-border/60 p-3">
                  <div className="text-muted-foreground">{t('skillDetail.filesChanged')}</div>
                  <div className="mt-1 font-semibold text-foreground">{fileDiffSummary.changed.length}</div>
                </div>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setDiffSourceVersion(null)
                setDiffCompareVersion(null)
              }}
            >
              {t('dialog.close')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
