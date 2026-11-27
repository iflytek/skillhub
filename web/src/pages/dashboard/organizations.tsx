import { Link, useParams, useRouteContext } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Building2, ChevronRight, UserPlus, X } from 'lucide-react'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { organizationApi, type OrganizationRole } from '@/api/client'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'
import { useAuth } from '@/features/auth/use-auth'
import { OrganizationLoginConnections } from '@/features/organization/organization-login-connections'
import { OrganizationDirectoryConnections } from '@/features/organization/organization-directory-connections'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

const PAGE_SIZE = 20
const DELEGABLE_ROLES: OrganizationRole[] = [
  'ORG_OWNER', 'IDENTITY_ADMIN', 'LOGIN_SECRET_ADMIN', 'MEMBER_ADMIN', 'ORG_AUDITOR',
]

type MemberEditor = { kind: 'add' } | {
  kind: 'grant'
  userId: string
  name: string
  availableRoles: OrganizationRole[]
}

function useOrganizationViewerId(routeUserId: string | undefined) {
  const { user, error } = useAuth()
  return !error && user?.userId === routeUserId ? routeUserId : undefined
}

export function OrganizationsPage() {
  const { t } = useTranslation()
  const { user } = useRouteContext({ from: '/dashboard/organizations' })
  const viewerId = useOrganizationViewerId(user?.userId)
  const [page, setPage] = useState(0)
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ['organizations', viewerId, 'list', page],
    queryFn: () => organizationApi.list(page, PAGE_SIZE),
    enabled: !!viewerId,
    refetchOnWindowFocus: true,
  })
  const organizations = error ? undefined : data

  return (
    <div className={`${APP_SHELL_PAGE_CLASS_NAME} space-y-6`}>
      <DashboardPageHeader title={t('organizations.title')} />
      {isPending ? <p role="status" className="text-sm text-muted-foreground">{t('organizations.loading')}</p> : null}
      {error ? (
        <Card className="flex flex-wrap items-center gap-3 p-4">
          <p role="alert" className="text-sm text-destructive">{error.message}</p>
          <Button size="sm" variant="outline" onClick={() => void refetch()}>{t('organizations.retry')}</Button>
        </Card>
      ) : null}
      {!isPending && !error && !organizations?.items.length ? (
        <Card className="p-5 text-sm text-muted-foreground">{t('organizations.empty')}</Card>
      ) : null}
      <div className="grid items-start gap-3 xl:grid-cols-2">
        {organizations?.items.map((organization) => {
          return (
            <Card key={organization.id} className="flex min-w-0 items-center gap-3 px-4 py-3.5">
              <span className="grid size-9 shrink-0 place-items-center rounded-lg border border-border/70 bg-muted text-muted-foreground">
                <Building2 className="size-4" aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <h2 className="break-words text-sm font-semibold">{organization.displayName || organization.slug}</h2>
                {organization.slug !== organization.displayName && organization.slug ? (
                  <p className="mt-0.5 break-words text-xs text-muted-foreground">{organization.slug}</p>
                ) : null}
              </div>
              {organization.id && (organization.permissions?.canViewMembers || organization.permissions?.canViewLoginConnections) ? (
                <Link
                  to="/dashboard/organizations/$organizationId"
                  params={{ organizationId: organization.id }}
                  aria-label={t(organization.permissions.canViewLoginConnections
                    ? 'organizationConnections.viewOrganization' : 'organizations.viewOrganizationMembers', {
                    name: organization.displayName || organization.slug,
                  })}
                  className="inline-flex min-h-8 shrink-0 items-center gap-1 rounded-md px-2 text-xs font-medium text-foreground hover:bg-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring"
                >
                  {t(organization.permissions.canViewLoginConnections ? 'organizationConnections.view' : 'organizations.viewMembers')}
                  <ChevronRight className="size-3.5 text-muted-foreground" aria-hidden="true" />
                </Link>
              ) : null}
            </Card>
          )
        })}
      </div>
      {organizations && organizations.total > PAGE_SIZE ? (
        <Pagination page={page} totalPages={Math.ceil(organizations.total / PAGE_SIZE)} onPageChange={setPage} />
      ) : null}
    </div>
  )
}

export function OrganizationMembersPage() {
  const { organizationId } = useParams({ from: '/dashboard/organizations/$organizationId' })
  const { user } = useRouteContext({ from: '/dashboard/organizations/$organizationId' })
  const viewerId = useOrganizationViewerId(user?.userId)
  if (!viewerId) return null
  return <OrganizationMembers key={JSON.stringify([viewerId, organizationId])} viewerId={viewerId} organizationId={organizationId} />
}

function OrganizationMembers({ viewerId, organizationId }: { viewerId: string; organizationId: string }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [userId, setUserId] = useState('')
  const [role, setRole] = useState<OrganizationRole | ''>('')
  const [editor, setEditor] = useState<MemberEditor | null>(null)
  const [pendingRevoke, setPendingRevoke] = useState<{ id: string; name: string; role: string } | null>(null)
  const editorTrigger = useRef<HTMLButtonElement | null>(null)
  const revokeTrigger = useRef<HTMLButtonElement | null>(null)
  const membersHeading = useRef<HTMLHeadingElement | null>(null)
  const organizationKey = ['organizations', viewerId, 'detail', organizationId] as const
  const detail = useQuery({
    queryKey: organizationKey,
    queryFn: () => organizationApi.get(organizationId),
    refetchOnWindowFocus: true,
  })
  const organization = detail.error ? undefined : detail.data
  const members = useQuery({
    queryKey: [...organizationKey, 'members', page],
    queryFn: () => organizationApi.members(organizationId, page, PAGE_SIZE),
    enabled: !!organization?.permissions?.canViewMembers,
    refetchOnWindowFocus: true,
  })
  const memberPage = members.error ? undefined : members.data
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['organizations', viewerId] })
  const add = useMutation({
    mutationFn: (targetUserId: string) => organizationApi.addMember(organizationId, targetUserId),
    onSuccess: async () => {
      setPage(0)
      await invalidate()
      toast.success(t('organizations.memberAdded'))
      setEditor(null)
    },
  })
  const grant = useMutation({
    mutationFn: ({ targetUserId, role }: { targetUserId: string; role: OrganizationRole }) =>
      organizationApi.grantRole(organizationId, targetUserId, role),
    onSuccess: async () => {
      await invalidate()
      toast.success(t('organizations.roleGranted'))
      setEditor(null)
    },
  })
  const revoke = useMutation({
    mutationFn: (bindingId: string) => organizationApi.revokeRole(organizationId, bindingId),
    onSuccess: async () => {
      await invalidate()
      toast.success(t('organizations.roleRevoked'))
      setPendingRevoke(null)
    },
    onError: (error) => toast.error(t('organizations.failed'), error.message),
  })
  const canManageMembers = !members.error && organization?.permissions?.canManageMembers === true
  const canViewRoles = organization?.permissions?.canViewRoles === true
  const canManageRoles = !members.error && organization?.roles?.includes('ORG_OWNER') === true
  const visibleEditor = editor?.kind === 'add' ? (canManageMembers ? editor : null) : (canManageRoles ? editor : null)
  const isAdding = visibleEditor?.kind === 'add'
  const editorMutation = isAdding ? add : grant
  const editorAction = isAdding ? t('organizations.addMember') : t('organizations.grantRole')
  const openEditor = (next: MemberEditor, trigger: HTMLButtonElement) => {
    editorTrigger.current = trigger
    setUserId('')
    setRole('')
    add.reset()
    grant.reset()
    setEditor(next)
  }

  const restoreFocus = (event: Event, trigger: HTMLButtonElement | null) => {
    event.preventDefault()
    if (trigger?.isConnected) trigger.focus()
    else membersHeading.current?.focus()
  }

  return (
    <div className={`${APP_SHELL_PAGE_CLASS_NAME} space-y-6`}>
      <div className="space-y-3">
        <Link to="/dashboard/organizations" className="inline-flex min-h-8 items-center gap-1.5 rounded-md text-sm text-muted-foreground hover:text-foreground focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring">
          <ArrowLeft className="size-4" aria-hidden="true" />
          {t('organizations.title')}
        </Link>
        <DashboardPageHeader
          title={organization?.displayName || organization?.slug || t('organizations.members')}
          subtitle={organization?.slug !== organization?.displayName ? organization?.slug : undefined}
          actions={canManageMembers ? (
            <Button aria-haspopup="dialog" onClick={(event) => openEditor({ kind: 'add' }, event.currentTarget)}>
              <UserPlus className="size-4" aria-hidden="true" />
              {t('organizations.addMember')}
            </Button>
          ) : undefined}
        />
      </div>
      {detail.isPending ? <p role="status" className="text-sm text-muted-foreground">{t('organizations.loading')}</p> : null}
      {detail.error ? (
        <Card className="p-4">
          <p role="alert" className="break-words text-sm text-destructive">{detail.error.message}</p>
        </Card>
      ) : null}
      {organization && !organization.permissions?.canViewMembers && !organization.permissions?.canViewLoginConnections ? (
        <Card className="p-5 text-sm text-muted-foreground">{t('organizations.noMemberAccess')}</Card>
      ) : null}
      {organization?.permissions?.canViewMembers ? (
        <Card className="divide-y divide-border/60">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3.5 sm:px-5">
            <h2 ref={membersHeading} tabIndex={-1} className="text-base font-semibold">{t('organizations.members')}</h2>
            {memberPage ? <span className="text-xs text-muted-foreground">{t('organizations.memberCount', { count: memberPage.total })}</span> : null}
          </div>
          {members.isPending ? <p role="status" className="p-5 text-sm text-muted-foreground">{t('organizations.loading')}</p> : null}
          {members.error ? (
            <div className="flex flex-wrap items-center gap-3 p-5">
              <p role="alert" className="text-sm text-destructive">{members.error.message}</p>
              <Button size="sm" variant="outline" onClick={() => void members.refetch()}>{t('organizations.retry')}</Button>
            </div>
          ) : null}
          {!members.isPending && !members.error && !memberPage?.items.length ? (
            <p className="p-5 text-sm text-muted-foreground">{t('organizations.noMembers')}</p>
          ) : null}
          {memberPage?.items.length ? (
            <ul className="divide-y divide-border/60">
              {memberPage.items.map((member) => {
                const name = member.displayName || member.userId || t('organizations.unnamedMember')
                const secondaryLabel = member.userId
                const targetUserId = member.userId
                const roles = member.roles ?? []
                const availableRoles = DELEGABLE_ROLES.filter((item) => !roles.some((binding) => binding.role === item))
                return (
                  <li key={member.id} className={`grid items-center gap-2.5 px-4 py-3 sm:px-5 lg:gap-4 ${canViewRoles ? 'lg:grid-cols-[minmax(0,1fr)_minmax(0,1.35fr)_auto]' : 'lg:grid-cols-[minmax(0,1fr)_auto]'}`}>
                    <div className="min-w-0">
                      <p className="break-words text-sm font-medium">{name}</p>
                      {secondaryLabel && secondaryLabel !== name ? (
                        <p className="mt-0.5 break-words text-xs text-muted-foreground">{secondaryLabel}</p>
                      ) : null}
                      {member.status && member.status !== 'ACTIVE' ? (
                        <p className="mt-0.5 text-xs text-muted-foreground">{t(`organizations.memberStatus.${member.status}`, { defaultValue: member.status })}</p>
                      ) : null}
                    </div>
                    {canViewRoles ? <div className="flex flex-wrap gap-1.5">
                      {roles.length ? roles.map((binding) => {
                        const roleName = t(`organizations.roles.${binding.role}`)
                        return (
                          <span key={binding.id} className="inline-flex min-h-6 max-w-full items-center gap-0.5 rounded-md border border-border/70 bg-muted/50 px-2 text-xs leading-5">
                            <span className="min-w-0 break-words">{roleName}</span>
                            {canManageRoles && binding.id ? (
                              <Button size="xs" variant="ghost" type="button"
                                className="-mr-1.5 w-6 shrink-0 px-0 text-muted-foreground hover:text-destructive"
                                aria-haspopup="dialog"
                                aria-label={t('organizations.revokeRole', { name, role: roleName })}
                                onClick={(event) => {
                                  if (!binding.id) return
                                  revokeTrigger.current = event.currentTarget
                                  setPendingRevoke({ id: binding.id, name, role: roleName })
                                }}
                              >
                                <X className="size-3" aria-hidden="true" />
                              </Button>
                            ) : null}
                          </span>
                        )
                      }) : <span className="text-xs text-muted-foreground">{t('organizations.noRoles')}</span>}
                    </div> : null}
                    {canManageRoles && targetUserId && member.status === 'ACTIVE' && availableRoles.length ? (
                      <Button size="sm" variant="outline" className="justify-self-start"
                        aria-label={t('organizations.grantMemberRole', { name })}
                        aria-haspopup="dialog"
                        onClick={(event) => openEditor({ kind: 'grant', userId: targetUserId, name, availableRoles }, event.currentTarget)}
                      >
                        {t('organizations.grantRole')}
                      </Button>
                    ) : null}
                  </li>
                )
              })}
            </ul>
          ) : null}
        </Card>
      ) : null}
      {memberPage && organization?.permissions?.canViewMembers && memberPage.total > PAGE_SIZE ? (
        <Pagination page={page} totalPages={Math.ceil(memberPage.total / PAGE_SIZE)} onPageChange={setPage} />
      ) : null}
      {organization?.permissions?.canViewLoginConnections ? (
        <OrganizationLoginConnections viewerId={viewerId} organizationId={organizationId}
          canManage={organization.permissions.canManageLoginConnections === true}
          canWriteSecret={organization.permissions.canRotateLoginSecrets === true} />
      ) : null}
      {organization?.permissions?.canViewDirectoryConnections ? (
        <OrganizationDirectoryConnections viewerId={viewerId} organizationId={organizationId}
          canManage={organization.permissions.canManageDirectoryConnections === true}
          canRotateSecret={organization.permissions.canRotateDirectorySecrets === true} />
      ) : null}
      <Dialog open={!!visibleEditor} onOpenChange={(open) => { if (!open && !editorMutation.isPending) setEditor(null) }}>
        <DialogContent hideClose={editorMutation.isPending} onCloseAutoFocus={(event) => restoreFocus(event, editorTrigger.current)}>
          {visibleEditor ? (
            <>
              <DialogHeader className="text-left">
                <DialogTitle>{editorAction}</DialogTitle>
                <DialogDescription className="break-words">
                  {visibleEditor.kind === 'add'
                    ? t('organizations.addMemberHint')
                    : t('organizations.grantDescription', { name: visibleEditor.name })}
                </DialogDescription>
              </DialogHeader>
              <form className="space-y-4" onSubmit={(event) => {
                event.preventDefault()
                if (editorMutation.isPending) return
                if (visibleEditor.kind === 'add' && userId.trim()) add.mutate(userId.trim())
                if (visibleEditor.kind === 'grant' && role) grant.mutate({ targetUserId: visibleEditor.userId, role })
              }}>
                <div className="space-y-2">
                  {visibleEditor.kind === 'add' ? (
                    <>
                      <Label htmlFor="organization-user-id">{t('organizations.userId')}</Label>
                      <Input id="organization-user-id" value={userId} onChange={(event) => setUserId(event.target.value)}
                        aria-invalid={!!add.error} aria-describedby={add.error ? 'organization-editor-error' : undefined}
                        autoComplete="off" disabled={add.isPending} required />
                      <Link to="/settings/profile" className="inline-block text-xs text-muted-foreground underline underline-offset-2 hover:text-foreground">
                        {t('organizations.profileLink')}
                      </Link>
                    </>
                  ) : (
                    <>
                      <Label htmlFor="organization-role">{t('organizations.selectRole')}</Label>
                      <Select value={role} required disabled={grant.isPending} onValueChange={(value) => setRole(value as OrganizationRole)}>
                        <SelectTrigger id="organization-role" aria-invalid={!!grant.error}
                          aria-describedby={grant.error ? 'organization-editor-error' : undefined}>
                          <SelectValue placeholder={t('organizations.selectRole')} />
                        </SelectTrigger>
                        <SelectContent className="max-w-[var(--radix-select-content-available-width)]">
                          {visibleEditor.availableRoles.map((item) => (
                            <SelectItem key={item} value={item}>{t(`organizations.roles.${item}`)}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </>
                  )}
                </div>
                {editorMutation.error ? <p id="organization-editor-error" role="alert" className="break-words text-sm text-destructive">{editorMutation.error.message}</p> : null}
                <DialogFooter className="flex-row justify-end pt-1">
                  <Button type="button" variant="outline" disabled={editorMutation.isPending} onClick={() => setEditor(null)}>
                    {t('dialog.cancel')}
                  </Button>
                  <Button type="submit" disabled={editorMutation.isPending || !(isAdding ? userId.trim() : role)}>
                    {editorMutation.isPending ? t('dialog.processing') : editorAction}
                  </Button>
                </DialogFooter>
              </form>
            </>
          ) : null}
        </DialogContent>
      </Dialog>
      <ConfirmDialog open={canManageRoles && !!pendingRevoke} onOpenChange={(open) => { if (!open) setPendingRevoke(null) }}
        title={t('organizations.revokeConfirm')} variant="destructive"
        description={pendingRevoke ? t('organizations.revokeDescription', { name: pendingRevoke.name, role: pendingRevoke.role }) : undefined}
        onCloseAutoFocus={(event) => restoreFocus(event, revokeTrigger.current)}
        onConfirm={async () => {
          if (!pendingRevoke) return
          try {
            await revoke.mutateAsync(pendingRevoke.id)
          } catch { return false }
        }} />
    </div>
  )
}
