import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { EmptyState } from '@/shared/components/empty-state'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { CreateNamespaceDialog } from '@/features/namespace/create-namespace-dialog'
import {
  useArchiveNamespace,
  useDeleteNamespace,
  useFreezeNamespace,
  useMyNamespaces,
  useRestoreNamespace,
  useUnfreezeNamespace,
} from '@/shared/hooks/use-namespace-queries'
import { toast } from '@/shared/lib/toast'

type PendingNamespaceAction =
  | { action: 'freeze'; slug: string; name: string }
  | { action: 'unfreeze'; slug: string; name: string }
  | { action: 'archive'; slug: string; name: string }
  | { action: 'restore'; slug: string; name: string }
  | { action: 'delete'; slug: string; name: string }

type NamespaceActionCopy = {
  title: string
  description: string
  confirmText: string
  successTitle: string
  successDescription: string
  errorTitle: string
  variant: 'default' | 'destructive'
}

type NamespaceActionMutation = {
  mutateAsync: (input: { slug: string }) => Promise<unknown>
}

type NamespaceActionMutations = {
  freeze: NamespaceActionMutation
  unfreeze: NamespaceActionMutation
  archive: NamespaceActionMutation
  restore: NamespaceActionMutation
  delete: NamespaceActionMutation
}

type NamespaceActionToast = {
  success: (title: string, description?: string) => void
  error: (title: string, description?: string) => void
}

export function resolveNamespaceActionCopy(
  t: (key: string, options?: Record<string, unknown>) => string,
  action: PendingNamespaceAction['action'],
  name: string,
): NamespaceActionCopy {
  if (action === 'freeze') {
    return {
      title: t('myNamespaces.freezeConfirmTitle'),
      description: t('myNamespaces.freezeConfirmDescription', { name }),
      confirmText: t('myNamespaces.freeze'),
      successTitle: t('myNamespaces.freezeSuccessTitle'),
      successDescription: t('myNamespaces.freezeSuccessDescription', { name }),
      errorTitle: t('myNamespaces.freezeErrorTitle'),
      variant: 'default',
    }
  }
  if (action === 'unfreeze') {
    return {
      title: t('myNamespaces.unfreezeConfirmTitle'),
      description: t('myNamespaces.unfreezeConfirmDescription', { name }),
      confirmText: t('myNamespaces.unfreeze'),
      successTitle: t('myNamespaces.unfreezeSuccessTitle'),
      successDescription: t('myNamespaces.unfreezeSuccessDescription', { name }),
      errorTitle: t('myNamespaces.unfreezeErrorTitle'),
      variant: 'default',
    }
  }
  if (action === 'archive') {
    return {
      title: t('myNamespaces.archiveConfirmTitle'),
      description: t('myNamespaces.archiveConfirmDescription', { name }),
      confirmText: t('myNamespaces.archive'),
      successTitle: t('myNamespaces.archiveSuccessTitle'),
      successDescription: t('myNamespaces.archiveSuccessDescription', { name }),
      errorTitle: t('myNamespaces.archiveErrorTitle'),
      variant: 'destructive',
    }
  }
  if (action === 'delete') {
    return {
      title: t('myNamespaces.deleteConfirmTitle'),
      description: t('myNamespaces.deleteConfirmDescription', { name }),
      confirmText: t('myNamespaces.delete'),
      successTitle: t('myNamespaces.deleteSuccessTitle'),
      successDescription: t('myNamespaces.deleteSuccessDescription', { name }),
      errorTitle: t('myNamespaces.deleteErrorTitle'),
      variant: 'destructive',
    }
  }
  return {
    title: t('myNamespaces.restoreConfirmTitle'),
    description: t('myNamespaces.restoreConfirmDescription', { name }),
    confirmText: t('myNamespaces.restore'),
    successTitle: t('myNamespaces.restoreSuccessTitle'),
    successDescription: t('myNamespaces.restoreSuccessDescription', { name }),
    errorTitle: t('myNamespaces.restoreErrorTitle'),
    variant: 'default',
  }
}

export async function executeNamespaceAction(
  pendingAction: PendingNamespaceAction,
  mutations: NamespaceActionMutations,
  copy: NamespaceActionCopy,
  notifications: NamespaceActionToast,
) {
  try {
    if (pendingAction.action === 'freeze') {
      await mutations.freeze.mutateAsync({ slug: pendingAction.slug })
    } else if (pendingAction.action === 'unfreeze') {
      await mutations.unfreeze.mutateAsync({ slug: pendingAction.slug })
    } else if (pendingAction.action === 'archive') {
      await mutations.archive.mutateAsync({ slug: pendingAction.slug })
    } else if (pendingAction.action === 'delete') {
      await mutations.delete.mutateAsync({ slug: pendingAction.slug })
    } else {
      await mutations.restore.mutateAsync({ slug: pendingAction.slug })
    }

    notifications.success(copy.successTitle, copy.successDescription)
  } catch (error) {
    notifications.error(copy.errorTitle, error instanceof Error ? error.message : '')
    throw error
  }
}

/**
 * Dashboard page for namespaces the current user can manage or review. It owns
 * namespace lifecycle actions because each action combines permissions, copy,
 * and optimistic follow-up behavior that are specific to this route.
 */
export function MyNamespacesPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { hasRole } = useAuth()
  const canCreateNamespace = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')
  const [pendingAction, setPendingAction] = useState<PendingNamespaceAction | null>(null)
  const { data: namespaces, isLoading } = useMyNamespaces()
  const freezeMutation = useFreezeNamespace()
  const unfreezeMutation = useUnfreezeNamespace()
  const archiveMutation = useArchiveNamespace()
  const restoreMutation = useRestoreNamespace()
  const deleteMutation = useDeleteNamespace()

  const handleNamespaceClick = (slug: string) => {
    navigate({ to: `/space/${encodeURIComponent(slug)}` })
  }

  const handleMembersClick = (slug: string, e: React.MouseEvent) => {
    e.stopPropagation()
    navigate({ to: `/dashboard/namespaces/${encodeURIComponent(slug)}/members` })
  }

  const handleReviewsClick = (slug: string, e: React.MouseEvent) => {
    e.stopPropagation()
    navigate({ to: `/dashboard/namespaces/${encodeURIComponent(slug)}/reviews` })
  }

  const resolveStatusLabel = (status: string) => {
    if (status === 'FROZEN') {
      return t('namespaceStatus.frozen')
    }
    if (status === 'ARCHIVED') {
      return t('namespaceStatus.archived')
    }
    return t('namespaceStatus.active')
  }

  const resolveStatusClassName = (status: string) => {
    if (status === 'FROZEN') {
      return 'bg-amber-500/10 text-amber-500 border-amber-500/20'
    }
    if (status === 'ARCHIVED') {
      return 'bg-slate-500/10 text-slate-500 border-slate-500/20'
    }
    return 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20'
  }

  const resolveHint = (status: string, type: string) => {
    if (type === 'GLOBAL') {
      return t('myNamespaces.immutableHint')
    }
    if (status === 'FROZEN') {
      return t('myNamespaces.frozenHint')
    }
    if (status === 'ARCHIVED') {
      return t('myNamespaces.archivedHint')
    }
    return t('myNamespaces.activeHint')
  }

  const handleNamespaceAction = async () => {
    if (!pendingAction) {
      return
    }

    const copy = resolveNamespaceActionCopy(t, pendingAction.action, pendingAction.name)
    await executeNamespaceAction(
      pendingAction,
      {
        freeze: freezeMutation,
        unfreeze: unfreezeMutation,
        archive: archiveMutation,
        restore: restoreMutation,
        delete: deleteMutation,
      },
      copy,
      toast,
    )
    setPendingAction(null)
  }

  if (isLoading) {
    return (
      <div className="space-y-4 animate-fade-up">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="h-32 animate-shimmer rounded-xl" />
        ))}
      </div>
    )
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader
        title={t('myNamespaces.title')}
        subtitle={t('myNamespaces.subtitle')}
        actions={canCreateNamespace ? (
          <CreateNamespaceDialog>
            <Button>{t('myNamespaces.create')}</Button>
          </CreateNamespaceDialog>
        ) : undefined}
      />

      {namespaces && namespaces.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          {namespaces.map((namespace, idx) => (
            <Card
              key={namespace.id}
              data-testid={`namespace-card-${namespace.slug}`}
              className={`p-6 cursor-pointer group animate-fade-up delay-${Math.min(idx + 1, 6)}`}
              onClick={() => handleNamespaceClick(namespace.slug)}
            >
              <div className="space-y-4">
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-3 mb-2">
                      <h3 className="font-semibold font-heading text-lg group-hover:text-primary transition-colors">
                        {namespace.displayName}
                      </h3>
                      <NamespaceBadge
                        type={namespace.type}
                        name={namespace.type === 'GLOBAL' ? t('myNamespaces.typeGlobal') : t('myNamespaces.typeTeam')}
                      />
                      <span className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium ${resolveStatusClassName(namespace.status)}`}>
                        {resolveStatusLabel(namespace.status)}
                      </span>
                    </div>
                    {namespace.description && (
                      <p className="text-sm text-muted-foreground mb-2 leading-relaxed">
                        {namespace.description}
                      </p>
                    )}
                    <div className="text-sm text-muted-foreground font-mono">@{namespace.slug}</div>
                    <div className="mt-3 rounded-lg border border-border/50 bg-secondary/40 px-3 py-2 text-sm text-muted-foreground">
                      {resolveHint(namespace.status, namespace.type)}
                    </div>
                    <div className="mt-2 text-xs uppercase tracking-[0.18em] text-muted-foreground/80">
                      {t('myNamespaces.roleLabel')}: {namespace.currentUserRole ?? t('myNamespaces.roleUnknown')}
                    </div>
                  </div>
                </div>
                <div className="flex flex-wrap gap-3">
                  {namespace.type === 'TEAM' && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={(e) => handleMembersClick(namespace.slug, e)}
                    >
                      {t('myNamespaces.manageMembers')}
                    </Button>
                  )}
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={(e) => handleReviewsClick(namespace.slug, e)}
                  >
                    {t('myNamespaces.reviewTasks')}
                  </Button>
                  {namespace.canFreeze && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation()
                        setPendingAction({ action: 'freeze', slug: namespace.slug, name: namespace.displayName })
                      }}
                    >
                      {t('myNamespaces.freeze')}
                    </Button>
                  )}
                  {namespace.canUnfreeze && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation()
                        setPendingAction({ action: 'unfreeze', slug: namespace.slug, name: namespace.displayName })
                      }}
                    >
                      {t('myNamespaces.unfreeze')}
                    </Button>
                  )}
                  {namespace.canArchive && (
                    <Button
                      variant="destructive"
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation()
                        setPendingAction({ action: 'archive', slug: namespace.slug, name: namespace.displayName })
                      }}
                    >
                      {t('myNamespaces.archive')}
                    </Button>
                  )}
                  {namespace.canRestore && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation()
                        setPendingAction({ action: 'restore', slug: namespace.slug, name: namespace.displayName })
                      }}
                    >
                      {t('myNamespaces.restore')}
                    </Button>
                  )}
                  {namespace.canDelete && (
                    <Button
                      data-testid={`delete-namespace-${namespace.slug}`}
                      variant="destructive"
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation()
                        setPendingAction({ action: 'delete', slug: namespace.slug, name: namespace.displayName })
                      }}
                    >
                      {t('myNamespaces.delete')}
                    </Button>
                  )}
                </div>
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <EmptyState
          title={t('myNamespaces.emptyTitle')}
          description={t('myNamespaces.emptyDescription')}
          action={(
            <CreateNamespaceDialog>
              <Button>{t('myNamespaces.create')}</Button>
            </CreateNamespaceDialog>
          )}
        />
      )}

      <ConfirmDialog
        open={!!pendingAction}
        onOpenChange={(open) => {
          if (!open) {
            setPendingAction(null)
          }
        }}
        title={pendingAction ? resolveNamespaceActionCopy(t, pendingAction.action, pendingAction.name).title : ''}
        description={pendingAction ? resolveNamespaceActionCopy(t, pendingAction.action, pendingAction.name).description : ''}
        confirmText={pendingAction ? resolveNamespaceActionCopy(t, pendingAction.action, pendingAction.name).confirmText : undefined}
        variant={pendingAction ? resolveNamespaceActionCopy(t, pendingAction.action, pendingAction.name).variant : 'default'}
        contentTestId={pendingAction ? `namespace-action-dialog-${pendingAction.action}` : undefined}
        confirmButtonTestId={pendingAction ? `namespace-action-confirm-${pendingAction.action}` : undefined}
        onConfirm={handleNamespaceAction}
      />
    </div>
  )
}
