import { Copy, KeyRound, Plus, RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { organizationDirectoryConnectionApi, type OrganizationDirectoryConnection } from '@/api/client'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'

type Scope = {
  viewerId: string
  organizationId: string
  canManage: boolean
  canRotateSecret: boolean
}

export function OrganizationDirectoryConnections({ viewerId, organizationId, canManage, canRotateSecret }: Scope) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [newToken, setNewToken] = useState<string | null>(null)
  const queryKey = ['organizations', viewerId, 'detail', organizationId, 'directory-connections'] as const
  const list = useQuery({
    queryKey,
    queryFn: () => organizationDirectoryConnectionApi.list(organizationId),
    refetchOnWindowFocus: true,
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey })
  const create = useMutation({
    mutationFn: () => organizationDirectoryConnectionApi.create(organizationId),
    onSuccess: async (connection) => {
      setNewToken(connection.bootstrapToken ?? null)
      await refresh()
      toast.success(t('organizationDirectoryConnections.created'))
    },
    onError: (error) => toast.error(t('organizations.failed'), error.message),
  })
  const action = useMutation({
    mutationFn: ({ connectionId, kind }: { connectionId: string; kind: 'activate' | 'suspend' | 'disable' | 'rotateSecret' }) => {
      if (kind === 'activate') return organizationDirectoryConnectionApi.activate(organizationId, connectionId)
      if (kind === 'suspend') return organizationDirectoryConnectionApi.suspend(organizationId, connectionId)
      if (kind === 'disable') return organizationDirectoryConnectionApi.disable(organizationId, connectionId)
      return organizationDirectoryConnectionApi.rotateSecret(organizationId, connectionId)
    },
    onSuccess: async (connection, variables) => {
      if (variables.kind === 'rotateSecret') setNewToken(connection.bootstrapToken ?? null)
      await refresh()
      toast.success(t('organizationDirectoryConnections.saved'))
    },
    onError: (error) => toast.error(t('organizations.failed'), error.message),
  })
  const items = list.error ? [] : list.data ?? []

  return (
    <Card className="divide-y divide-border/60">
      <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3.5 sm:px-5">
        <div>
          <h2 className="text-base font-semibold">{t('organizationDirectoryConnections.title')}</h2>
          <p className="mt-1 text-xs text-muted-foreground">{t('organizationDirectoryConnections.description')}</p>
        </div>
        {canManage ? (
          <Button size="sm" disabled={create.isPending} onClick={() => create.mutate()}>
            <Plus className="size-4" aria-hidden="true" />
            {t('organizationDirectoryConnections.create')}
          </Button>
        ) : null}
      </div>
      {list.isPending ? <p role="status" className="p-5 text-sm text-muted-foreground">{t('organizations.loading')}</p> : null}
      {list.error ? (
        <div className="flex flex-wrap items-center gap-3 p-5">
          <p role="alert" className="text-sm text-destructive">{list.error.message}</p>
          <Button size="sm" variant="outline" onClick={() => void list.refetch()}>
            <RefreshCw className="size-3.5" aria-hidden="true" />{t('organizations.retry')}
          </Button>
        </div>
      ) : null}
      {!list.isPending && !list.error && !items.length ? (
        <p className="p-5 text-sm text-muted-foreground">{t('organizationDirectoryConnections.empty')}</p>
      ) : null}
      {items.map((item) => (
        <DirectoryConnectionRow key={item.id} item={item} canManage={canManage} canRotateSecret={canRotateSecret}
          pending={action.isPending} onAction={(kind) => action.mutate({ connectionId: item.id, kind })} />
      ))}
      {newToken ? (
        <div className="space-y-2 bg-muted/30 px-4 py-4 sm:px-5">
          <p className="text-sm font-medium">{t('organizationDirectoryConnections.tokenTitle')}</p>
          <p className="text-xs text-muted-foreground">{t('organizationDirectoryConnections.tokenHint')}</p>
          <div className="flex items-center gap-2 rounded-md border border-border bg-background p-2">
            <code className="min-w-0 flex-1 break-all text-xs">{newToken}</code>
            <Button size="xs" variant="outline" onClick={() => void navigator.clipboard?.writeText(newToken)}>
              <Copy className="size-3.5" aria-hidden="true" />{t('organizationDirectoryConnections.copy')}
            </Button>
          </div>
          <Button size="sm" variant="ghost" onClick={() => setNewToken(null)}>{t('dialog.close')}</Button>
        </div>
      ) : null}
    </Card>
  )
}

function DirectoryConnectionRow({ item, canManage, canRotateSecret, pending, onAction }: {
  item: OrganizationDirectoryConnection
  canManage: boolean
  canRotateSecret: boolean
  pending: boolean
  onAction: (kind: 'activate' | 'suspend' | 'disable' | 'rotateSecret') => void
}) {
  const { t } = useTranslation()
  return (
    <div className="space-y-3 px-4 py-4 sm:px-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <div className="flex items-center gap-2">
            <KeyRound className="size-4 text-muted-foreground" aria-hidden="true" />
            <h3 className="text-sm font-medium">{t('organizationDirectoryConnections.scim')}</h3>
          </div>
          <p className="text-xs text-muted-foreground">
            {t(`organizationDirectoryConnections.status.${item.status}`, { defaultValue: item.status })} · {t('organizationDirectoryConnections.secretVersion', { version: item.secretVersion })}
          </p>
        </div>
        <code className="max-w-full break-all text-xs text-muted-foreground">{item.tokenHint}</code>
      </div>
      <div className="rounded-md border border-border/70 bg-muted/30 p-3 text-xs">
        <p className="mb-1 text-muted-foreground">{t('organizationDirectoryConnections.endpoint')}</p>
        <code className="break-all">{item.scimBaseUrl}</code>
      </div>
      <div className="flex flex-wrap gap-2">
        {canManage && item.status === 'DRAFT' ? <Button size="sm" disabled={pending} onClick={() => onAction('activate')}>{t('organizationDirectoryConnections.activate')}</Button> : null}
        {canManage && item.status === 'ACTIVE' ? <Button size="sm" variant="outline" disabled={pending} onClick={() => onAction('suspend')}>{t('organizationDirectoryConnections.suspend')}</Button> : null}
        {canManage && item.status !== 'DISABLED' ? <Button size="sm" variant="ghost" className="text-destructive hover:text-destructive" disabled={pending} onClick={() => onAction('disable')}>{t('organizationDirectoryConnections.disable')}</Button> : null}
        {canRotateSecret ? <Button size="sm" variant="outline" disabled={pending} onClick={() => onAction('rotateSecret')}>{t('organizationDirectoryConnections.rotate')}</Button> : null}
      </div>
    </div>
  )
}
