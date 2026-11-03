import { useEffect, useRef, useState } from 'react'
import { Check, Loader2, Trash2, X } from 'lucide-react'
import { useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { AddNamespaceMemberDialog } from '@/features/namespace/add-namespace-member-dialog'
import { BatchImportMembersDialog } from '@/features/namespace/batch-import-members-dialog'
import { InvitationControls } from '@/features/namespace/invitation-controls'
import { InvitationHistory } from '@/features/namespace/invitation-history'
import { NamespaceManagementBack, NamespaceManagementNav } from '@/features/namespace/namespace-management-nav'
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'
import { NamespaceHeader } from '@/features/namespace/namespace-header'
import { TransferOwnershipDialog } from '@/features/namespace/transfer-ownership-dialog'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Pagination } from '@/shared/components/pagination'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'
import {
  useMyNamespaces,
  useNamespaceDetail,
  useNamespaceMembers,
  useRemoveNamespaceMember,
  useUpdateNamespaceMemberRole,
} from '@/shared/hooks/use-namespace-queries'
import { toast } from '@/shared/lib/toast'

type PendingRemoval = {
  userId: string
}

const MEMBER_PAGE_SIZE = 20

/**
 * Member management page for a namespace. The route computes mutability from
 * both namespace state and the current user's role because the backend model
 * allows namespaces to become read-only for several independent reasons.
 */
export function NamespaceMembersPage() {
  const { t, i18n } = useTranslation()
  const params = useParams({ from: '/dashboard/namespaces/$slug/members' })
  const slug = params.slug
  const [page, setPage] = useState(0)
  const [draftRoles, setDraftRoles] = useState<Record<string, string>>({})
  const [pendingRemoval, setPendingRemoval] = useState<PendingRemoval | null>(null)
  const [savingRoleUserId, setSavingRoleUserId] = useState<string | null>(null)
  const [removingUserId, setRemovingUserId] = useState<string | null>(null)
  const roleSelectRefs = useRef<Record<string, HTMLButtonElement | null>>({})
  const roleToFocus = useRef<string | null>(null)

  useEffect(() => {
    if (savingRoleUserId === null && roleToFocus.current) {
      roleSelectRefs.current[roleToFocus.current]?.focus()
      roleToFocus.current = null
    }
  }, [savingRoleUserId])


  const { data: namespace, isLoading: isLoadingNamespace } = useNamespaceDetail(slug)
  const { data: membersPage, isLoading: isLoadingMembers, error: membersError } = useNamespaceMembers(slug, page, MEMBER_PAGE_SIZE)
  const { data: myNamespaces } = useMyNamespaces()
  const updateRoleMutation = useUpdateNamespaceMemberRole()
  const removeMemberMutation = useRemoveNamespaceMember()

  const members = membersPage?.items ?? []
  const totalMembers = membersPage?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(totalMembers / MEMBER_PAGE_SIZE))

  const currentNamespace = myNamespaces?.find((item) => item.slug === slug)
  const currentUserRole = currentNamespace?.currentUserRole
  const isReadOnly = namespace?.type === 'GLOBAL' || namespace?.status !== 'ACTIVE'
  // Membership changes are only allowed in active team namespaces and only for
  // elevated roles surfaced through the current user's namespace membership.
  const canManageMembers = !isReadOnly && (currentUserRole === 'OWNER' || currentUserRole === 'ADMIN')
  const canViewInvitations = namespace?.type === 'TEAM' && (currentUserRole === 'OWNER' || currentUserRole === 'ADMIN')
  const canEditNamespace = canManageMembers
  const canTransferOwnership = !isReadOnly && currentUserRole === 'OWNER'

  const readOnlyMessage = namespace?.type === 'GLOBAL'
    ? t('members.globalReadOnly')
    : namespace?.status === 'FROZEN'
      ? t('members.frozenReadOnly')
      : namespace?.status === 'ARCHIVED'
        ? t('members.archivedReadOnly')
        : currentUserRole === 'MEMBER'
          ? t('members.memberReadOnly')
          : null

  const resolveDraftRole = (userId: string, currentRole: string) => draftRoles[userId] ?? currentRole

  const handleRoleSave = async (userId: string, currentRole: string) => {
    const nextRole = resolveDraftRole(userId, currentRole)
    if (nextRole === currentRole) {
      return
    }

    setSavingRoleUserId(userId)
    try {
      await updateRoleMutation.mutateAsync({
        slug,
        userId,
        role: nextRole,
      })
      toast.success(
        t('members.updateRoleSuccessTitle'),
        t('members.updateRoleSuccessDescription', { userId, role: nextRole }),
      )
      roleToFocus.current = userId
      setDraftRoles((current) => {
        const next = { ...current }
        delete next[userId]
        return next
      })
    } catch (error) {
      toast.error(t('members.updateRoleErrorTitle'), error instanceof Error ? error.message : '')
    } finally {
      setSavingRoleUserId(null)
    }
  }

  const handleRemoveMember = async () => {
    if (!pendingRemoval) {
      return
    }

    setRemovingUserId(pendingRemoval.userId)
    try {
      await removeMemberMutation.mutateAsync({
        slug,
        userId: pendingRemoval.userId,
      })
      toast.success(
        t('members.removeSuccessTitle'),
        t('members.removeSuccessDescription', { userId: pendingRemoval.userId }),
      )
      setPendingRemoval(null)
    } catch (error) {
      toast.error(t('members.removeErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    } finally {
      setRemovingUserId(null)
    }
  }

  if (isLoadingNamespace) {
    return (
      <div className="space-y-6 animate-fade-up">
        <div className="h-12 w-48 animate-shimmer rounded-lg" />
        <div className="h-6 w-96 animate-shimmer rounded-md" />
      </div>
    )
  }

  if (!namespace) {
    return (
      <div className="text-center py-20 animate-fade-up">
        <h2 className="text-2xl font-bold font-heading mb-2">{t('members.namespaceNotFound')}</h2>
      </div>
    )
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <NamespaceManagementBack />
      <NamespaceHeader namespace={namespace} canEdit={canEditNamespace} />
      <NamespaceManagementNav slug={slug} active="members" />

      <div className="space-y-6">
        {readOnlyMessage ? (
          <Card className="border-border/50 bg-secondary/40 p-4 text-sm text-muted-foreground">
            {readOnlyMessage}
          </Card>
        ) : null}

        {canViewInvitations ? <div className="flex flex-wrap items-center justify-end gap-2">
          {canViewInvitations ? (
            <Dialog>
              <DialogTrigger asChild><Button data-analytics-id="namespace.invitation.open" variant="outline">{t(canManageMembers ? 'namespaceManagement.invite' : 'invitation.historyTitle')}</Button></DialogTrigger>
              <DialogContent closeAnalyticsId="namespace.invitation.close" className="w-[min(calc(100vw-2rem),40rem)] gap-5 p-5 sm:p-6">
                <DialogTitle>{t(canManageMembers ? 'namespaceManagement.invite' : 'invitation.historyTitle')}</DialogTitle>
                <DialogDescription className="break-words">{namespace.displayName} <span className="text-muted-foreground">@{slug}</span></DialogDescription>
                {canManageMembers ? <InvitationControls slug={slug} /> : <InvitationHistory key={slug} slug={slug} />}
              </DialogContent>
            </Dialog>
          ) : null}
          {canTransferOwnership ? (
            <TransferOwnershipDialog namespace={namespace} members={members}>
              <Button data-analytics-id="namespace.ownership.open" variant="outline">{t('members.transferOwnership')}</Button>
            </TransferOwnershipDialog>
          ) : null}
          {canManageMembers ? (
            <>
              <BatchImportMembersDialog slug={slug}>
                <Button data-analytics-id="namespace.import.open" variant="outline">{t('members.batchImport')}</Button>
              </BatchImportMembersDialog>
              <AddNamespaceMemberDialog slug={slug}>
                <Button data-analytics-id="namespace.member.open">{t('members.addMember')}</Button>
              </AddNamespaceMemberDialog>
            </>
          ) : null}
        </div> : null}

        {membersError ? (
          <Card className="p-6 text-center text-red-600">
            {membersError.message}
          </Card>
        ) : isLoadingMembers ? (
          <div className="space-y-3">
            {Array.from({ length: 3 }).map((_, index) => (
              <div key={index} className="h-14 animate-shimmer rounded-lg" />
            ))}
          </div>
        ) : members.length > 0 ? (
          <Card className="overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-border/40">
                    <th className="text-left p-4 font-medium font-heading text-sm text-muted-foreground">{t('members.colUsername')}</th>
                    <th className="text-left p-4 font-medium font-heading text-sm text-muted-foreground">{t('members.colEmail')}</th>
                    <th className="text-left p-4 font-medium font-heading text-sm text-muted-foreground">{t('members.colRole')}</th>
                    <th className="text-left p-4 font-medium font-heading text-sm text-muted-foreground">{t('members.colJoinedAt')}</th>
                    {canManageMembers ? <th className="text-right p-4 font-medium font-heading text-sm text-muted-foreground">{t('members.colActions')}</th> : null}
                  </tr>
                </thead>
                <tbody>
                  {members.map((member) => {
                    const roleValue = resolveDraftRole(member.userId, member.role)
                    const isOwner = member.role === 'OWNER'
                    const isSavingRole = savingRoleUserId === member.userId
                    const isRemoving = removingUserId === member.userId

                    return (
                      <tr key={member.id} className="border-b border-border/40 last:border-b-0 hover:bg-secondary/30 transition-colors">
                        <td className="p-4">
                          <div className="flex flex-col">
                            <span className="font-medium">{member.displayName || member.userId}</span>
                            <span className="text-xs text-muted-foreground">{member.userId}</span>
                          </div>
                        </td>
                        <td className="p-4 text-sm text-muted-foreground">{member.email || '-'}</td>
                        <td className="p-4">
                          {canManageMembers && !isOwner ? (
                            <div className="flex items-center gap-2">
                              <Select
                                value={roleValue}
                                disabled={isSavingRole || isRemoving}
                                onValueChange={(value) => {
                                  setDraftRoles((current) => ({
                                    ...current,
                                    [member.userId]: value,
                                  }))
                                }}
                              >
                                <SelectTrigger data-analytics-id="namespace.member.role.draft.interact" ref={(element) => { roleSelectRefs.current[member.userId] = element }} className="w-36" aria-label={t('members.colRole')}>
                                  <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                  <SelectItem data-analytics-id="namespace.member.role.draft.member" value="MEMBER">{t('members.roleMember')}</SelectItem>
                                  <SelectItem data-analytics-id="namespace.member.role.draft.admin" value="ADMIN">{t('members.roleAdmin')}</SelectItem>
                                </SelectContent>
                              </Select>
                            </div>
                          ) : (
                            <span className="inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium bg-secondary text-foreground border border-border/60">
                              {member.role === 'OWNER'
                                ? t('members.roleOwner')
                                : member.role === 'ADMIN'
                                  ? t('members.roleAdmin')
                                  : t('members.roleMember')}
                            </span>
                          )}
                        </td>
                        <td className="p-4 text-sm whitespace-nowrap text-muted-foreground">
                          {formatLocalDateTime(member.createdAt, i18n.language, { dateStyle: 'medium' })}
                        </td>
                        {canManageMembers ? (
                          <td className="p-4 text-right">
                            {!isOwner ? (
                              <div className="flex items-center justify-end gap-1">
                                {roleValue !== member.role ? (
                                  <>
                                    <Button data-analytics-id="namespace.member.role.save" type="button" variant="ghost" size="icon"
                                      aria-label={t('members.saveRole')} title={t('members.saveRole')}
                                      disabled={isSavingRole || isRemoving}
                                      onClick={() => handleRoleSave(member.userId, member.role)}>
                                      {isSavingRole ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Check className="h-4 w-4" aria-hidden="true" />}
                                    </Button>
                                    <Button data-analytics-id="namespace.member.role.cancel" type="button" variant="ghost" size="icon"
                                      aria-label={t('members.cancelRole')} title={t('members.cancelRole')}
                                      disabled={isSavingRole || isRemoving}
                                      onClick={() => {
                                        setDraftRoles((current) => { const next = { ...current }; delete next[member.userId]; return next })
                                        roleSelectRefs.current[member.userId]?.focus()
                                      }}>
                                      <X className="h-4 w-4" aria-hidden="true" />
                                    </Button>
                                  </>
                                ) : null}
                                <Button data-analytics-id="namespace.member.remove" type="button" variant="ghost" size="icon"
                                  className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                                  aria-label={t('members.remove')} title={t('members.remove')}
                                  disabled={isRemoving || isSavingRole}
                                  onClick={() => setPendingRemoval({ userId: member.userId })}>
                                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                                </Button>
                              </div>
                            ) : null}
                          </td>
                        ) : null}
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </Card>
        ) : (
          <Card className="p-6 text-center text-muted-foreground">
            {t('members.empty')}
          </Card>
        )}

        {totalMembers > MEMBER_PAGE_SIZE ? (
          <Pagination analyticsId="namespace.member.paginate" page={page} totalPages={totalPages} onPageChange={setPage} />
        ) : null}
      </div>

      <ConfirmDialog confirmAnalyticsId="namespace.member.remove.confirm" cancelAnalyticsId="namespace.member.remove.cancel"
        open={!!pendingRemoval}
        onOpenChange={(open) => {
          if (!open) {
            setPendingRemoval(null)
          }
        }}
        title={t('members.removeConfirmTitle')}
        description={pendingRemoval ? t('members.removeConfirmDescription', { userId: pendingRemoval.userId }) : ''}
        confirmText={t('members.remove')}
        variant="destructive"
        onConfirm={handleRemoveMember}
      />
    </div>
  )
}
