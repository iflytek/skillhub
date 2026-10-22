import { KeyboardEvent, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { AdminNamespace, NamespaceMember, NamespaceRole } from '@/api/types'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  normalizeSelectValue,
} from '@/shared/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Pagination } from '@/shared/components/pagination'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import {
  useAddAdminNamespaceMember,
  useAdminNamespace,
  useAdminNamespaceLifecycleAction,
  useAdminNamespaceMemberCandidates,
  useAdminNamespaceMembers,
  useAdminNamespaces,
  useRemoveAdminNamespaceMember,
  useTransferAdminNamespaceOwnership,
  useUpdateAdminNamespaceMemberRole,
} from '@/features/admin/use-admin-namespaces'

const PAGE_SIZE = 20
const MEMBER_PAGE_SIZE = 10
const ALL_FILTER_VALUE = '__all__'
const EMPTY_NAMESPACES: AdminNamespace[] = []
const EMPTY_MEMBERS: NamespaceMember[] = []

type PendingLifecycleAction = {
  slug: string
  name: string
  action: 'freeze' | 'unfreeze' | 'archive' | 'restore'
}

type PendingRemoval = {
  slug: string
  userId: string
}

type AddMemberDialogState = {
  open: boolean
  slug: string
  userId: string
  role: Exclude<NamespaceRole, 'OWNER'> | string
  search: string
}

type TransferDialogState = {
  open: boolean
  slug: string
  selectedUserId: string
  confirmSlug: string
}

function statusLabel(t: (key: string) => string, status: string) {
  if (status === 'FROZEN') return t('namespaceStatus.frozen')
  if (status === 'ARCHIVED') return t('namespaceStatus.archived')
  return t('namespaceStatus.active')
}

function statusClassName(status: string) {
  if (status === 'FROZEN') return 'bg-amber-500/10 text-amber-500 border-amber-500/20'
  if (status === 'ARCHIVED') return 'bg-slate-500/10 text-slate-500 border-slate-500/20'
  return 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20'
}

function namespaceTypeLabel(t: (key: string) => string, type: string) {
  return type === 'GLOBAL' ? t('adminNamespaces.typeGlobal') : t('adminNamespaces.typeTeam')
}

function displayRole(t: (key: string) => string, role?: string) {
  if (!role) return t('adminNamespaces.noMembership')
  if (role === 'OWNER') return t('members.roleOwner')
  if (role === 'ADMIN') return t('members.roleAdmin')
  return t('members.roleMember')
}

export function AdminNamespacesPage() {
  const { t, i18n } = useTranslation()
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  const [page, setPage] = useState(0)
  const [selectedSlug, setSelectedSlug] = useState<string | null>(null)
  const [memberPage, setMemberPage] = useState(0)
  const [draftRoles, setDraftRoles] = useState<Record<string, string>>({})
  const [pendingLifecycleAction, setPendingLifecycleAction] = useState<PendingLifecycleAction | null>(null)
  const [pendingRemoval, setPendingRemoval] = useState<PendingRemoval | null>(null)
  const [lifecycleReason, setLifecycleReason] = useState('')
  const [addMemberDialog, setAddMemberDialog] = useState<AddMemberDialogState>({
    open: false,
    slug: '',
    userId: '',
    role: 'MEMBER',
    search: '',
  })
  const [transferDialog, setTransferDialog] = useState<TransferDialogState>({
    open: false,
    slug: '',
    selectedUserId: '',
    confirmSlug: '',
  })

  const namespacesQuery = useAdminNamespaces({
    keyword,
    status: statusFilter || undefined,
    type: typeFilter || undefined,
    page,
    size: PAGE_SIZE,
  })
  const selectedNamespaceQuery = useAdminNamespace(selectedSlug ?? '')
  const membersQuery = useAdminNamespaceMembers(selectedSlug ?? '', memberPage, MEMBER_PAGE_SIZE)
  const candidatesQuery = useAdminNamespaceMemberCandidates(
    addMemberDialog.slug,
    addMemberDialog.search,
    addMemberDialog.open && addMemberDialog.search.trim().length >= 2,
  )

  const lifecycleMutation = useAdminNamespaceLifecycleAction()
  const addMemberMutation = useAddAdminNamespaceMember()
  const updateRoleMutation = useUpdateAdminNamespaceMemberRole()
  const removeMemberMutation = useRemoveAdminNamespaceMember()
  const transferMutation = useTransferAdminNamespaceOwnership()

  const namespaces = namespacesQuery.data?.items ?? EMPTY_NAMESPACES
  const selectedNamespace = selectedNamespaceQuery.data ?? namespaces.find((item) => item.slug === selectedSlug) ?? null
  const members = membersQuery.data?.items ?? EMPTY_MEMBERS
  const totalMembers = membersQuery.data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil((namespacesQuery.data?.total ?? 0) / PAGE_SIZE))
  const memberTotalPages = Math.max(1, Math.ceil(totalMembers / MEMBER_PAGE_SIZE))

  useEffect(() => {
    setPage(0)
  }, [keyword, statusFilter, typeFilter])

  useEffect(() => {
    if (!selectedSlug && namespaces.length > 0) {
      setSelectedSlug(namespaces[0].slug)
    }
  }, [selectedSlug, namespaces])

  useEffect(() => {
    setMemberPage(0)
    setDraftRoles({})
  }, [selectedSlug])

  const stats = namespacesQuery.data?.stats
  const transferCandidates = useMemo(
    () => members.filter((member) => member.role !== 'OWNER'),
    [members],
  )

  const applySearch = () => {
    setKeyword(keywordInput.trim())
  }

  const clearSearch = () => {
    setKeywordInput('')
    setKeyword('')
  }

  const handleSearchKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      applySearch()
    }
  }

  const openAddMemberDialog = (namespace: AdminNamespace) => {
    setAddMemberDialog({
      open: true,
      slug: namespace.slug,
      userId: '',
      role: 'MEMBER',
      search: '',
    })
  }

  const closeAddMemberDialog = () => {
    setAddMemberDialog({
      open: false,
      slug: '',
      userId: '',
      role: 'MEMBER',
      search: '',
    })
  }

  const openTransferDialog = (namespace: AdminNamespace) => {
    setTransferDialog({
      open: true,
      slug: namespace.slug,
      selectedUserId: '',
      confirmSlug: '',
    })
  }

  const closeTransferDialog = () => {
    setTransferDialog({
      open: false,
      slug: '',
      selectedUserId: '',
      confirmSlug: '',
    })
  }

  const updateDraftRole = (userId: string, role: string) => {
    setDraftRoles((current) => ({ ...current, [userId]: role }))
  }

  const saveMemberRole = async (member: NamespaceMember) => {
    if (!selectedSlug) return
    const nextRole = draftRoles[member.userId] ?? member.role
    if (nextRole === member.role) return
    try {
      await updateRoleMutation.mutateAsync({ slug: selectedSlug, userId: member.userId, role: nextRole })
      setDraftRoles((current) => {
        const next = { ...current }
        delete next[member.userId]
        return next
      })
      toast.success(t('adminNamespaces.memberRoleUpdated'), t('members.updateRoleSuccessDescription', { userId: member.userId, role: nextRole }))
    } catch (error) {
      toast.error(t('members.updateRoleErrorTitle'), error instanceof Error ? error.message : '')
    }
  }

  const addMember = async () => {
    if (!addMemberDialog.slug || !addMemberDialog.userId.trim()) {
      toast.error(t('members.userIdRequired'))
      return
    }
    try {
      await addMemberMutation.mutateAsync({
        slug: addMemberDialog.slug,
        userId: addMemberDialog.userId,
        role: addMemberDialog.role,
      })
      toast.success(t('members.addSuccessTitle'), t('members.addSuccessDescription', { userId: addMemberDialog.userId }))
      closeAddMemberDialog()
    } catch (error) {
      toast.error(t('members.addErrorTitle'), error instanceof Error ? error.message : '')
    }
  }

  const removeMember = async () => {
    if (!pendingRemoval) return
    try {
      await removeMemberMutation.mutateAsync(pendingRemoval)
      toast.success(t('members.removeSuccessTitle'), t('members.removeSuccessDescription', { userId: pendingRemoval.userId }))
      setPendingRemoval(null)
    } catch (error) {
      toast.error(t('members.removeErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  const transferOwnership = async () => {
    if (!transferDialog.slug || !transferDialog.selectedUserId) return
    try {
      await transferMutation.mutateAsync({
        slug: transferDialog.slug,
        newOwnerUserId: transferDialog.selectedUserId,
      })
      toast.success(t('members.transferSuccessTitle'), t('members.transferSuccessDescription', { userId: transferDialog.selectedUserId }))
      closeTransferDialog()
    } catch (error) {
      toast.error(t('members.transferErrorTitle'), error instanceof Error ? error.message : '')
    }
  }

  const runLifecycleAction = async () => {
    if (!pendingLifecycleAction) return
    try {
      await lifecycleMutation.mutateAsync({
        slug: pendingLifecycleAction.slug,
        action: pendingLifecycleAction.action,
        reason: lifecycleReason,
      })
      toast.success(
        t(`adminNamespaces.${pendingLifecycleAction.action}SuccessTitle`),
        t(`adminNamespaces.${pendingLifecycleAction.action}SuccessDescription`, { name: pendingLifecycleAction.name }),
      )
      setPendingLifecycleAction(null)
      setLifecycleReason('')
    } catch (error) {
      toast.error(t(`adminNamespaces.${pendingLifecycleAction.action}ErrorTitle`), error instanceof Error ? error.message : '')
      throw error
    }
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="text-4xl font-bold font-heading mb-2">{t('adminNamespaces.title')}</h1>
        <p className="text-muted-foreground text-lg">{t('adminNamespaces.subtitle')}</p>
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        <StatCard label={t('adminNamespaces.statTotal')} value={stats?.total ?? 0} />
        <StatCard label={t('adminNamespaces.statActive')} value={stats?.active ?? 0} tone="success" />
        <StatCard label={t('adminNamespaces.statFrozen')} value={stats?.frozen ?? 0} tone="warning" />
        <StatCard label={t('adminNamespaces.statArchived')} value={stats?.archived ?? 0} tone="muted" />
      </div>

      <Card className="p-5">
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_180px_180px]">
          <div className="space-y-2">
            <Label htmlFor="admin-namespace-search">{t('adminNamespaces.searchLabel')}</Label>
            <div className="flex gap-2">
              <Input data-analytics-id="admin.namespace.search"
                id="admin-namespace-search"
                placeholder={t('adminNamespaces.searchPlaceholder')}
                value={keywordInput}
                onChange={(event) => setKeywordInput(event.target.value)}
                onKeyDown={handleSearchKeyDown}
              />
              <Button data-analytics-id="admin.namespace.search" type="button" onClick={applySearch}>{t('adminNamespaces.searchAction')}</Button>
              <Button data-analytics-id="admin.namespace.clear" type="button" variant="outline" onClick={clearSearch} disabled={!keywordInput && !keyword}>
                {t('adminNamespaces.clearSearch')}
              </Button>
            </div>
          </div>
          <div className="space-y-2">
            <Label>{t('adminNamespaces.statusFilter')}</Label>
            <Select
              value={normalizeSelectValue(statusFilter) ?? ALL_FILTER_VALUE}
              onValueChange={(value) => setStatusFilter(value === ALL_FILTER_VALUE ? '' : value)}
            >
              <SelectTrigger data-analytics-id="admin.namespace.status.interact"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem data-analytics-id="admin.namespace.status.filter" value={ALL_FILTER_VALUE}>{t('adminNamespaces.filterAll')}</SelectItem>
                <SelectItem data-analytics-id="admin.namespace.status.filter" value="ACTIVE">{t('namespaceStatus.active')}</SelectItem>
                <SelectItem data-analytics-id="admin.namespace.status.filter" value="FROZEN">{t('namespaceStatus.frozen')}</SelectItem>
                <SelectItem data-analytics-id="admin.namespace.status.filter" value="ARCHIVED">{t('namespaceStatus.archived')}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>{t('adminNamespaces.typeFilter')}</Label>
            <Select
              value={normalizeSelectValue(typeFilter) ?? ALL_FILTER_VALUE}
              onValueChange={(value) => setTypeFilter(value === ALL_FILTER_VALUE ? '' : value)}
            >
              <SelectTrigger data-analytics-id="admin.namespace.type.interact"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem data-analytics-id="admin.namespace.type.filter" value={ALL_FILTER_VALUE}>{t('adminNamespaces.filterAll')}</SelectItem>
                <SelectItem data-analytics-id="admin.namespace.type.filter" value="GLOBAL">{t('adminNamespaces.typeGlobal')}</SelectItem>
                <SelectItem data-analytics-id="admin.namespace.type.filter" value="TEAM">{t('adminNamespaces.typeTeam')}</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      </Card>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.1fr)_minmax(26rem,0.9fr)]">
        <Card className="overflow-hidden">
          {namespacesQuery.isLoading ? (
            <div className="space-y-3 p-5">
              {Array.from({ length: 5 }).map((_, index) => (
                <div key={index} className="h-14 animate-shimmer rounded-lg" />
              ))}
            </div>
          ) : namespaces.length === 0 ? (
            <div className="p-12 text-center text-muted-foreground">{t('adminNamespaces.empty')}</div>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('adminNamespaces.colNamespace')}</TableHead>
                    <TableHead>{t('adminNamespaces.colType')}</TableHead>
                    <TableHead>{t('adminNamespaces.colStatus')}</TableHead>
                    <TableHead>{t('adminNamespaces.colMembers')}</TableHead>
                    <TableHead>{t('adminNamespaces.colSkills')}</TableHead>
                    <TableHead>{t('adminNamespaces.colUpdated')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {namespaces.map((namespace) => (
                    <TableRow data-analytics-id="admin.namespace.open"
                      key={namespace.slug}
                      className={selectedSlug === namespace.slug ? 'bg-secondary/50' : undefined}
                      onClick={() => setSelectedSlug(namespace.slug)}
                    >
                      <TableCell>
                        <div className="font-medium">{namespace.displayName}</div>
                        <div className="font-mono text-xs text-muted-foreground">@{namespace.slug}</div>
                      </TableCell>
                      <TableCell>{namespaceTypeLabel(t, namespace.type)}</TableCell>
                      <TableCell>
                        <span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-medium ${statusClassName(namespace.status)}`}>
                          {statusLabel(t, namespace.status)}
                        </span>
                      </TableCell>
                      <TableCell>{namespace.stats.memberCount}</TableCell>
                      <TableCell>{namespace.stats.skillCount}</TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatLocalDateTime(namespace.updatedAt, i18n.language, { dateStyle: 'medium' })}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              {totalPages > 1 ? <Pagination analyticsId="admin.namespace.paginate" page={page} totalPages={totalPages} onPageChange={setPage} /> : null}
            </>
          )}
        </Card>

        <Card className="p-5">
          {!selectedNamespace ? (
            <div className="py-20 text-center text-muted-foreground">{t('adminNamespaces.selectNamespace')}</div>
          ) : (
            <div className="space-y-6">
              <NamespaceDetailHeader namespace={selectedNamespace} t={t} i18nLanguage={i18n.language} />
              <div className="grid grid-cols-2 gap-3">
                <SmallFact label={t('adminNamespaces.members')} value={selectedNamespace.stats.memberCount} />
                <SmallFact label={t('adminNamespaces.skills')} value={selectedNamespace.stats.skillCount} />
                <SmallFact label={t('adminNamespaces.currentRole')} value={displayRole(t, selectedNamespace.permissions.currentUserRole)} />
                <SmallFact label={t('adminNamespaces.platformOverride')} value={selectedNamespace.permissions.platformOverride ? t('adminNamespaces.yes') : t('adminNamespaces.no')} />
              </div>

              <div className="space-y-3">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <h2 className="font-heading text-lg font-semibold">{t('adminNamespaces.membersTitle')}</h2>
                    <p className="text-sm text-muted-foreground">{t('adminNamespaces.membersDescription')}</p>
                  </div>
                  <div className="flex gap-2">
                    <Button data-analytics-id="admin.namespace.transfer"
                      variant="outline"
                      size="sm"
                      disabled={!selectedNamespace.permissions.canTransferOwnership}
                      onClick={() => openTransferDialog(selectedNamespace)}
                    >
                      {t('members.transferOwnership')}
                    </Button>
                    <Button data-analytics-id="admin.namespace.member.open"
                      size="sm"
                      disabled={!selectedNamespace.permissions.canManageMembers}
                      onClick={() => openAddMemberDialog(selectedNamespace)}
                    >
                      {t('members.addMember')}
                    </Button>
                  </div>
                </div>

                <MembersTable
                  members={members}
                  canManageMembers={selectedNamespace.permissions.canManageMembers}
                  draftRoles={draftRoles}
                  onDraftRoleChange={updateDraftRole}
                  onSaveRole={saveMemberRole}
                  onRemove={(member) => setPendingRemoval({ slug: selectedNamespace.slug, userId: member.userId })}
                  t={t}
                  i18nLanguage={i18n.language}
                />
                {memberTotalPages > 1 ? <Pagination analyticsId="admin.namespace.member.paginate" page={memberPage} totalPages={memberTotalPages} onPageChange={setMemberPage} /> : null}
              </div>

              <div className="space-y-3 rounded-xl border border-destructive/20 bg-destructive/5 p-4">
                <div>
                  <h2 className="font-heading text-lg font-semibold">{t('adminNamespaces.governanceTitle')}</h2>
                  <p className="text-sm text-muted-foreground">{t('adminNamespaces.governanceDescription')}</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button data-analytics-id="admin.namespace.freeze"
                    variant="outline"
                    size="sm"
                    disabled={!selectedNamespace.permissions.canFreeze}
                    onClick={() => setPendingLifecycleAction({ slug: selectedNamespace.slug, name: selectedNamespace.displayName, action: 'freeze' })}
                  >
                    {t('myNamespaces.freeze')}
                  </Button>
                  <Button data-analytics-id="admin.namespace.unfreeze"
                    variant="outline"
                    size="sm"
                    disabled={!selectedNamespace.permissions.canUnfreeze}
                    onClick={() => setPendingLifecycleAction({ slug: selectedNamespace.slug, name: selectedNamespace.displayName, action: 'unfreeze' })}
                  >
                    {t('myNamespaces.unfreeze')}
                  </Button>
                  <Button data-analytics-id="admin.namespace.archive"
                    variant="destructive"
                    size="sm"
                    disabled={!selectedNamespace.permissions.canArchive}
                    onClick={() => setPendingLifecycleAction({ slug: selectedNamespace.slug, name: selectedNamespace.displayName, action: 'archive' })}
                  >
                    {t('myNamespaces.archive')}
                  </Button>
                  <Button data-analytics-id="admin.namespace.restore"
                    variant="outline"
                    size="sm"
                    disabled={!selectedNamespace.permissions.canRestore}
                    onClick={() => setPendingLifecycleAction({ slug: selectedNamespace.slug, name: selectedNamespace.displayName, action: 'restore' })}
                  >
                    {t('myNamespaces.restore')}
                  </Button>
                </div>
              </div>
            </div>
          )}
        </Card>
      </div>

      <Dialog open={addMemberDialog.open} onOpenChange={(open) => (open ? setAddMemberDialog((current) => ({ ...current, open })) : closeAddMemberDialog())}>
        <DialogContent closeAnalyticsId="admin.namespace.member.cancel">
          <DialogHeader>
            <DialogTitle>{t('members.addDialogTitle')}</DialogTitle>
            <DialogDescription>{t('members.addDialogDescription')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>{t('members.searchLabel')}</Label>
              <Input data-analytics-id="admin.namespace.member.search"
                value={addMemberDialog.search}
                placeholder={t('members.searchPlaceholder')}
                onChange={(event) => setAddMemberDialog((current) => ({ ...current, search: event.target.value }))}
              />
              {candidatesQuery.data && candidatesQuery.data.length > 0 ? (
                <div className="max-h-44 space-y-2 overflow-y-auto rounded-lg border border-border/60 p-2">
                  {candidatesQuery.data.map((candidate) => (
                    <button data-analytics-id="admin.namespace.member.select"
                      key={candidate.userId}
                      type="button"
                      className="w-full rounded-md px-3 py-2 text-left text-sm hover:bg-secondary"
                      onClick={() => setAddMemberDialog((current) => ({ ...current, userId: candidate.userId }))}
                    >
                      <div className="font-medium">{candidate.displayName || candidate.userId}</div>
                      <div className="text-xs text-muted-foreground">{candidate.email || candidate.userId}</div>
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label>{t('members.manualUserIdLabel')}</Label>
              <Input
                value={addMemberDialog.userId}
                placeholder={t('members.manualUserIdPlaceholder')}
                onChange={(event) => setAddMemberDialog((current) => ({ ...current, userId: event.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label>{t('members.roleLabel')}</Label>
              <Select value={addMemberDialog.role} onValueChange={(role) => setAddMemberDialog((current) => ({ ...current, role }))}>
                <SelectTrigger data-analytics-id="admin.namespace.member.role.interact"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem data-analytics-id="admin.namespace.member.role.member" value="MEMBER">{t('members.roleMember')}</SelectItem>
                  <SelectItem data-analytics-id="admin.namespace.member.role.admin" value="ADMIN">{t('members.roleAdmin')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button data-analytics-id="admin.namespace.member.cancel" type="button" variant="outline" onClick={closeAddMemberDialog}>{t('dialog.cancel')}</Button>
            <Button data-analytics-id="admin.namespace.member.add" type="button" disabled={addMemberMutation.isPending} onClick={addMember}>{t('members.addMember')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={transferDialog.open} onOpenChange={(open) => (open ? setTransferDialog((current) => ({ ...current, open })) : closeTransferDialog())}>
        <DialogContent closeAnalyticsId="admin.namespace.transfer.cancel">
          <DialogHeader>
            <DialogTitle>{t('members.transferDialogTitle')}</DialogTitle>
            <DialogDescription>{t('members.transferDialogDescription')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="rounded-lg border border-amber-500/20 bg-amber-500/10 p-3 text-sm text-amber-700 dark:text-amber-400">
              {t('members.transferWarning')}
            </div>
            <div className="space-y-2">
              <Label>{t('members.transferNewOwnerLabel')}</Label>
              <Select
                value={transferDialog.selectedUserId}
                onValueChange={(selectedUserId) => setTransferDialog((current) => ({ ...current, selectedUserId }))}
              >
                <SelectTrigger data-analytics-id="admin.namespace.ownership.interact"><SelectValue placeholder={t('members.transferSelectPlaceholder')} /></SelectTrigger>
                <SelectContent>
                  {transferCandidates.map((candidate) => (
                    <SelectItem data-analytics-id="admin.namespace.ownership.select" key={candidate.userId} value={candidate.userId}>
                      {candidate.displayName || candidate.userId} ({candidate.role})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>{t('members.transferConfirmSlugPrompt')}</Label>
              <Input
                value={transferDialog.confirmSlug}
                placeholder={transferDialog.slug}
                onChange={(event) => setTransferDialog((current) => ({ ...current, confirmSlug: event.target.value }))}
              />
            </div>
          </div>
          <DialogFooter>
            <Button data-analytics-id="admin.namespace.transfer.cancel" type="button" variant="outline" onClick={closeTransferDialog}>{t('dialog.cancel')}</Button>
            <Button data-analytics-id="admin.namespace.transfer.confirm"
              type="button"
              variant="destructive"
              disabled={!transferDialog.selectedUserId || transferDialog.confirmSlug !== transferDialog.slug || transferMutation.isPending}
              onClick={transferOwnership}
            >
              {t('members.transferConfirmAction')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog confirmAnalyticsId="admin.namespace.member.remove.confirm" cancelAnalyticsId="admin.namespace.member.remove.cancel"
        open={!!pendingRemoval}
        onOpenChange={(open) => {
          if (!open) setPendingRemoval(null)
        }}
        title={t('members.removeConfirmTitle')}
        description={pendingRemoval ? t('members.removeConfirmDescription', { userId: pendingRemoval.userId }) : ''}
        confirmText={t('members.remove')}
        variant="destructive"
        onConfirm={removeMember}
      />

      <Dialog
        open={!!pendingLifecycleAction}
        onOpenChange={(open) => {
          if (!open) {
            setPendingLifecycleAction(null)
            setLifecycleReason('')
          }
        }}
      >
        <DialogContent closeAnalyticsId={pendingLifecycleAction?.action === 'freeze' ? 'admin.namespace.freeze.cancel' : pendingLifecycleAction?.action === 'unfreeze' ? 'admin.namespace.unfreeze.cancel' : pendingLifecycleAction?.action === 'archive' ? 'admin.namespace.archive.cancel' : 'admin.namespace.restore.cancel'}>
          <DialogHeader>
            <DialogTitle>{pendingLifecycleAction ? t(`adminNamespaces.${pendingLifecycleAction.action}ConfirmTitle`) : ''}</DialogTitle>
            <DialogDescription>
              {pendingLifecycleAction ? t(`adminNamespaces.${pendingLifecycleAction.action}ConfirmDescription`, { name: pendingLifecycleAction.name }) : ''}
            </DialogDescription>
          </DialogHeader>
          {pendingLifecycleAction?.action === 'freeze' || pendingLifecycleAction?.action === 'archive' ? (
            <div className="space-y-2">
              <Label>{t('adminNamespaces.reasonLabel')}</Label>
              <Textarea
                value={lifecycleReason}
                placeholder={t('adminNamespaces.reasonPlaceholder')}
                onChange={(event) => setLifecycleReason(event.target.value)}
              />
            </div>
          ) : null}
          <DialogFooter>
            <Button data-analytics-id={pendingLifecycleAction?.action === 'freeze' ? 'admin.namespace.freeze.cancel' : pendingLifecycleAction?.action === 'unfreeze' ? 'admin.namespace.unfreeze.cancel' : pendingLifecycleAction?.action === 'archive' ? 'admin.namespace.archive.cancel' : 'admin.namespace.restore.cancel'} type="button" variant="outline" onClick={() => setPendingLifecycleAction(null)}>{t('dialog.cancel')}</Button>
            <Button data-analytics-id={pendingLifecycleAction?.action === 'freeze' ? 'admin.namespace.freeze.confirm' : pendingLifecycleAction?.action === 'unfreeze' ? 'admin.namespace.unfreeze.confirm' : pendingLifecycleAction?.action === 'archive' ? 'admin.namespace.archive.confirm' : 'admin.namespace.restore.confirm'}
              type="button"
              variant={pendingLifecycleAction?.action === 'archive' ? 'destructive' : 'default'}
              disabled={lifecycleMutation.isPending}
              onClick={runLifecycleAction}
            >
              {pendingLifecycleAction ? t(`adminNamespaces.${pendingLifecycleAction.action}Action`) : t('dialog.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function StatCard({ label, value, tone = 'default' }: { label: string; value: number; tone?: 'default' | 'success' | 'warning' | 'muted' }) {
  const toneClassName = tone === 'success'
    ? 'text-emerald-500'
    : tone === 'warning'
      ? 'text-amber-500'
      : tone === 'muted'
        ? 'text-muted-foreground'
        : 'text-foreground'
  return (
    <Card className="p-5">
      <div className="text-sm text-muted-foreground">{label}</div>
      <div className={`mt-2 text-3xl font-bold ${toneClassName}`}>{value}</div>
    </Card>
  )
}

function SmallFact({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-lg border border-border/50 bg-secondary/30 p-3">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-1 text-sm font-semibold">{value}</div>
    </div>
  )
}

function NamespaceDetailHeader({
  namespace,
  t,
  i18nLanguage,
}: {
  namespace: AdminNamespace
  t: (key: string, options?: Record<string, unknown>) => string
  i18nLanguage: string
}) {
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-heading text-2xl font-semibold">{namespace.displayName}</h2>
          <p className="font-mono text-sm text-muted-foreground">@{namespace.slug}</p>
        </div>
        <div className="flex gap-2">
          <span className="inline-flex rounded-full border border-border/50 px-2.5 py-1 text-xs font-medium">
            {namespaceTypeLabel(t, namespace.type)}
          </span>
          <span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-medium ${statusClassName(namespace.status)}`}>
            {statusLabel(t, namespace.status)}
          </span>
        </div>
      </div>
      {namespace.description ? (
        <p className="text-sm text-muted-foreground">{namespace.description}</p>
      ) : null}
      <div className="text-xs text-muted-foreground">
        {t('adminNamespaces.createdAt')}: {formatLocalDateTime(namespace.createdAt, i18nLanguage)} · {t('adminNamespaces.updatedAt')}: {formatLocalDateTime(namespace.updatedAt, i18nLanguage)}
      </div>
    </div>
  )
}

function MembersTable({
  members,
  canManageMembers,
  draftRoles,
  onDraftRoleChange,
  onSaveRole,
  onRemove,
  t,
  i18nLanguage,
}: {
  members: NamespaceMember[]
  canManageMembers: boolean
  draftRoles: Record<string, string>
  onDraftRoleChange: (userId: string, role: string) => void
  onSaveRole: (member: NamespaceMember) => void
  onRemove: (member: NamespaceMember) => void
  t: (key: string, options?: Record<string, unknown>) => string
  i18nLanguage: string
}) {
  if (members.length === 0) {
    return <div className="rounded-lg border border-border/60 p-6 text-center text-sm text-muted-foreground">{t('members.empty')}</div>
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('members.colUsername')}</TableHead>
          <TableHead>{t('members.colRole')}</TableHead>
          <TableHead>{t('members.colJoinedAt')}</TableHead>
          <TableHead className="text-right">{t('members.colActions')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {members.map((member) => {
          const isOwner = member.role === 'OWNER'
          const roleValue = draftRoles[member.userId] ?? member.role
          return (
            <TableRow key={member.id}>
              <TableCell>
                <div className="font-medium">{member.displayName || member.userId}</div>
                <div className="text-xs text-muted-foreground">{member.email || member.userId}</div>
              </TableCell>
              <TableCell>
                {canManageMembers && !isOwner ? (
                  <div className="flex flex-wrap items-center gap-2">
                    <Select value={roleValue} onValueChange={(role) => onDraftRoleChange(member.userId, role)}>
                      <SelectTrigger data-analytics-id="admin.namespace.member.role.draft.interact" className="w-32"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem data-analytics-id="admin.namespace.member.role.draft.member" value="MEMBER">{t('members.roleMember')}</SelectItem>
                        <SelectItem data-analytics-id="admin.namespace.member.role.draft.admin" value="ADMIN">{t('members.roleAdmin')}</SelectItem>
                      </SelectContent>
                    </Select>
                    <Button data-analytics-id="admin.namespace.member.role"
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={roleValue === member.role}
                      onClick={() => onSaveRole(member)}
                    >
                      {t('members.saveRole')}
                    </Button>
                  </div>
                ) : (
                  <span className="inline-flex rounded-full border border-accent/20 bg-accent/10 px-2.5 py-1 text-xs font-medium text-accent">
                    {displayRole(t, member.role)}
                  </span>
                )}
              </TableCell>
              <TableCell className="text-muted-foreground">
                {formatLocalDateTime(member.createdAt, i18nLanguage, { dateStyle: 'medium' })}
              </TableCell>
              <TableCell className="text-right">
                <Button data-analytics-id="admin.namespace.member.remove"
                  type="button"
                  variant="destructive"
                  size="sm"
                  disabled={!canManageMembers || isOwner}
                  onClick={() => onRemove(member)}
                >
                  {t('members.remove')}
                </Button>
              </TableCell>
            </TableRow>
          )
        })}
      </TableBody>
    </Table>
  )
}
