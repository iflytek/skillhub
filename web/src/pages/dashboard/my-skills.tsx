import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { EmptyState } from '@/shared/components/empty-state'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { useArchiveSkill, useMySkills, useSubmitPromotion, useUnarchiveSkill, useWithdrawSkillReview } from '@/shared/hooks/use-skill-queries'
import { formatCompactCount } from '@/shared/lib/number-format'
import { toast } from '@/shared/lib/toast'
import { ApiError } from '@/api/client'

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
  const [archiveTarget, setArchiveTarget] = useState<{ namespace: string; slug: string; name: string } | null>(null)
  const [unarchiveTarget, setUnarchiveTarget] = useState<{ namespace: string; slug: string; name: string } | null>(null)
  const [withdrawTarget, setWithdrawTarget] = useState<{ namespace: string; slug: string; name: string; version: string } | null>(null)
  const [promotionTarget, setPromotionTarget] = useState<{ skillId: number; versionId: number; name: string; version: string } | null>(null)
  const { data: skills, isLoading } = useMySkills()
  const archiveMutation = useArchiveSkill()
  const unarchiveMutation = useUnarchiveSkill()
  const withdrawMutation = useWithdrawSkillReview()
  const submitPromotionMutation = useSubmitPromotion()

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${slug}` })
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
    return status
  }

  const resolveStatusClassName = (status?: string) => {
    if (status === 'ARCHIVED') {
      return 'bg-slate-500/10 text-slate-500 border-slate-500/20'
    }
    if (status === 'PENDING_REVIEW') {
      return 'bg-amber-500/10 text-amber-500 border-amber-500/20'
    }
    if (status === 'PUBLISHED') {
      return 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20'
    }
    return 'bg-secondary/60 text-muted-foreground border-border/40'
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

      {skills && skills.length > 0 ? (
        <div className="grid grid-cols-1 gap-4">
          {skills.map((skill, idx) => (
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
                  <div className="flex items-center gap-4 text-sm text-muted-foreground">
                    <span className="px-2.5 py-0.5 rounded-full bg-secondary/60 text-xs">@{skill.namespace}</span>
                    {skill.latestVersion && (
                      <span className="font-mono text-xs">v{skill.latestVersion}</span>
                    )}
                    {skill.status ? (
                      <span className={`rounded-full border px-2.5 py-0.5 text-xs ${resolveStatusClassName(skill.status)}`}>
                        {resolveStatusLabel(skill.status)}
                      </span>
                    ) : null}
                    {skill.latestVersionStatus ? (
                      <span className={`rounded-full border px-2.5 py-0.5 text-xs ${resolveStatusClassName(skill.latestVersionStatus)}`}>
                        {resolveStatusLabel(skill.latestVersionStatus)}
                      </span>
                    ) : null}
                    <span className="flex items-center gap-1">
                      <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
                      </svg>
                      {formatCompactCount(skill.downloadCount)}
                    </span>
                  </div>
                </div>
                <div className="flex items-center gap-2 pl-4">
                  {skill.latestVersionStatus === 'PENDING_REVIEW' && skill.latestVersion ? (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={(event) => {
                        event.stopPropagation()
                        const pendingVersion = skill.latestVersion
                        if (!pendingVersion) {
                          return
                        }
                        setWithdrawTarget({
                          namespace: skill.namespace,
                          slug: skill.slug,
                          name: skill.displayName,
                          version: pendingVersion,
                        })
                      }}
                    >
                      {t('mySkills.withdrawReview')}
                    </Button>
                  ) : skill.canSubmitPromotion && skill.latestVersionId && skill.latestVersion ? (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={(event) => {
                        event.stopPropagation()
                        setPromotionTarget({
                          skillId: skill.id,
                          versionId: skill.latestVersionId!,
                          name: skill.displayName,
                          version: skill.latestVersion!,
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
          ))}
        </div>
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
