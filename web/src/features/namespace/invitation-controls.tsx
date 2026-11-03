import { useId, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Copy, Link2, Loader2, RefreshCw, ShieldCheck, Trash2, Users } from 'lucide-react'
import { getAppBaseUrl, namespaceApi } from '@/api/client'
import { useAuth } from '@/features/auth/use-auth'
import { ApiError } from '@/shared/lib/api-error'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { BASE_PATH } from '@/shared/lib/base-path'
import { invitationUrl } from '@/shared/lib/namespace-invitation'
import { resolvePublicRegistryUrl } from '@/shared/lib/registry-url'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { InvitationHistory } from './invitation-history'

export function InvitationControls({ slug, compact = false }: { slug: string; compact?: boolean }) {
  const { t, i18n } = useTranslation()
  const linkId = useId()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const storageKey = `skillhub.namespace.issued-invitation:${user?.userId ?? ''}:${slug}`
  const inMemoryToken = useRef<{ key: string; token: string } | null>(null)
  const [storageUnavailable, setStorageUnavailable] = useState(false)
  const [confirm, setConfirm] = useState<'replace' | 'revoke' | null>(null)
  const clearStoredToken = () => {
    inMemoryToken.current = null
    try { window.sessionStorage.removeItem(storageKey) } catch { /* Storage may be unavailable. */ }
  }
  const status = useQuery({
    queryKey: ['namespace-invitation-status', slug, user?.userId],
    enabled: !compact && !!user,
    staleTime: 0,
    queryFn: async () => {
      const current = await namespaceApi.invitationStatus(slug)
      if (!current.active) {
        clearStoredToken()
        return { active: false, token: null, expiresAt: null }
      }
      let token = inMemoryToken.current?.key === storageKey ? inMemoryToken.current.token : null
      try { token = window.sessionStorage.getItem(storageKey) ?? token } catch { /* Use the current dialog's copy. */ }
      if (!token) return { active: true, token: null, expiresAt: null }
      let expiresAt: string | null = null
      try {
        const preview = await namespaceApi.previewInvitation(token)
        expiresAt = preview.expiresAt
        if (preview.namespaceSlug !== slug || Date.parse(preview.expiresAt) <= Date.now()) {
          clearStoredToken()
          return { active: true, token: null, expiresAt: null }
        }
      } catch (error) {
        if (!(error instanceof ApiError) || error.status !== 404) throw error
        clearStoredToken()
        return { active: true, token: null, expiresAt: null }
      }
      return { active: true, token, expiresAt }
    },
  })
  const link = !status.isFetching && !status.isError && status.data?.token
    ? invitationUrl(resolvePublicRegistryUrl(getAppBaseUrl(), window.location.origin, BASE_PATH), status.data.token)
    : null
  const expiresAt = !status.isFetching && !status.isError && status.data?.expiresAt
    ? new Intl.DateTimeFormat(i18n?.language ?? 'zh', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(status.data.expiresAt))
    : null
  const copyLink = async (url: string, replaced = false) => {
    try {
      await navigator.clipboard.writeText(url)
      toast.success(t(replaced ? 'invitation.replacedCopied' : 'invitation.copied'))
    } catch { toast.info(t('invitation.manualCopy')) }
  }
  const create = useMutation({
    mutationFn: (replace: boolean) => namespaceApi.createInvitation(slug, replace),
    onSuccess: async ({ token, replaced }) => {
      inMemoryToken.current = { key: storageKey, token }
      try { window.sessionStorage.setItem(storageKey, token) } catch { setStorageUnavailable(true) }
      const url = invitationUrl(resolvePublicRegistryUrl(getAppBaseUrl(), window.location.origin, BASE_PATH), token)
      await copyLink(url, replaced)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['namespace-invitation-status', slug] }),
        queryClient.invalidateQueries({ queryKey: ['namespace-invitation-history', slug, user?.userId] }),
      ])
    },
  })
  const revoke = useMutation({
    mutationFn: () => namespaceApi.revokeInvitation(slug),
    onSuccess: async () => {
      clearStoredToken()
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['namespace-invitation-status', slug] }),
        queryClient.invalidateQueries({ queryKey: ['namespace-invitation-history', slug, user?.userId] }),
      ])
      toast.success(t('invitation.revoked'))
    },
  })
  const handleCreate = async () => {
    const result = await status.refetch()
    if (result.isError || !result.data) return
    const current = result.data
    if (current.active) setConfirm('replace')
    else create.mutate(false)
  }
  return (
    <div id={compact ? undefined : 'invite'} className="space-y-5 scroll-mt-24" onClick={compact ? (event) => event.stopPropagation() : undefined}>
      {!compact ? (
        <div className="rounded-xl border bg-secondary/30 p-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <span className="flex items-center gap-2 font-medium"><Link2 className="h-4 w-4" aria-hidden="true" />{t('invitation.link')}</span>
            <span role="status" className="inline-flex items-center gap-1.5 rounded-full bg-background px-2 py-0.5 text-xs font-medium">
              {status.isFetching ? <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" /> : null}
              {t(status.isError ? 'invitation.statusError' : status.isFetching ? 'invitation.statusLoading' : status.data?.active ? 'invitation.statusActive' : 'invitation.statusInactive')}
            </span>
          </div>
          {link ? (
            <div className="mt-4 space-y-2">
              <label className="sr-only" htmlFor={linkId}>{t('invitation.link')}</label>
              <div className="flex flex-col gap-2 sm:flex-row">
                <Input id={linkId} className="h-8 min-w-0 flex-1 bg-background font-mono text-xs" readOnly value={link} onFocus={(event) => event.currentTarget.select()} />
                <Button data-analytics-id="namespace.invitation.copy" size="sm" className="shrink-0 gap-1.5 px-2.5" onClick={() => void copyLink(link)}><Copy className="h-3.5 w-3.5" aria-hidden="true" />{t('invitation.copy')}</Button>
              </div>
              {expiresAt ? <p className="text-xs text-muted-foreground">{t('invitation.expiresAtLabel')}: {expiresAt}</p> : null}
              <p className="text-xs leading-relaxed text-muted-foreground">{t(storageUnavailable ? 'invitation.storageUnavailable' : 'invitation.localLinkHint')}</p>
            </div>
          ) : null}
          {!link && !status.isFetching && !status.isError && status.data?.active ? <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{t('invitation.activeUnavailable')}</p> : null}
          {!status.isError && !status.data?.active ? (
            <div className="mt-4"><Button data-analytics-id="namespace.invitation.create" size="sm" className="gap-1.5 px-2.5" disabled={!user || status.isPending || status.isFetching || create.isPending} onClick={handleCreate}><Link2 className="h-3.5 w-3.5" aria-hidden="true" />{t('invitation.create')}</Button></div>
          ) : null}
        </div>
      ) : <Button data-analytics-id="namespace.invitation.create" variant="outline" size="sm" className="gap-1.5 px-2.5" disabled={!user || status.isFetching || create.isPending} onClick={handleCreate}>{t('invitation.create')}</Button>}
      {!compact ? (
        <div className="space-y-3 text-sm">
          <p className="font-medium">{t('invitation.detailsTitle')}</p>
          <div className="flex gap-3"><Users className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" /><p className="leading-relaxed text-muted-foreground">{t('invitation.singleUse')} {t('invitation.memberAccess')}</p></div>
          <div className="flex gap-3"><ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" /><p className="leading-relaxed text-muted-foreground">{t('invitation.accessPolicy')}</p></div>
        </div>
      ) : null}
      {(create.error || revoke.error || status.error) ? (
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-destructive/20 bg-destructive/5 p-3">
          <p role="alert" className="text-sm text-destructive">{t('invitation.error')}</p>
          {status.error ? <Button data-analytics-id="namespace.invitation.retry" variant="outline" size="sm" className="gap-1.5 px-2.5" disabled={status.isFetching} onClick={() => void status.refetch()}><RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />{t('invitation.retry')}</Button> : null}
        </div>
      ) : null}
      {!compact && !status.isError && status.data?.active ? (
        <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-4">
          <span className="text-xs text-muted-foreground">{t('invitation.managementTitle')}</span>
          <div className="flex flex-wrap gap-2">
            <Button data-analytics-id="namespace.invitation.replace" variant="outline" size="sm" className="gap-1.5 px-2.5" disabled={!user || status.isFetching || create.isPending || revoke.isPending} onClick={handleCreate}><RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />{t('invitation.replaceAction')}</Button>
            <Button data-analytics-id="namespace.invitation.revoke" variant="ghost" size="sm" className="gap-1.5 px-2.5 text-destructive hover:bg-destructive/10 hover:text-destructive" disabled={status.isFetching || revoke.isPending || create.isPending} onClick={() => setConfirm('revoke')}><Trash2 className="h-3.5 w-3.5" aria-hidden="true" />{t('invitation.revoke')}</Button>
          </div>
        </div>
      ) : null}
      {!compact && user ? <InvitationHistory key={`${slug}:${user.userId}`} slug={slug} /> : null}
      <ConfirmDialog confirmAnalyticsId={confirm === 'revoke' ? 'namespace.invitation.revoke.confirm' : confirm === 'replace' ? 'namespace.invitation.replace.confirm' : undefined} cancelAnalyticsId={confirm === 'revoke' ? 'namespace.invitation.revoke.cancel' : confirm === 'replace' ? 'namespace.invitation.replace.cancel' : undefined} open={confirm !== null} onOpenChange={(open) => { if (!open) setConfirm(null) }}
        title={t(confirm === 'revoke' ? 'invitation.revokeTitle' : 'invitation.replaceTitle')}
        description={t(confirm === 'revoke' ? 'invitation.revokeDescription' : 'invitation.replaceDescription')}
        confirmText={t('dialog.confirm')}
        onConfirm={async () => {
          try {
            if (confirm === 'revoke') await revoke.mutateAsync()
            else await create.mutateAsync(true)
          } catch {
            setConfirm(null)
          }
        }} />
    </div>
  )
}
