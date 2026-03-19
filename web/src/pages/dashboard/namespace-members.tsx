import { useState } from 'react'
import { useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { AddNamespaceMemberDialog } from '@/features/namespace/add-namespace-member-dialog'
import { NamespaceHeader } from '@/features/namespace/namespace-header'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Select } from '@/shared/ui/select'
import {
  useMyNamespaces,
  useNamespaceDetail,
  useNamespaceMembers,
  useRemoveNamespaceMember,
  useUpdateNamespaceMemberRole,
} from '@/shared/hooks/use-skill-queries'
import { toast } from '@/shared/lib/toast'

type PendingRemoval = {
  userId: string
}

/**
 * Member management page for a namespace. The route computes mutability from
 * both namespace state and the current user's role because the backend model
 * allows namespaces to become read-only for several independent reasons.
 */
export function NamespaceMembersPage() {
  const { t, i18n } = useTranslation()
  const params = useParams({ from: '/dashboard/namespaces/$slug/members' })
  const slug = params.slug
  const [draftRoles, setDraftRoles] = useState<Record<string, string>>({})
  const [pendingRemoval, setPendingRemoval] = useState<PendingRemoval | null>(null)
  const [savingRoleUserId, setSavingRoleUserId] = useState<string | null>(null)
  const [removingUserId, setRemovingUserId] = useState<string | null>(null)

  const { data: namespace, isLoading: isLoadingNamespace } = useNamespaceDetail(slug)
  const { data: members, isLoading: isLoadingMembers, error: membersError } = useNamespaceMembers(slug)
  const { data: myNamespaces } = useMyNamespaces()
  const updateRoleMutation = useUpdateNamespaceMemberRole()
  const removeMemberMutation = useRemoveNamespaceMember()

  const currentNamespace = myNamespaces?.find((item) => item.slug === slug)
  const currentUserRole = currentNamespace?.currentUserRole
  const isReadOnly = namespace?.type === 'GLOBAL' || namespace?.status !== 'ACTIVE'
  // Membership changes are only allowed in active team namespaces and only for
  // elevated roles surfaced through the current user's namespace membership.
  const canManageMembers = !isReadOnly && (currentUserRole === 'OWNER' || currentUserRole === 'ADMIN')

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
      <DashboardPageHeader
        title={t('members.title')}
        subtitle={namespace ? `@${namespace.slug}` : undefined}
      />
      <NamespaceHeader namespace={namespace} />

      <div className="space-y-6">
        {readOnlyMessage ? (
          <Card className="border-border/50 bg-secondary/40 p-4 text-sm text-muted-foreground">
            {readOnlyMessage}
          </Card>
        ) : null}

        <div className="flex items-center justify-end">
          {canManageMembers ? (
            <AddNamespaceMemberDialog slug={slug}>
              <Button>{t('members.addMember')}</Button>
            </AddNamespaceMemberDialog>
          ) : (
            <Button disabled>{t('members.addMember')}</Button>
          )}
        </div>

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
        ) : members && members.length > 0 ? (
          <Card className="overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-border/40">
                    <th className="text-left p-4 font-medium font-heading text-sm text-muted-foreground">{t('members.colUserId')}</th>
                    <th className="text-left p-4 font-medium font-heading text-sm text-muted-foreground">{t('members.colRole')}</th>
                    <th className="text-left p-4 font-medium font-heading text-sm text-muted-foreground">{t('members.colJoinedAt')}</th>
                    <th className="text-right p-4 font-medium font-heading text-sm text-muted-foreground">{t('members.colActions')}</th>
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
                        <td className="p-4 font-medium font-mono">{member.userId}</td>
                        <td className="p-4">
                          {canManageMembers && !isOwner ? (
                            <div className="flex items-center gap-2">
                              <Select
                                className="w-36"
                                value={roleValue}
                                onChange={(event) => {
                                  setDraftRoles((current) => ({
                                    ...current,
                                    [member.userId]: event.target.value,
                                  }))
                                }}
                              >
                                <option value="MEMBER">{t('members.roleMember')}</option>
                                <option value="ADMIN">{t('members.roleAdmin')}</option>
                              </Select>
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                disabled={roleValue === member.role || isSavingRole}
                                onClick={() => handleRoleSave(member.userId, member.role)}
                              >
                                {isSavingRole ? t('members.savingRole') : t('members.saveRole')}
                              </Button>
                            </div>
                          ) : (
                            <span className="inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium bg-accent/10 text-accent border border-accent/20">
                              {member.role === 'OWNER'
                                ? t('members.roleOwner')
                                : member.role === 'ADMIN'
                                  ? t('members.roleAdmin')
                                  : t('members.roleMember')}
                            </span>
                          )}
                        </td>
                        <td className="p-4 text-sm text-muted-foreground">
                          {formatLocalDateTime(member.createdAt, i18n.language, { dateStyle: 'medium' })}
                        </td>
                        <td className="p-4 text-right">
                          <Button
                            type="button"
                            variant="destructive"
                            size="sm"
                            disabled={!canManageMembers || isOwner || isRemoving}
                            onClick={() => setPendingRemoval({ userId: member.userId })}
                          >
                            {t('members.remove')}
                          </Button>
                        </td>
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
      </div>

      <ConfirmDialog
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
