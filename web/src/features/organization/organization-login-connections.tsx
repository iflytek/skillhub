import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, RefreshCw } from 'lucide-react'
import { useId, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { organizationConnectionApi, type OrganizationLoginConnection } from '@/api/client'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Pagination } from '@/shared/components/pagination'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'

const PAGE_SIZE = 20

type Draft = NonNullable<OrganizationLoginConnection['latestRevision']>
type ConnectionEditor = { kind: 'create' } | { kind: 'revise' | 'secret'; connectionId: string; draft: Draft }
type LifecycleAction = {
  connectionId: string
  name: string
} & ({ kind: 'activate' | 'resume'; revisionId: string } | { kind: 'suspend' | 'disable' })
type Scope = {
  viewerId: string
  organizationId: string
  canManage: boolean
  canWriteSecret: boolean
}

export function OrganizationLoginConnections({ viewerId, organizationId, canManage, canWriteSecret }: Scope) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<string>()
  const [editor, setEditor] = useState<ConnectionEditor | null>(null)
  const [confirmation, setConfirmation] = useState<LifecycleAction | null>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  const dialogTrigger = useRef<HTMLButtonElement | null>(null)
  const queryKey = ['organizations', viewerId, 'detail', organizationId, 'login-connections'] as const
  const list = useQuery({
    queryKey: [...queryKey, 'list', page],
    queryFn: () => organizationConnectionApi.list(organizationId, page, PAGE_SIZE),
    refetchOnWindowFocus: true,
  })
  const management = useQuery({
    queryKey: [...queryKey, 'management'],
    queryFn: () => organizationConnectionApi.management(organizationId),
    enabled: canManage || canWriteSecret,
    retry: false,
    refetchOnWindowFocus: true,
  })
  const managementEnabled = !management.error && management.data?.enabled === true
  const canOpen = managementEnabled && (canManage || canWriteSecret)
  const detail = useQuery({
    queryKey: [...queryKey, 'connection', selected],
    queryFn: () => selected ? organizationConnectionApi.get(organizationId, selected) : Promise.resolve(undefined),
    enabled: canOpen && !!selected,
    staleTime: 0,
    refetchOnWindowFocus: true,
  })
  const connection = detail.error ? undefined : detail.data
  const draft = connection?.latestRevision
  const active = connection?.activeRevision ?? (draft?.id && draft.id === connection?.activeRevisionId ? draft : undefined)
  const activeIsLatest = !!active?.id && active.id === draft?.id
  const failedProbe = connection?.health?.status === 'FAILED'
  const passedProbe = !!draft?.id && connection?.lastTestedRevisionId === draft.id && !failedProbe
  const visibleEditor = !canOpen || !editor ? null
    : editor.kind === 'secret' ? (canWriteSecret ? editor : null)
      : (canManage ? editor : null)
  const staleEditor = visibleEditor && visibleEditor.kind !== 'create'
    && (selected !== visibleEditor.connectionId || draft?.id !== visibleEditor.draft.id)
  const visibleConfirmation = canOpen && canManage ? confirmation : null
  const refresh = () => queryClient.invalidateQueries({ queryKey })

  const probe = useMutation({
    mutationFn: ({ connectionId, revisionId }: { connectionId: string; revisionId: string }) =>
      organizationConnectionApi.test(organizationId, connectionId, revisionId),
    onSuccess: async (result) => {
      await refresh()
      if (result.success) toast.success(t('organizationConnections.probePassed'))
      else toast.error(t('organizationConnections.probeFailed'))
    },
    onError: async () => {
      await refresh()
      toast.error(t('organizationConnections.probeFailed'))
    },
  })
  const lifecycle = useMutation({
    mutationFn: (action: LifecycleAction) => {
      if (action.kind === 'activate') {
        return organizationConnectionApi.activate(organizationId, action.connectionId, action.revisionId)
      }
      if (action.kind === 'resume') {
        return organizationConnectionApi.resume(organizationId, action.connectionId, action.revisionId)
      }
      return organizationConnectionApi.changeStatus(organizationId, action.connectionId, action.kind)
    },
    onSuccess: async () => {
      await refresh()
      toast.success(t('organizationConnections.saved'))
    },
    onError: async (error) => {
      await refresh()
      toast.error(t('organizations.failed'), error.message)
    },
  })
  const openEditor = (next: ConnectionEditor, trigger: HTMLButtonElement) => {
    dialogTrigger.current = trigger
    setEditor(next)
  }
  const restoreFocus = (event: Event) => {
    event.preventDefault()
    if (dialogTrigger.current?.isConnected && !dialogTrigger.current.disabled) dialogTrigger.current.focus()
    else heading.current?.focus()
  }
  const openConfirmation = (action: LifecycleAction, trigger: HTMLButtonElement) => {
    dialogTrigger.current = trigger
    setConfirmation(action)
  }
  const items = list.error ? [] : list.data?.items ?? []
  const totalPages = Math.ceil((list.data?.total ?? 0) / PAGE_SIZE)
  const connectionName = connection?.displayName ?? ''
  const activeId = active?.id
  const draftId = draft?.id
  const latestProbe = { failed: failedProbe, passed: passedProbe, failureReason: connection?.health?.failureReason }
  const latestControls = selected && draft && draftId && connection?.status !== 'DISABLED' ? (
    <>
      {canManage ? <Button size="sm" variant="outline" aria-haspopup="dialog" disabled={probe.isPending}
        onClick={(event) => openEditor({ kind: 'revise', connectionId: selected, draft }, event.currentTarget)}>
        {t('organizationConnections.edit')}
      </Button> : null}
      {canWriteSecret ? <Button size="sm" variant="outline" aria-haspopup="dialog" disabled={probe.isPending}
        onClick={(event) => openEditor({ kind: 'secret', connectionId: selected, draft }, event.currentTarget)}>
        {t('organizationConnections.writeSecret')}
      </Button> : null}
      {canManage ? (
        <>
          <Button size="sm" variant="outline" disabled={!draft.secretConfigured || probe.isPending}
            onClick={() => probe.mutate({ connectionId: selected, revisionId: draftId })}>
            {t(probe.isPending ? 'dialog.processing' : 'organizationConnections.test')}
          </Button>
          {passedProbe && !activeIsLatest ? (
            <Button size="sm" aria-haspopup="dialog" disabled={probe.isPending}
              onClick={(event) => openConfirmation({ kind: 'activate', connectionId: selected, name: connectionName, revisionId: draftId }, event.currentTarget)}>
              {t('organizationConnections.action.activate')}
            </Button>
          ) : null}
        </>
      ) : null}
    </>
  ) : null
  const suspend = canManage && selected && activeId && connection?.status === 'ACTIVE' ? (
    <Button size="sm" variant="outline" aria-haspopup="dialog" disabled={probe.isPending}
      onClick={(event) => openConfirmation({ kind: 'suspend', connectionId: selected, name: connectionName }, event.currentTarget)}>
      {t('organizationConnections.action.suspend')}
    </Button>
  ) : null
  const resume = canManage && selected && activeId && connection?.status === 'SUSPENDED' ? (
    <Button size="sm" aria-haspopup="dialog" disabled={probe.isPending}
      onClick={(event) => openConfirmation({ kind: 'resume', connectionId: selected, name: connectionName, revisionId: activeId }, event.currentTarget)}>
      {t('organizationConnections.action.resume')}
    </Button>
  ) : null
  const activeControls = suspend ?? resume

  return (
    <Card className="divide-y divide-border/60">
      <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3.5 sm:px-5">
        <h2 ref={heading} tabIndex={-1} className="text-base font-semibold">{t('organizationConnections.title')}</h2>
        {canOpen && canManage ? (
          <Button aria-haspopup="dialog" onClick={(event) => openEditor({ kind: 'create' }, event.currentTarget)}>
            <Plus className="size-4" aria-hidden="true" />
            {t('organizationConnections.create')}
          </Button>
        ) : null}
      </div>
      {management.error && (canManage || canWriteSecret) ? (
        <div className="flex flex-wrap items-center gap-3 px-4 py-3 sm:px-5">
          <p role="alert" className="text-sm text-destructive">{t('organizationConnections.managementUnavailable')}</p>
          <Button size="sm" variant="outline" onClick={() => void management.refetch()}>{t('organizations.retry')}</Button>
        </div>
      ) : null}
      {list.isPending ? <p role="status" className="px-4 py-4 text-sm text-muted-foreground sm:px-5">{t('organizations.loading')}</p> : null}
      {list.error ? (
        <div className="flex flex-wrap items-center gap-3 px-4 py-4 sm:px-5">
          <p role="alert" className="break-words text-sm text-destructive">{list.error.message}</p>
          <Button size="sm" variant="outline" onClick={() => void list.refetch()}>{t('organizations.retry')}</Button>
        </div>
      ) : null}
      {!list.isPending && !list.error && !items.length ? (
        <p className="px-4 py-4 text-sm text-muted-foreground sm:px-5">{t('organizationConnections.empty')}</p>
      ) : null}
      {items.length ? (
        <ul className="divide-y divide-border/60">
          {items.map((item) => (
            <li key={item.id} className="space-y-4 px-4 py-3.5 sm:px-5">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="min-w-0 space-y-1">
                  <h3 className="break-words text-sm font-medium">{item.displayName || t('organizationConnections.unnamed')}</h3>
                  {item.status ? <p className="text-xs text-muted-foreground">{t(`organizationConnections.status.${item.status}`, { defaultValue: item.status })}</p> : null}
                </div>
                {canOpen && item.id ? (
                  <Button size="sm" variant="outline" aria-expanded={item.id === selected}
                    onClick={() => { probe.reset(); setSelected(item.id === selected ? undefined : item.id) }}>
                    {t(item.id === selected ? 'organizationConnections.hideDetails' : 'organizationConnections.details')}
                  </Button>
                ) : null}
              </div>
              {canOpen && !!selected && item.id === selected ? (
                <div className="space-y-4 rounded-lg border border-border/70 bg-muted/30 p-4">
                  {detail.isPending ? <p role="status" className="text-sm text-muted-foreground">{t('organizations.loading')}</p> : null}
                  {detail.error ? (
                    <div className="flex flex-wrap items-center gap-3">
                      <p role="alert" className="break-words text-sm text-destructive">{detail.error.message}</p>
                      <Button size="sm" variant="outline" onClick={() => void detail.refetch()}>{t('organizations.retry')}</Button>
                    </div>
                  ) : null}
                  {connection && (draft || active) ? (
                    <>
                      <div className="flex justify-end">
                        <Button size="sm" variant="ghost" disabled={detail.isFetching || probe.isPending}
                          onClick={() => void detail.refetch()}>
                          <RefreshCw className="size-3.5" aria-hidden="true" />
                          {t('organizationConnections.refresh')}
                        </Button>
                      </div>
                      <div className={active && draft && !activeIsLatest ? 'grid gap-3 xl:grid-cols-2' : undefined}>
                        {active ? (
                          <ConnectionRevisionDetails
                            heading={t(connection.status === 'ACTIVE' ? 'organizationConnections.activeRevision' : 'organizationConnections.previousRevision', { revision: active.revision })}
                            revision={active}
                            probe={activeIsLatest ? latestProbe : undefined}
                            controls={activeIsLatest && latestControls ? <>{latestControls}{activeControls}</> : activeControls}
                          />
                        ) : null}
                        {draft && !activeIsLatest ? (
                          <ConnectionRevisionDetails
                            heading={t('organizationConnections.draftRevision', { revision: draft.revision })}
                            revision={draft}
                            probe={latestProbe}
                            controls={latestControls}
                          />
                        ) : null}
                      </div>
                      {canManage && connection.status !== 'DISABLED' ? (
                        <div className="flex flex-wrap gap-2 border-t border-border/60 pt-3">
                          <Button size="sm" variant="ghost" className="text-destructive hover:text-destructive" aria-haspopup="dialog" disabled={probe.isPending}
                            onClick={(event) => openConfirmation({ kind: 'disable', connectionId: selected, name: connectionName }, event.currentTarget)}>
                            {t('organizationConnections.action.disable')}
                          </Button>
                        </div>
                      ) : null}
                    </>
                  ) : null}
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      ) : null}
      {totalPages > 1 ? (
        <div className="px-4 py-3 sm:px-5">
          <Pagination page={page} totalPages={totalPages} onPageChange={(next) => { setSelected(undefined); setPage(next) }} />
        </div>
      ) : null}
      {visibleEditor ? (
        <ConnectionEditorDialog
          organizationId={organizationId}
          editor={visibleEditor}
          stale={!!staleEditor}
          onClose={() => setEditor(null)}
          restoreFocus={restoreFocus}
          onSaved={async () => {
            if (visibleEditor.kind === 'create') setPage(0)
            await refresh()
            toast.success(t('organizationConnections.saved'))
            setEditor(null)
          }}
          onFailed={refresh}
        />
      ) : null}
      {visibleConfirmation ? (
        <ConfirmDialog
          open
          onOpenChange={(open) => { if (!open) setConfirmation(null) }}
          title={t(`organizationConnections.confirm.${visibleConfirmation.kind}`)}
          description={visibleConfirmation.kind === 'disable'
            ? t('organizationConnections.disableImpact', { name: visibleConfirmation.name })
            : visibleConfirmation.kind === 'resume'
              ? t('organizationConnections.resumeImpact', { name: visibleConfirmation.name })
              : visibleConfirmation.name}
          confirmText={t(`organizationConnections.action.${visibleConfirmation.kind}`)}
          variant={visibleConfirmation.kind === 'disable' ? 'destructive' : 'default'}
          onCloseAutoFocus={restoreFocus}
          onConfirm={async () => {
            try {
              await lifecycle.mutateAsync(visibleConfirmation)
            } catch { return false }
          }}
        />
      ) : null}
    </Card>
  )
}

function probeFailureHint(reason: string | null | undefined) {
  switch (reason) {
    case 'BLOCKED': return 'organizationConnections.probeFailure.blocked'
    case 'TIMEOUT': return 'organizationConnections.probeFailure.timeout'
    case 'UPSTREAM_UNAVAILABLE': return 'organizationConnections.probeFailure.unavailable'
    case 'INVALID_METADATA': return 'organizationConnections.probeFailure.invalidMetadata'
    default: return undefined
  }
}

function ConnectionRevisionDetails({ heading, revision, probe, controls }: {
  heading: string
  revision: Draft
  probe?: { failed: boolean; passed: boolean; failureReason?: string | null }
  controls: ReactNode
}) {
  const { t } = useTranslation()
  const headingId = useId()
  const failureHint = probe?.failed ? probeFailureHint(probe.failureReason) : undefined
  return (
    <section aria-labelledby={headingId} className="min-w-0 space-y-3 rounded-md border border-border/60 bg-background p-3">
      <h4 id={headingId} className="text-sm font-semibold">{heading}</h4>
      <dl className="grid gap-y-2 text-sm sm:grid-cols-[max-content_minmax(0,1fr)] sm:gap-x-4">
        <dt className="text-muted-foreground">{t('organizationConnections.issuer')}</dt>
        <dd className="break-all">{revision.issuer}</dd>
        <dt className="text-muted-foreground">{t('organizationConnections.clientId')}</dt>
        <dd className="break-all">{revision.clientId}</dd>
        <dt className="text-muted-foreground">{t('organizationConnections.secret')}</dt>
        <dd>{t(revision.secretConfigured ? 'organizationConnections.secretReady' : 'organizationConnections.secretMissing')}</dd>
        {probe ? (
          <>
            <dt className="text-muted-foreground">{t('organizationConnections.test')}</dt>
            <dd className={probe.failed ? 'text-destructive' : undefined}>
              {t(probe.failed ? 'organizationConnections.probeFailed'
                : probe.passed ? 'organizationConnections.probePassed' : 'organizationConnections.probeRequired')}
              {failureHint ? <p className="mt-1 text-xs text-muted-foreground">{t(failureHint)}</p> : null}
            </dd>
          </>
        ) : null}
      </dl>
      {controls ? <div className="flex flex-wrap items-center gap-2">{controls}</div> : null}
    </section>
  )
}

function ConnectionEditorDialog({ organizationId, editor, stale, onClose, restoreFocus, onSaved, onFailed }: {
  organizationId: string
  editor: ConnectionEditor
  stale: boolean
  onClose: () => void
  restoreFocus: (event: Event) => void
  onSaved: () => Promise<void>
  onFailed: () => Promise<void>
}) {
  const { t } = useTranslation()
  const [displayName, setDisplayName] = useState('')
  const [issuer, setIssuer] = useState(editor.kind === 'create' ? '' : editor.draft.issuer ?? '')
  const [clientId, setClientId] = useState(editor.kind === 'create' ? '' : editor.draft.clientId ?? '')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const pendingRef = useRef(false)
  const secretInput = useRef<HTMLInputElement>(null)
  const title = t(editor.kind === 'create' ? 'organizationConnections.create'
    : editor.kind === 'revise' ? 'organizationConnections.edit' : 'organizationConnections.writeSecret')
  const blocked = stale || pending

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (stale || pendingRef.current) return
    pendingRef.current = true
    setPending(true)
    setError('')
    try {
      if (editor.kind === 'create') {
        await organizationConnectionApi.create(organizationId, { displayName: displayName.trim(), issuer: issuer.trim(), clientId: clientId.trim() })
      } else if (editor.draft.id) {
        if (editor.kind === 'revise') {
          await organizationConnectionApi.revise(organizationId, editor.connectionId, {
            expectedRevisionId: editor.draft.id, issuer: issuer.trim(), clientId: clientId.trim(),
          })
        } else if (secretInput.current?.value) {
          const clientSecret = secretInput.current.value
          secretInput.current.value = ''
          await organizationConnectionApi.writeSecret(organizationId, editor.connectionId, editor.draft.id, { clientSecret })
        } else {
          return
        }
      } else {
        return
      }
      await onSaved()
    } catch (cause) {
      // Credential submissions never surface a raw error that could contain the input.
      setError(editor.kind !== 'secret' && cause instanceof Error ? cause.message : t('organizationConnections.secretError'))
      await onFailed()
    } finally {
      pendingRef.current = false
      setPending(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !pendingRef.current) onClose() }}>
      <DialogContent hideClose={pending} onCloseAutoFocus={restoreFocus}>
        <DialogHeader className="text-left">
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{t(editor.kind === 'secret'
            ? 'organizationConnections.secretHint' : 'organizationConnections.draftHint')}</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={(event) => void submit(event)}>
          {editor.kind === 'secret' ? (
            <>
              <dl className="grid gap-x-3 gap-y-2 rounded-lg bg-muted/60 p-3 text-sm sm:grid-cols-[max-content_minmax(0,1fr)]">
                <dt className="text-muted-foreground">{t('organizationConnections.issuer')}</dt>
                <dd className="break-all">{editor.draft.issuer}</dd>
                <dt className="text-muted-foreground">{t('organizationConnections.clientId')}</dt>
                <dd className="break-all">{editor.draft.clientId}</dd>
              </dl>
              <div className="space-y-2">
                <Label htmlFor="oidc-client-secret">{t('organizationConnections.secret')}</Label>
                <Input ref={secretInput} id="oidc-client-secret" type="password" autoComplete="new-password" required
                  maxLength={4096} disabled={blocked} aria-invalid={!!error} aria-describedby={error ? 'oidc-editor-error' : undefined} />
              </div>
            </>
          ) : (
            <>
              {editor.kind === 'create' ? <div className="space-y-2">
                <Label htmlFor="oidc-display-name">{t('organizationConnections.name')}</Label>
                <Input id="oidc-display-name" value={displayName} onChange={(event) => setDisplayName(event.target.value)}
                  maxLength={128} disabled={blocked} required />
              </div> : null}
              <div className="space-y-2">
                <Label htmlFor="oidc-issuer">{t('organizationConnections.issuer')}</Label>
                <Input id="oidc-issuer" type="url" value={issuer} onChange={(event) => setIssuer(event.target.value)}
                  maxLength={512} disabled={blocked} placeholder="https://" required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="oidc-client-id">{t('organizationConnections.clientId')}</Label>
                <Input id="oidc-client-id" value={clientId} onChange={(event) => setClientId(event.target.value)}
                  maxLength={256} disabled={blocked} autoComplete="off" required />
              </div>
            </>
          )}
          {stale || error ? <p id="oidc-editor-error" role="alert" className="break-words text-sm text-destructive">
            {stale ? t('organizationConnections.staleDraft') : error}
          </p> : null}
          <DialogFooter className="flex-row justify-end">
            <Button type="button" variant="outline" disabled={pending} onClick={onClose}>{t('dialog.cancel')}</Button>
            <Button type="submit" disabled={blocked || (editor.kind !== 'secret' && (!issuer.trim() || !clientId.trim()
              || (editor.kind === 'create' && !displayName.trim())))}>
              {t(pending ? 'dialog.processing' : editor.kind === 'secret' ? 'organizationConnections.writeSecret' : 'organizationConnections.saveDraft')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
