import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { EmptyState } from '@/shared/components/empty-state'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { useArchiveSkill, useMySkills, useSubmitPromotion, useUnarchiveSkill, useWithdrawSkillReview } from '@/shared/hooks/use-skill-queries'
import { getHeadlineVersion, getPublishedVersion, getOwnerPreviewVersion, hasPendingOwnerPreview } from '@/shared/lib/skill-lifecycle'
import { formatCompactCount } from '@/shared/lib/number-format'
import { toast } from '@/shared/lib/toast'
import { ApiError } from '@/api/client'

const PAGE_SIZE = 10

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
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const [archiveTarget, setArchiveTarget] = useState<{ namespace: string; slug: string; name: string } | null>(null)
  const [unarchiveTarget, setUnarchiveTarget] = useState<{ namespace: string; slug: string; name: string } | null>(null)
  const [withdrawTarget, setWithdrawTarget] = useState<{ namespace: string; slug: string; name: string; version: string } | null>(null)
  const [promotionTarget, setPromotionTarget] = useState<{ skillId: number; versionId: number; name: string; version: string } | null>(null)
  const { data: skillPage, isLoading } = useMySkills({ page, size: PAGE_SIZE })
  const skills = skillPage?.items ?? []
  const totalPages = skillPage ? Math.max(Math.ceil(skillPage.total / skillPage.size), 1) : 1
  const archiveMutation = useArchiveSkill()
  const unarchiveMutation = useUnarchiveSkill()
  const withdrawMutation = useWithdrawSkillReview()
  const submitPromotionMutation = useSubmitPromotion()

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({
      to: `/space/${namespace}/${slug}`,
      search: { returnTo: '/dashboard/skills' },
    })
  }

  const resolveStatusLabel = (status?: string) => {
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
    return status
  }

  const resolveStatusClassName = (status?: string) => {
    if (status === 'ARCHIVED') {
      return 'status-pill status-pill--archived'
    }
    if (status === 'PENDING_REVIEW') {
      return 'status-pill status-pill--review'
    }
    if (status === 'PUBLISHED') {
      return 'status-pill status-pill--published'
    }
    if (status === 'REJECTED') {
      return 'status-pill status-pill--rejected'
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
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader
        title={t('mySkills.title')}
        subtitle={t('mySkills.subtitle')}
        actions={(
          <Button size="lg" onClick={() => navigate({ to: '/dashboard/publish' })}>
          {t('mySkills.publishNew')}
          </Button>
        )}
      />

      {skillPage && skillPage.total > 0 ? (
        <>
          <div className="grid grid-cols-1 gap-4">
            {skills.map((skill, idx) => (
              (() => {
                const headlineVersion = getHeadlineVersion(skill)
                const publishedVersion = getPublishedVersion(skill)
                const ownerPreviewVersion = getOwnerPreviewVersion(skill)
                const hasPendingPreview = hasPendingOwnerPreview(skill)

                return (
                  <Card
                    key={skill.id}
                    className={`p-5 cursor-pointer group animate-fade-up delay-${Math.min(idx + 1, 6)}`}
                    onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <h3 className="font-semibold font-heading text-lg mb-1 group-hover:text-primary transition-colors">
                          {skill.displayName}
                        </h3>
                        {skill.summary && (
                          <p className="text-sm text-muted-foreground mb-3 leading-relaxed">{skill.summary}</p>
                        )}
                        <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
                          <span className="handle-tag">@{skill.namespace}</span>
                          {headlineVersion ? (
                            <span className="font-mono text-xs">v{headlineVersion.version}</span>
                          ) : null}
                          <span className="flex items-center gap-1">
                            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
                            </svg>
                            {formatCompactCount(skill.downloadCount)}
                          </span>
                          {skill.status ? (
                            <span className={resolveStatusClassName(skill.status)}>
                              {resolveStatusLabel(skill.status)}
                            </span>
                          ) : null}
                          {headlineVersion?.status ? (
                            <span className={resolveStatusClassName(headlineVersion.status)}>
                              {resolveStatusLabel(headlineVersion.status)}
                            </span>
                          ) : null}
                          {hasPendingPreview && ownerPreviewVersion?.version !== headlineVersion?.version ? (
                            <span className={resolveStatusClassName(ownerPreviewVersion?.status)}>
                              {resolveStatusLabel(ownerPreviewVersion?.status)}
                            </span>
                          ) : null}
                          {!hasPendingPreview && ownerPreviewVersion?.status === 'REJECTED' && ownerPreviewVersion?.version !== headlineVersion?.version ? (
                            <span className={resolveStatusClassName('REJECTED')}>
                              {resolveStatusLabel('REJECTED')}
                            </span>
                          ) : null}
                        </div>
                      </div>
                      <div className="flex items-center gap-2 pl-4">
                        {hasPendingPreview && ownerPreviewVersion ? (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={(event) => {
                              event.stopPropagation()
                              setWithdrawTarget({
                                namespace: skill.namespace,
                                slug: skill.slug,
                                name: skill.displayName,
                                version: ownerPreviewVersion.version,
                              })
                            }}
                          >
                            {t('mySkills.withdrawReview')}
                          </Button>
                        ) : skill.canSubmitPromotion && publishedVersion ? (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={(event) => {
                              event.stopPropagation()
                              setPromotionTarget({
                                skillId: skill.id,
                                versionId: publishedVersion.id,
                                name: skill.displayName,
                                version: publishedVersion.version,
                              })
                            }}
                          >
                            {t('mySkills.promoteToGlobal')}
                          </Button>
                        ) : skill.status === 'ARCHIVED' ? (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={(event) => {
                              event.stopPropagation()
                              setUnarchiveTarget({
                                namespace: skill.namespace,
                                slug: skill.slug,
                                name: skill.displayName,
                              })
                            }}
                          >
                            {t('mySkills.unarchive')}
                          </Button>
                        ) : (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={(event) => {
                              event.stopPropagation()
                              setArchiveTarget({
                                namespace: skill.namespace,
                                slug: skill.slug,
                                name: skill.displayName,
                              })
                            }}
                          >
                            {t('mySkills.archive')}
                          </Button>
                        )}
                        <svg className="w-5 h-5 text-muted-foreground group-hover:text-primary transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                        </svg>
                      </div>
                    </div>
                  </Card>
                )
              })()
            ))}
          </div>

          {skillPage.total > PAGE_SIZE ? (
            <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
          ) : null}
        </>
      ) : (
        <EmptyState
          title={t('mySkills.emptyTitle')}
          description={t('mySkills.emptyDescription')}
          action={
            <Button size="lg" onClick={() => navigate({ to: '/dashboard/publish' })}>
              {t('mySkills.publishSkill')}
            </Button>
          }
        />
      )}

      <ConfirmDialog
        open={!!promotionTarget}
        onOpenChange={(open) => {
          if (!open) {
            setPromotionTarget(null)
          }
        }}
        title={t('mySkills.promotionConfirmTitle')}
        description={promotionTarget ? t('mySkills.promotionConfirmDescription', { skill: promotionTarget.name, version: promotionTarget.version }) : ''}
        confirmText={t('mySkills.promoteToGlobal')}
        onConfirm={handleSubmitPromotion}
      />

      <ConfirmDialog
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

      <ConfirmDialog
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

      <ConfirmDialog
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
