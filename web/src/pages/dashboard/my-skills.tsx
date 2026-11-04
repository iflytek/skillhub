import { useCallback, useEffect, useRef, useState } from 'react'
import { Archive, ArrowUpRight, Copy, Download, Eye, GitCompareArrows, Globe, LockKeyhole, MoreHorizontal, Package, Plus, RotateCcw, Search, Undo2, Upload, Users } from 'lucide-react'
import styles from './my-skills.module.css'
import { useLocation, useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/shared/ui/dropdown-menu'
import { copyToClipboard } from '@/shared/lib/clipboard'
import { SkillVersionCompareContent } from '@/features/skill/skill-version-compare-content'
import { rememberMySkillsScroll, readMySkillsScroll, takeMySkillsScroll } from './my-skills-scroll'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { EmptyState } from '@/shared/components/empty-state'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { useArchiveSkill, useUnarchiveSkill, useWithdrawSkillReview } from '@/shared/hooks/use-skill-queries'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { useMySkills, useSubmitPromotion } from '@/shared/hooks/use-user-queries'
import { useRestoreHiddenSkill } from '@/features/admin/use-admin-skills'
import { getHeadlineVersion, getPublishedVersion, getOwnerPreviewVersion, hasPendingOwnerPreview } from '@/shared/lib/skill-lifecycle'
import { formatCompactCount } from '@/shared/lib/number-format'
import { toast } from '@/shared/lib/toast'
import { withBasePath } from '@/shared/lib/base-path'
import { buildReturnTo } from '@/shared/lib/auth-route'
import { SkillVisibilityDialog } from '@/features/skill/skill-visibility-dialog'
import { SkillAvatar } from '@/features/skill/skill-avatar'
import { SkillAvatarManager } from '@/features/skill/skill-avatar-manager'
import { ApiError } from '@/api/client'
import { getMySkillEmptyStateKey, getMySkillFilters, type MySkillFilter } from './my-skill-filters'

const PAGE_SIZE = 10
const ALL_NAMESPACES_VALUE = '__all_namespaces__'

/**
 * Dashboard page for skills owned by the current user.
 *
 * It combines lifecycle display, archive and unarchive actions, review withdrawal, and promotion
 * submission into one management surface.
 */
function getPromotionConflictKey(error: ApiError): 'promotion.duplicate_pending' | 'promotion.already_promoted' | null {
  if (error.serverMessageKey === 'promotion.duplicate_pending') {
    return 'promotion.duplicate_pending'
  }
  if (error.serverMessageKey === 'promotion.already_promoted') {
    return 'promotion.already_promoted'
  }
  return null
}

export function MySkillsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const search = useSearch({ from: '/dashboard/skills' })
  const { t } = useTranslation()
  const { hasRole, user } = useAuth()

  // The URL is the source of truth for page / filter / namespace / keyword so the
  // search context survives navigating into a skill and back via the returnTo link.
  const page = search.page ?? 0
  const filter = (search.filter as MySkillFilter) ?? 'ALL'
  const namespaceFilter = search.namespace ?? ''
  const keyword = search.q ?? ''

  // Input remains local until the user explicitly submits the search.
  const [keywordInput, setKeywordInput] = useState(keyword)

  const [compareTarget, setCompareTarget] = useState<{ namespace: string; slug: string; name: string } | null>(null)
  const [compareVersions, setCompareVersions] = useState({ from: '', to: '' })
  const compareTrigger = useRef<HTMLButtonElement | null>(null)
  const searchInput = useRef<HTMLInputElement | null>(null)
  const restoredTrigger = useRef<HTMLButtonElement | null>(null)
  const [visibilityTarget, setVisibilityTarget] = useState<{ namespace: string; slug: string; visibility: string } | null>(null)
  const scrollKey = JSON.stringify([user?.userId, keyword, namespaceFilter, filter, page])
  const [archiveTarget, setArchiveTarget] = useState<{ namespace: string; slug: string; name: string } | null>(null)
  const [unarchiveTarget, setUnarchiveTarget] = useState<{ namespace: string; slug: string; name: string } | null>(null)
  const [withdrawTarget, setWithdrawTarget] = useState<{ namespace: string; slug: string; name: string; version: string } | null>(null)
  const [promotionTarget, setPromotionTarget] = useState<{ skillId: number; versionId: number; name: string; version: string } | null>(null)
  const [restoreHiddenTarget, setRestoreHiddenTarget] = useState<{ skillId: number; name: string } | null>(null)

  const updateSearch = useCallback((next: Partial<typeof search>, options?: { replace?: boolean }) => {
    navigate({
      to: '/dashboard/skills',
      search: (prev) => ({ ...prev, ...next }),
      replace: options?.replace,
    })
  }, [navigate])

  // Sync keywordInput when navigating back via returnTo
  useEffect(() => {
    setKeywordInput(keyword)
  }, [keyword])

  const { data: skillPage, isLoading } = useMySkills({
    page,
    size: PAGE_SIZE,
    filter: filter === 'ALL' ? undefined : filter,
    q: keyword || undefined,
    namespace: namespaceFilter || undefined,
  })
  const { data: namespaceOptions } = useMyNamespaces()

  const skills = skillPage?.items ?? []
  const totalPages = skillPage ? Math.max(Math.ceil(skillPage.total / skillPage.size), 1) : 1
  const availableFilters = getMySkillFilters(hasRole('SUPER_ADMIN'))
  const canRestoreHidden = filter === 'HIDDEN' && hasRole('SUPER_ADMIN')
  const hasActiveSearch = keyword.trim() !== '' || namespaceFilter !== ''
  const hasActiveFilters = hasActiveSearch || filter !== 'ALL'
  const emptyStateKey = getMySkillEmptyStateKey(filter)
  const archiveMutation = useArchiveSkill()
  const unarchiveMutation = useUnarchiveSkill()
  const withdrawMutation = useWithdrawSkillReview()
  const submitPromotionMutation = useSubmitPromotion()
  const restoreHiddenMutation = useRestoreHiddenSkill()

  useEffect(() => {
    if (isLoading) return
    const top = readMySkillsScroll(scrollKey)
    if (top === undefined) return
    const frame = requestAnimationFrame(() => {
      takeMySkillsScroll(scrollKey)
      window.scrollTo({ top, behavior: 'instant' })
    })
    return () => cancelAnimationFrame(frame)
  }, [isLoading, scrollKey])

  // Query invalidation can remove the row after its confirmation has already closed.
  useEffect(() => {
    const trigger = restoredTrigger.current
    if (!trigger) return
    const active = document.activeElement
    if (!trigger.isConnected) {
      if (active === document.body) searchInput.current?.focus()
      restoredTrigger.current = null
    } else if (active !== trigger && active !== document.body) {
      restoredTrigger.current = null
    }
  }, [skillPage?.items])

  const restoreMoreFocus = (event: Event) => {
    event.preventDefault()
    const trigger = compareTrigger.current
    if (trigger?.isConnected) {
      restoredTrigger.current = trigger
      trigger.focus()
    } else {
      restoredTrigger.current = null
      searchInput.current?.focus()
    }
  }

  const handleSkillClick = (namespace: string, slug: string, version?: string) => {
    rememberMySkillsScroll(scrollKey, window.scrollY)
    navigate({
      to: `/space/${namespace}/${encodeURIComponent(slug)}`,
      search: { returnTo: buildReturnTo(location), version },
    })
  }

  const handleClearFilters = () => {
    setKeywordInput('')
    updateSearch({ q: undefined, namespace: undefined, filter: undefined, page: 0 })
  }

  const handleUpdateSkill = (namespace: string, slug: string, name: string, visibility?: string) => {
    navigate({
      to: '/dashboard/publish',
      search: { namespace, updateSkill: slug, updateName: name, visibility: visibility || 'PUBLIC' },
    })
  }

  const resolveStatusLabel = (status?: string) => {
    if (status === 'HIDDEN') {
      return t('mySkills.statusHidden')
    }
    if (status === 'ARCHIVED') {
      return t('mySkills.statusArchived')
    }
    if (status === 'PENDING_REVIEW') {
      return t('mySkills.statusPendingReview')
    }
    if (status === 'PUBLISHED') {
      return t('mySkills.statusPublished')
    }
    if (status === 'REJECTED') {
      return t('mySkills.statusRejected')
    }
    if (status === 'SCANNING') {
      return t('mySkills.statusScanning')
    }
    if (status === 'SCAN_FAILED') {
      return t('mySkills.statusScanFailed')
    }
    if (status === 'UPLOADED') {
      return t('skillDetail.versionStatusUploaded')
    }
    return status
  }

  const resolveStatusClassName = (status?: string) => {
    if (status === 'HIDDEN') {
      return 'status-pill'
    }
    if (status === 'ARCHIVED') {
      return 'status-pill'
    }
    if (status === 'PENDING_REVIEW') {
      return `status-pill ${styles.statusReview}`
    }
    if (status === 'PUBLISHED') {
      return `status-pill ${styles.statusPublished}`
    }
    if (status === 'REJECTED') {
      return `status-pill ${styles.statusRejected}`
    }
    if (status === 'SCANNING') {
      return `status-pill ${styles.statusReview}`
    }
    if (status === 'SCAN_FAILED') {
      return `status-pill ${styles.statusRejected}`
    }
    if (status === 'UPLOADED') {
      return `status-pill ${styles.statusReview}`
    }
    return 'status-pill'
  }

  const handleArchiveSkill = async () => {
    if (!archiveTarget) {
      return
    }
    try {
      await archiveMutation.mutateAsync({
        namespace: archiveTarget.namespace,
        slug: archiveTarget.slug,
      })
      toast.success(
        t('mySkills.archiveSuccessTitle'),
        t('mySkills.archiveSuccessDescription', { skill: archiveTarget.name }),
      )
      setArchiveTarget(null)
    } catch (error) {
      toast.error(t('mySkills.archiveErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  const handleUnarchiveSkill = async () => {
    if (!unarchiveTarget) {
      return
    }
    try {
      await unarchiveMutation.mutateAsync({
        namespace: unarchiveTarget.namespace,
        slug: unarchiveTarget.slug,
      })
      toast.success(
        t('mySkills.unarchiveSuccessTitle'),
        t('mySkills.unarchiveSuccessDescription', { skill: unarchiveTarget.name }),
      )
      setUnarchiveTarget(null)
    } catch (error) {
      toast.error(t('mySkills.unarchiveErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  const handleWithdrawSkill = async () => {
    if (!withdrawTarget) {
      return
    }
    try {
      await withdrawMutation.mutateAsync({
        namespace: withdrawTarget.namespace,
        slug: withdrawTarget.slug,
        version: withdrawTarget.version,
      })
      toast.success(
        t('mySkills.withdrawSuccessTitle'),
        t('mySkills.withdrawSuccessDescription', { skill: withdrawTarget.name }),
      )
      setWithdrawTarget(null)
    } catch (error) {
      toast.error(t('mySkills.withdrawErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  const handleSubmitPromotion = async () => {
    if (!promotionTarget) {
      return
    }
    try {
      await submitPromotionMutation.mutateAsync({
        sourceSkillId: promotionTarget.skillId,
        sourceVersionId: promotionTarget.versionId,
      })
      toast.success(
        t('mySkills.promotionSuccessTitle'),
        t('mySkills.promotionSuccessDescription', { skill: promotionTarget.name, version: promotionTarget.version }),
      )
      setPromotionTarget(null)
    } catch (error) {
      if (error instanceof ApiError) {
        const conflictKey = getPromotionConflictKey(error)
        if (conflictKey === 'promotion.duplicate_pending') {
          toast.error(t('mySkills.promotionDuplicateTitle'), t('mySkills.promotionDuplicateDescription'))
          return
        }
        if (conflictKey === 'promotion.already_promoted') {
          toast.error(t('mySkills.promotionAlreadyPromotedTitle'), t('mySkills.promotionAlreadyPromotedDescription'))
          return
        }
      }
      toast.error(t('mySkills.promotionErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  const handleRestoreHiddenSkill = async () => {
    if (!restoreHiddenTarget) {
      return
    }
    try {
      await restoreHiddenMutation.mutateAsync(restoreHiddenTarget.skillId)
      toast.success(
        t('mySkills.restoreHiddenSuccessTitle'),
        t('mySkills.restoreHiddenSuccessDescription', { skill: restoreHiddenTarget.name }),
      )
      setRestoreHiddenTarget(null)
    } catch (error) {
      toast.error(t('mySkills.restoreHiddenErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  if (isLoading) {
    return (
      <div className="space-y-4 animate-fade-up">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="h-24 animate-shimmer rounded-xl" />
        ))}
      </div>
    )
  }

  return (
    <div className={`${styles.page} animate-fade-up`}>
      <DashboardPageHeader
        title={t('mySkills.title')}
        subtitle={t('mySkills.subtitle')}
        actions={<Button size="sm" className={styles.publishButton} onClick={() => navigate({ to: '/dashboard/publish' })}>
          <Plus aria-hidden="true" className="h-4 w-4" />{t('mySkills.publishNew')}
        </Button>}
      />

      <section aria-label={t('mySkills.filterLabel')} className={`${styles.filterPanel} border border-border/60`}>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <form data-analytics-id="skill.owned.search" className="flex min-w-0 flex-1 gap-2" onSubmit={event => {
            event.preventDefault()
            updateSearch({ q: keywordInput.trim() || undefined, page: 0 }, { replace: true })
          }}>
            <div className="relative min-w-0 flex-1">
              <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                ref={searchInput}
                type="search"
                value={keywordInput}
                onChange={(event) => setKeywordInput(event.target.value)}
                placeholder={t('mySkills.searchPlaceholder')}
                aria-label={t('mySkills.searchPlaceholder')}
                className={`${styles.filterControl} bg-background pl-9`}
              />
            </div>
            <Button type="submit" variant="outline" className={styles.filterControl}>{t('nav.search')}</Button>
          </form>
          {filter === 'HIDDEN' ? null : (
            <Select
              value={namespaceFilter || ALL_NAMESPACES_VALUE}
              onValueChange={(value) => {
                updateSearch({ namespace: value === ALL_NAMESPACES_VALUE ? undefined : value, page: 0 })
              }}
            >
              <SelectTrigger data-analytics-id="skill.owned.namespace.interact" aria-label={t('mySkills.namespaceFilterLabel')} className={`${styles.filterControl} bg-background sm:w-60 sm:shrink-0`}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem data-analytics-id="skill.owned.namespace" value={ALL_NAMESPACES_VALUE}>{t('mySkills.namespaceFilterAll')}</SelectItem>
                {(namespaceOptions ?? []).map((ns: { id: number; slug: string }) => (
                  <SelectItem data-analytics-id="skill.owned.namespace" key={ns.id} value={ns.slug}>
                    @{ns.slug}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>

        <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
          <div role="group" aria-label={t('mySkills.statusFilterLabel')} className="flex flex-wrap gap-2">
            {availableFilters.map((option) => (
              <Button data-analytics-id="skill.owned.filter"
                key={option}
                type="button"
                size="sm"
                className={styles.filterButton}
                variant={filter === option ? 'default' : 'outline'}
                aria-pressed={filter === option}
                onClick={() => {
                  updateSearch({ filter: option === 'ALL' ? undefined : option, page: 0 })
                }}
              >
                {t(`mySkills.filters.${option}`)}
              </Button>
            ))}
          </div>
          <div className={`${styles.resultCount} flex items-center text-muted-foreground`}>
            {skillPage ? <span aria-live="polite" aria-atomic="true">{t('mySkills.resultCount', { count: skillPage.total })}</span> : null}
            {hasActiveFilters ? (
              <Button data-analytics-id="skill.owned.clear" type="button" size="sm" variant="ghost" className={styles.clearButton} onClick={handleClearFilters}>
                {t('mySkills.clearSearch')}
              </Button>
            ) : null}
          </div>
        </div>
      </section>

      {skillPage && skillPage.total > 0 ? (
        <>
          <div className={`${styles.skillList} grid grid-cols-1 gap-3`}>
            {skills.map((skill) => {
              const ownerReadOnly = skill.managementState?.ownerReadOnly === true
              const hidden = filter === 'HIDDEN'
              const relatedNamespace = skill.managementState?.relatedNamespace
              const relatedSlug = skill.managementState?.relatedSlug
              const headlineVersion = getHeadlineVersion(skill)
              const publishedVersion = getPublishedVersion(skill)
              const ownerPreviewVersion = getOwnerPreviewVersion(skill)
              const hasPendingPreview = hasPendingOwnerPreview(skill)
              const preview = ownerPreviewVersion && ownerPreviewVersion.id !== publishedVersion?.id && ownerPreviewVersion.status !== 'YANKED'
                ? ownerPreviewVersion : null
              const displayName = preview?.displayName ?? skill.displayName
              const summary = preview?.summary ?? skill.summary
              const mainStatus = hidden ? 'HIDDEN' : skill.status === 'ARCHIVED' || skill.status === 'HIDDEN'
                ? skill.status : preview?.status ?? headlineVersion?.status ?? skill.status
              const writable = !hidden && !ownerReadOnly
              const canChangeVisibility = writable && skill.status === 'ACTIVE' && !!publishedVersion
              const coordinate = `@${skill.namespace}/${skill.slug}`
              const openDetail = () => handleSkillClick(skill.namespace, skill.slug, preview?.version)
              const detailSearch = new URLSearchParams({ returnTo: buildReturnTo(location) })
              if (preview?.version) detailSearch.set('version', preview.version)

              return (
                <Card key={skill.id} data-analytics-id={hidden ? undefined : 'skill.owned.open'}
                  className={`${styles.skillCard} relative border-border/70 hover:shadow-none ${hidden ? '' : 'cursor-pointer hover:border-border'}`}
                  onClick={hidden ? undefined : openDetail}>
                  <div className="flex items-start gap-3">
                    <SkillAvatar name={displayName} url={skill.avatarUrl} size="h-8 w-8 text-xs" />
                    <div className="min-w-0 flex-1 space-y-2">
                      <div className="flex items-start justify-between gap-3">
                        <div className={`${styles.titleGroup} flex min-w-0 flex-1 items-center gap-3`}>
                          <h3 className={`${styles.skillTitle} min-w-0 flex-1 truncate`} title={displayName}>
                            {hidden ? displayName : <a href={withBasePath(`/space/${skill.namespace}/${encodeURIComponent(skill.slug)}?${detailSearch}`)}
                              className="block truncate rounded-sm hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                              onClick={event => {
                                event.stopPropagation()
                                if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
                                event.preventDefault()
                                openDetail()
                              }}>{displayName}</a>}
                          </h3>
                          {mainStatus && <span className={`${resolveStatusClassName(mainStatus)} ${styles.status} shrink-0`}>
                            {hasPendingPreview && publishedVersion && mainStatus === 'PENDING_REVIEW'
                              ? t('mySkills.statusUpdatePendingReview') : resolveStatusLabel(mainStatus)}
                          </span>}
                        </div>
                        <div className="flex shrink-0 items-center gap-2" onClick={event => event.stopPropagation()}>
                          {writable && skill.status !== 'ARCHIVED' && <Button data-analytics-id="skill.update.open" size="sm" variant="outline"
                            className={`${styles.updateButton} ${styles.desktopUpdate}`} onClick={event => {
                              event.stopPropagation()
                              handleUpdateSkill(skill.namespace, skill.slug, displayName, skill.visibility ?? 'PUBLIC')
                            }}><Upload aria-hidden="true" className="h-4 w-4" />{t('mySkills.update')}</Button>}
                          {/* Keep the menu nonmodal so a new Dialog owns the focus trap during the handoff. */}
                          <DropdownMenu modal={false}>
                            <DropdownMenuTrigger asChild>
                              <Button size="icon" variant="outline" className={styles.moreButton}
                                title={t('mySkills.moreActions')} aria-label={t('mySkills.moreActionsFor', { skill: displayName })}
                                onClick={event => { event.stopPropagation(); compareTrigger.current = event.currentTarget }}
                                onKeyDown={event => { compareTrigger.current = event.currentTarget }}>
                                <MoreHorizontal aria-hidden="true" className="h-4 w-4" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end" className={styles.moreMenu} onClick={event => event.stopPropagation()}
                              onCloseAutoFocus={event => { if (compareTarget || visibilityTarget || archiveTarget || unarchiveTarget || withdrawTarget || promotionTarget || restoreHiddenTarget) event.preventDefault() }}>
                              {!hidden && <DropdownMenuItem onSelect={openDetail}><Eye aria-hidden="true" className="mr-2 h-4 w-4" />{t('mySkills.viewDetail')}</DropdownMenuItem>}
                              <DropdownMenuItem onSelect={async () => {
                                try { await copyToClipboard(coordinate); toast.success(t('mySkills.coordinateCopied')) }
                                catch { toast.error(t('mySkills.coordinateCopyFailed')) }
                              }}><Copy aria-hidden="true" className="mr-2 h-4 w-4" />{t('mySkills.copyCoordinate')}</DropdownMenuItem>
                              {!hidden && <DropdownMenuItem onSelect={() => {
                                setCompareVersions({ from: '', to: '' })
                                setCompareTarget({ namespace: skill.namespace, slug: skill.slug, name: displayName })
                              }}><GitCompareArrows aria-hidden="true" className="mr-2 h-4 w-4" />{t('mySkills.compareVersions')}</DropdownMenuItem>}
                              {writable && skill.status !== 'ARCHIVED' && <DropdownMenuItem data-analytics-id="skill.update.open" className={styles.mobileUpdate}
                                onSelect={() => handleUpdateSkill(skill.namespace, skill.slug, displayName, skill.visibility ?? 'PUBLIC')}>
                                <Upload aria-hidden="true" className="mr-2 h-4 w-4" />{t('mySkills.update')}
                              </DropdownMenuItem>}
                              {skill.managementState?.promotionRole && relatedNamespace && relatedSlug && <DropdownMenuItem data-analytics-id="skill.related.open" onSelect={() => handleSkillClick(relatedNamespace, relatedSlug)}>
                                <ArrowUpRight aria-hidden="true" className="mr-2 h-4 w-4" />{t(skill.managementState?.promotionRole === 'TEAM_SOURCE' ? 'mySkills.viewGlobalCopy' : 'mySkills.viewTeamSource')}
                              </DropdownMenuItem>}
                              {canChangeVisibility && <><DropdownMenuSeparator /><DropdownMenuItem data-analytics-id="skill.visibility.open" disabled={hasPendingPreview}
                                onSelect={() => setVisibilityTarget({ namespace: skill.namespace, slug: skill.slug, visibility: skill.visibility ?? 'PUBLIC' })}>
                                <Eye aria-hidden="true" className="mr-2 h-4 w-4" />{t('skillVisibility.title')}
                              </DropdownMenuItem></>}
                              {writable && (hasPendingPreview && ownerPreviewVersion ? <DropdownMenuItem data-analytics-id="skill.review.withdraw" onSelect={() => setWithdrawTarget({ namespace: skill.namespace, slug: skill.slug, name: displayName, version: ownerPreviewVersion.version })}>
                                <Undo2 aria-hidden="true" className="mr-2 h-4 w-4" />{t('mySkills.withdrawReview')}
                              </DropdownMenuItem> : skill.canSubmitPromotion && publishedVersion ? <DropdownMenuItem data-analytics-id={skill.managementState?.promotionRole === 'TEAM_SOURCE' ? 'skill.promotion.update.request' : 'skill.promotion.create.request'}
                                onSelect={() => setPromotionTarget({ skillId: skill.id, versionId: publishedVersion.id, name: displayName, version: publishedVersion.version })}>
                                <Globe aria-hidden="true" className="mr-2 h-4 w-4" />{t(skill.managementState?.promotionRole === 'TEAM_SOURCE' ? 'mySkills.updateGlobalVersion' : 'mySkills.promoteToGlobal')}
                              </DropdownMenuItem> : skill.status === 'ARCHIVED' ? <><DropdownMenuSeparator /><DropdownMenuItem data-analytics-id="skill.unarchive.request" onSelect={() => setUnarchiveTarget({ namespace: skill.namespace, slug: skill.slug, name: displayName })}>
                                <RotateCcw aria-hidden="true" className="mr-2 h-4 w-4" />{t('mySkills.unarchive')}
                              </DropdownMenuItem></> : publishedVersion ? <><DropdownMenuSeparator /><DropdownMenuItem data-analytics-id="skill.archive.request" className={styles.dangerAction} onSelect={() => setArchiveTarget({ namespace: skill.namespace, slug: skill.slug, name: displayName })}>
                                <Archive aria-hidden="true" className="mr-2 h-4 w-4" />{t('mySkills.archive')}
                              </DropdownMenuItem></> : null)}
                              {canRestoreHidden && <><DropdownMenuSeparator /><DropdownMenuItem data-analytics-id="skill.restore.request" onSelect={() => setRestoreHiddenTarget({ skillId: skill.id, name: displayName })}>
                                <RotateCcw aria-hidden="true" className="mr-2 h-4 w-4" />{t('mySkills.restoreHidden')}
                              </DropdownMenuItem></>}
                            </DropdownMenuContent>
                          </DropdownMenu>
                        </div>
                      </div>
                      {summary && <p className={`${styles.description} line-clamp-2 break-words text-muted-foreground [overflow-wrap:anywhere]`}>{summary}</p>}
                      <div className={`${styles.metadata} flex flex-wrap items-center text-muted-foreground`}>
                        <span className={`${styles.metadataItem} ${styles.coordinate}`} title={coordinate}><Package aria-hidden="true" strokeWidth={1.65} className="h-[13px] w-[13px] shrink-0" /><span className="truncate">{coordinate}</span></span>
                        <span className={styles.metadataItem} title={t('skillVisibility.current', { visibility: t(`skillVisibility.${skill.visibility ?? 'PUBLIC'}`) })}>
                          {skill.visibility === 'PRIVATE' ? <LockKeyhole aria-hidden="true" strokeWidth={1.65} className="h-[13px] w-[13px] shrink-0" />
                            : skill.visibility === 'NAMESPACE_ONLY' ? <Users aria-hidden="true" strokeWidth={1.65} className="h-[13px] w-[13px] shrink-0" />
                              : <Globe aria-hidden="true" strokeWidth={1.65} className="h-[13px] w-[13px] shrink-0" />}
                          {t(`publish.visibilityOptions.${skill.visibility === 'PRIVATE' ? 'private' : skill.visibility === 'NAMESPACE_ONLY' ? 'namespaceOnly' : 'public'}`)}
                        </span>
                        {preview && publishedVersion ? <span className={styles.pendingVersion}>{t('mySkills.versionDifference', { version: preview.version, published: publishedVersion.version })}</span>
                          : headlineVersion ? <span className="font-mono">v{headlineVersion.version}</span> : null}
                        <span className={`${styles.metadataItem} ${styles.downloads}`} title={t('mySkills.downloadCount')}><Download aria-hidden="true" strokeWidth={1.65} className="h-[13px] w-[13px] shrink-0" />{formatCompactCount(skill.downloadCount)}</span>
                      </div>
                      {ownerReadOnly && <p role="status" className={`${styles.note} text-muted-foreground`}>{t('mySkills.ownerReadOnly')}</p>}
                      {skill.managementState?.promotionRole && <p className={`${styles.note} text-muted-foreground`}>{t(`mySkills.promotionRoles.${skill.managementState.promotionRole}`)}</p>}
                      {skill.status === 'ACTIVE' && !hidden && <SkillAvatarManager namespace={skill.namespace} slug={skill.slug} previewOnly />}
                      {skill.managementState?.requestedVisibility && <p role="status" className={`${styles.note} text-muted-foreground`}>
                        {t('skillVisibility.pendingList', { visibility: t(`skillVisibility.${skill.managementState.requestedVisibility}`) })}
                      </p>}
                    </div>
                  </div>
                </Card>
              )
            })}
          </div>

          {skillPage.total > PAGE_SIZE ? (
            <Pagination analyticsId="skill.owned.paginate" page={page} totalPages={totalPages} onPageChange={(next) => updateSearch({ page: next })} />
          ) : null}
        </>
      ) : (
        <EmptyState
          title={hasActiveSearch ? t('mySkills.emptySearchTitle') : t(emptyStateKey.title)}
          description={hasActiveSearch ? t('mySkills.emptySearchDescription') : t(emptyStateKey.description)}
          action={
            hasActiveSearch ? (
              <Button data-analytics-id="skill.owned.clear" size="lg" variant="outline" onClick={handleClearFilters}>
                {t('mySkills.clearSearch')}
              </Button>
            ) : undefined
          }
        />
      )}

      {visibilityTarget && <SkillVisibilityDialog {...visibilityTarget} open hideTrigger onOpenChange={open => { if (!open) setVisibilityTarget(null) }}
        onCloseAutoFocus={restoreMoreFocus} />}
      <Dialog open={!!compareTarget} onOpenChange={open => { if (!open) setCompareTarget(null) }}>
        <DialogContent className="w-[calc(100vw-2rem)] max-w-6xl max-h-[85vh] gap-3 p-4 sm:p-6"
          onCloseAutoFocus={restoreMoreFocus}>
          <DialogHeader className="pr-8">
            <DialogTitle>{t('mySkills.compareVersions')}</DialogTitle>
            <DialogDescription className="break-all">{compareTarget?.name}</DialogDescription>
          </DialogHeader>
          {compareTarget && <SkillVersionCompareContent key={`${compareTarget.namespace}/${compareTarget.slug}`}
            namespace={compareTarget.namespace} slug={compareTarget.slug} {...compareVersions} onVersionsChange={setCompareVersions} />}
        </DialogContent>
      </Dialog>

      <ConfirmDialog onCloseAutoFocus={restoreMoreFocus} confirmAnalyticsId="skill.restore.confirm" cancelAnalyticsId="skill.restore.cancel"
        open={!!restoreHiddenTarget}
        onOpenChange={(open) => {
          if (!open) {
            setRestoreHiddenTarget(null)
          }
        }}
        title={t('mySkills.restoreHiddenConfirmTitle')}
        description={restoreHiddenTarget ? t('mySkills.restoreHiddenConfirmDescription', { skill: restoreHiddenTarget.name }) : ''}
        confirmText={t('mySkills.restoreHidden')}
        onConfirm={handleRestoreHiddenSkill}
      />

      <ConfirmDialog
        onCloseAutoFocus={restoreMoreFocus}
        confirmAnalyticsId={skillPage?.items.find((skill) => skill.id === promotionTarget?.skillId)?.managementState?.promotionRole === 'TEAM_SOURCE' ? 'skill.promotion.update.confirm' : 'skill.promotion.create.confirm'}
        cancelAnalyticsId={skillPage?.items.find((skill) => skill.id === promotionTarget?.skillId)?.managementState?.promotionRole === 'TEAM_SOURCE' ? 'skill.promotion.update.cancel' : 'skill.promotion.create.cancel'}
        open={!!promotionTarget}
        onOpenChange={(open) => {
          if (!open) {
            setPromotionTarget(null)
          }
        }}
        title={t('mySkills.promotionConfirmTitle')}
        description={promotionTarget ? t('mySkills.promotionConfirmDescription', { skill: promotionTarget.name, version: promotionTarget.version }) : ''}
        confirmText={t('mySkills.submitVersionToGlobal')}
        onConfirm={handleSubmitPromotion}
      />

      <ConfirmDialog onCloseAutoFocus={restoreMoreFocus} confirmAnalyticsId="skill.archive.confirm" cancelAnalyticsId="skill.archive.cancel"
        open={!!archiveTarget}
        onOpenChange={(open) => {
          if (!open) {
            setArchiveTarget(null)
          }
        }}
        title={t('mySkills.archiveConfirmTitle')}
        description={archiveTarget ? t('mySkills.archiveConfirmDescription', { skill: archiveTarget.name }) : ''}
        confirmText={t('mySkills.archive')}
        onConfirm={handleArchiveSkill}
      />

      <ConfirmDialog onCloseAutoFocus={restoreMoreFocus} confirmAnalyticsId="skill.unarchive.confirm" cancelAnalyticsId="skill.unarchive.cancel"
        open={!!unarchiveTarget}
        onOpenChange={(open) => {
          if (!open) {
            setUnarchiveTarget(null)
          }
        }}
        title={t('mySkills.unarchiveConfirmTitle')}
        description={unarchiveTarget ? t('mySkills.unarchiveConfirmDescription', { skill: unarchiveTarget.name }) : ''}
        confirmText={t('mySkills.unarchive')}
        onConfirm={handleUnarchiveSkill}
      />

      <ConfirmDialog onCloseAutoFocus={restoreMoreFocus} confirmAnalyticsId="skill.review.withdraw.confirm" cancelAnalyticsId="skill.review.withdraw.cancel"
        open={!!withdrawTarget}
        onOpenChange={(open) => {
          if (!open) {
            setWithdrawTarget(null)
          }
        }}
        title={t('mySkills.withdrawConfirmTitle')}
        description={withdrawTarget ? t('mySkills.withdrawConfirmDescription', { skill: withdrawTarget.name }) : ''}
        confirmText={t('mySkills.withdrawReview')}
        onConfirm={handleWithdrawSkill}
      />
    </div>
  )
}
