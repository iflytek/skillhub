import { useState } from 'react'
import { Link, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { AddCollectionContributorDialog } from '@/features/collection/add-collection-contributor-dialog'
import { AddCollectionSkillsDialog } from '@/features/collection/add-collection-skills-dialog'
import { CollectionShareActions } from '@/features/collection/collection-share-actions'
import { CollectionSkillRows } from '@/features/collection/collection-skill-rows'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import {
  useCollectionContributors,
  useCollectionDetail,
  useMyCollections,
  useRemoveCollectionContributor,
} from '@/shared/hooks/use-collection-queries'
import { toast } from '@/shared/lib/toast'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

type PendingContributorRemoval = {
  userId: string
}

export function CollectionDetailPage() {
  const { t } = useTranslation()
  const { user, hasRole } = useAuth()
  const { collectionId } = useParams({ from: '/dashboard/collections/$collectionId' })
  const { data, isLoading, error } = useCollectionDetail(collectionId)
  const myCollectionsQuery = useMyCollections({ page: 0, size: 200 })
  const isAdminEquivalent = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')
  const isOwner = !!data?.ownerId && data.ownerId === user?.userId
  const isContributor = !!data
    && !isOwner
    && !!myCollectionsQuery.data?.items.some((item) => String(item.id) === collectionId && item.ownerId !== user?.userId)
  const canManageContributors = isOwner || isAdminEquivalent
  const canManageSkills = !!data && (isOwner || isAdminEquivalent || isContributor)
  const canReorderSkills = canManageSkills
  const contributorsQuery = useCollectionContributors(collectionId, canManageContributors && !!data)
  const removeContributorMutation = useRemoveCollectionContributor()
  const [pendingRemoval, setPendingRemoval] = useState<PendingContributorRemoval | null>(null)
  const [removingUserId, setRemovingUserId] = useState<string | null>(null)

  const members = data?.members ?? []
  const memberSkillIds = members
    .map((member) => member.skillId)
    .filter((skillId): skillId is number => typeof skillId === 'number')
  const hiddenCount = data?.additionalMembersHiddenFromActorCount ?? 0

  const removeContributor = async () => {
    if (!pendingRemoval) {
      return
    }
    setRemovingUserId(pendingRemoval.userId)
    try {
      await removeContributorMutation.mutateAsync({
        id: collectionId,
        userId: pendingRemoval.userId,
      })
      toast.success(t('collections.contributorsRemoveSuccess'))
    } catch (removeError) {
      toast.error(t('collections.contributorsRemoveError'), removeError instanceof Error ? removeError.message : '')
      throw removeError
    } finally {
      setRemovingUserId(null)
      setPendingRemoval(null)
    }
  }

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME} data-testid="collection-detail-page">
      <DashboardPageHeader
        title={data?.title ?? t('collections.detailTitle')}
        subtitle={data?.description || t('collections.detailSubtitle')}
        actions={(
          <div className="flex flex-wrap gap-2">
            <Link to="/dashboard/collections" className={buttonVariants({ variant: 'outline' })} data-testid="collection-detail-back-to-list">
              {t('collections.backToList')}
            </Link>
            {data && canManageContributors ? (
              <Link
                to="/dashboard/collections/$collectionId/edit"
                params={{ collectionId: String(data.id) }}
                className={buttonVariants({ variant: 'outline' })}
                data-testid="collection-detail-edit"
              >
                {t('collections.editAction')}
              </Link>
            ) : null}
          </div>
        )}
      />

      {isLoading ? <p className="text-sm text-muted-foreground">{t('collections.loading')}</p> : null}
      {error ? <p className="text-sm text-destructive">{t('collections.loadError')}</p> : null}

      {data ? (
        <div className="space-y-6">
          <Card>
            <CardHeader className="space-y-3">
              <div className="flex flex-wrap items-center gap-2 text-xs">
                <span
                  className={`rounded-full border px-2 py-0.5 font-medium ${
                    data.visibility === 'PUBLIC'
                      ? 'border-emerald-500/40 text-emerald-700 dark:text-emerald-400'
                      : 'border-border text-muted-foreground'
                  }`}
                  data-testid="collection-detail-visibility"
                >
                  {data.visibility === 'PUBLIC' ? t('collections.visibilityPublic') : t('collections.visibilityPrivate')}
                </span>
                <span className="rounded-full border border-border px-2 py-0.5 text-muted-foreground" data-testid="collection-detail-role">
                  {isOwner ? t('collections.roleOwner') : isContributor ? t('collections.roleContributor') : t('collections.roleViewer')}
                </span>
              </div>
              <CardDescription>{t('collections.detailDescription')}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <CollectionShareActions
                ownerId={data.ownerId ?? ''}
                slug={data.slug ?? ''}
                visibility={data.visibility ?? 'PRIVATE'}
              />
              {hiddenCount > 0 ? (
                <p className="text-sm text-muted-foreground">
                  {t('collections.hiddenMembersHint', { count: hiddenCount })}
                </p>
              ) : null}
            </CardContent>
          </Card>

          <section className="space-y-3">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-lg font-semibold">{t('collections.skillsTitle')}</h2>
              {canManageSkills ? (
                <AddCollectionSkillsDialog collectionId={collectionId} collectionSkillIds={memberSkillIds}>
                  <Button type="button" variant="outline" data-testid="collection-detail-add-skill-header">
                    {t('collections.addSkillAction')}
                  </Button>
                </AddCollectionSkillsDialog>
              ) : null}
            </div>
            {members.length === 0 ? (
              <Card>
                <CardHeader>
                  <CardTitle>{t('collections.emptySkillsTitle')}</CardTitle>
                  <CardDescription>{t('collections.emptySkillsDescription')}</CardDescription>
                </CardHeader>
                <CardContent>
                  {canManageSkills ? (
                    <AddCollectionSkillsDialog collectionId={collectionId} collectionSkillIds={memberSkillIds}>
                      <Button type="button" data-testid="collection-detail-add-skill-empty">
                        {t('collections.addSkillAction')}
                      </Button>
                    </AddCollectionSkillsDialog>
                  ) : null}
                </CardContent>
              </Card>
            ) : (
              <CollectionSkillRows
                collectionId={collectionId}
                members={members}
                canReorder={canReorderSkills}
              />
            )}
          </section>

          {canManageContributors ? (
            <section className="space-y-3">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold">{t('collections.contributorsTitle')}</h2>
                <AddCollectionContributorDialog collectionId={collectionId}>
                  <Button type="button" variant="outline" data-testid="collection-detail-add-contributor">
                    {t('collections.contributorsAdd')}
                  </Button>
                </AddCollectionContributorDialog>
              </div>
              <Card>
                <CardContent className="pt-6">
                  {contributorsQuery.isLoading ? (
                    <p className="text-sm text-muted-foreground">{t('collections.contributorsLoading')}</p>
                  ) : contributorsQuery.error ? (
                    <p className="text-sm text-destructive">{t('collections.contributorsLoadError')}</p>
                  ) : (contributorsQuery.data?.length ?? 0) === 0 ? (
                    <p className="text-sm text-muted-foreground">{t('collections.contributorsEmpty')}</p>
                  ) : (
                    <ul className="space-y-2" data-testid="collection-detail-contributors-list">
                      {contributorsQuery.data?.map((contributor, index) => (
                        <li
                          key={contributor.userId ?? `contributor-${index}`}
                          className="flex items-center justify-between gap-3 rounded-md border border-border/40 p-3"
                          data-testid={contributor.userId ? `collection-detail-contributor-${contributor.userId}` : undefined}
                        >
                          <span className="font-mono text-sm" data-testid="collection-detail-contributor-user-id">
                            {contributor.userId ?? '-'}
                          </span>
                          <Button
                            type="button"
                            variant="destructive"
                            size="sm"
                            disabled={!contributor.userId || removingUserId === contributor.userId}
                            data-testid={contributor.userId ? `collection-detail-remove-contributor-${contributor.userId}` : undefined}
                            onClick={() => {
                              if (!contributor.userId) {
                                return
                              }
                              setPendingRemoval({ userId: contributor.userId })
                            }}
                          >
                            {t('collections.contributorsRemove')}
                          </Button>
                        </li>
                      ))}
                    </ul>
                  )}
                </CardContent>
              </Card>
            </section>
          ) : null}
        </div>
      ) : null}

      <ConfirmDialog
        open={!!pendingRemoval}
        onOpenChange={(open) => {
          if (!open) {
            setPendingRemoval(null)
          }
        }}
        title={t('collections.contributorsRemoveConfirmTitle')}
        description={pendingRemoval ? t('collections.contributorsRemoveConfirmDescription', { userId: pendingRemoval.userId }) : ''}
        confirmText={t('collections.contributorsRemove')}
        confirmButtonTestId="collection-detail-confirm-remove-contributor"
        variant="destructive"
        onConfirm={removeContributor}
      />
    </div>
  )
}
