import { useState } from 'react'
import { useNavigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { NamespaceHeader } from '@/features/namespace/namespace-header'
import { NamespaceManagementBack, NamespaceManagementNav } from '@/features/namespace/namespace-management-nav'
import { executeNamespaceAction, resolveNamespaceActionCopy, type PendingNamespaceAction } from '@/features/namespace/namespace-actions'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { EmptyState } from '@/shared/components/empty-state'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { useArchiveNamespace, useDeleteNamespace, useFreezeNamespace, useMyNamespaces, useNamespaceDetail, useRestoreNamespace, useUnfreezeNamespace } from '@/shared/hooks/use-namespace-queries'
import { toast } from '@/shared/lib/toast'

export function NamespaceSettingsPage() {
  const { slug } = useParams({ from: '/dashboard/namespaces/$slug/settings' })
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: namespace, isLoading } = useNamespaceDetail(slug)
  const { data: namespaces } = useMyNamespaces()
  const managed = namespaces?.find((item) => item.slug === slug)
  const [pending, setPending] = useState<PendingNamespaceAction | null>(null)
  const mutations = { freeze: useFreezeNamespace(), unfreeze: useUnfreezeNamespace(), archive: useArchiveNamespace(), restore: useRestoreNamespace(), delete: useDeleteNamespace() }
  const copy = pending ? resolveNamespaceActionCopy(t, pending.action, pending.name) : null
  const actions = [
    { action: 'freeze', allowed: managed?.canFreeze },
    { action: 'unfreeze', allowed: managed?.canUnfreeze },
    { action: 'archive', allowed: managed?.canArchive },
    { action: 'restore', allowed: managed?.canRestore },
    { action: 'delete', allowed: managed?.canDelete },
  ] as const
  if (isLoading) return <div className="h-40 animate-shimmer rounded-lg" />
  if (!namespace) return <EmptyState title={t('namespace.notFound')} />
  const canEdit = namespace.type === 'TEAM' && namespace.status === 'ACTIVE' && (managed?.currentUserRole === 'OWNER' || managed?.currentUserRole === 'ADMIN')
  return (
    <div className="space-y-6">
      <NamespaceManagementBack />
      <NamespaceHeader namespace={namespace} canEdit={canEdit} />
      <NamespaceManagementNav slug={slug} active="settings" />
      <Card className="space-y-4 p-5">
        <h2 className="font-semibold">{t('namespaceManagement.lifecycle')}</h2>
        <p className="text-sm text-muted-foreground">{t(namespace.type === 'GLOBAL' ? 'namespaceManagement.globalReadOnly' : 'namespaceManagement.lifecycleHint')}</p>
        {namespace.type === 'TEAM' ? actions.filter((item) => item.allowed).map(({ action }) => (
          <div key={action} className="flex flex-wrap items-center justify-between gap-3 border-t border-border/50 pt-4">
            <p className="min-w-0 flex-1 text-sm text-muted-foreground">{t(`namespaceManagement.${action}Hint`)}</p>
            <Button data-analytics-id={action === 'freeze' ? 'namespace.freeze.request' : action === 'unfreeze' ? 'namespace.unfreeze.request' : action === 'archive' ? 'namespace.archive.request' : action === 'restore' ? 'namespace.restore.request' : 'namespace.delete.request'} variant={action === 'archive' || action === 'delete' ? 'destructive' : 'outline'}
              data-testid={`${action}-namespace-${slug}`} onClick={() => setPending({ action, slug, name: namespace.displayName })}>
              {t(`myNamespaces.${action}`)}
            </Button>
          </div>
        )) : null}
      </Card>
      <ConfirmDialog
        confirmAnalyticsId={pending?.action === 'freeze' ? 'namespace.freeze.confirm' : pending?.action === 'unfreeze' ? 'namespace.unfreeze.confirm' : pending?.action === 'archive' ? 'namespace.archive.confirm' : pending?.action === 'restore' ? 'namespace.restore.confirm' : pending?.action === 'delete' ? 'namespace.delete.confirm' : undefined}
        cancelAnalyticsId={pending?.action === 'freeze' ? 'namespace.freeze.cancel' : pending?.action === 'unfreeze' ? 'namespace.unfreeze.cancel' : pending?.action === 'archive' ? 'namespace.archive.cancel' : pending?.action === 'restore' ? 'namespace.restore.cancel' : pending?.action === 'delete' ? 'namespace.delete.cancel' : undefined}
        open={!!pending} onOpenChange={(open) => { if (!open) setPending(null) }}
        title={copy?.title ?? ''} description={copy?.description ?? ''} confirmText={copy?.confirmText} variant={copy?.variant}
        contentTestId={pending ? `namespace-action-dialog-${pending.action}` : undefined}
        confirmButtonTestId={pending ? `namespace-action-confirm-${pending.action}` : undefined}
        onConfirm={async () => {
          if (!pending || !copy) return
          await executeNamespaceAction(pending, mutations, copy, toast)
          setPending(null)
          if (pending.action === 'delete') await navigate({ to: '/dashboard/namespaces' })
        }} />
    </div>
  )
}
