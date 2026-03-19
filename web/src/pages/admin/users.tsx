import { KeyboardEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'
import { Select } from '@/shared/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import { useAdminUsers, useApproveUser, useDisableUser, useEnableUser, useUpdateUserRole } from '@/features/admin/use-admin-users'
import type { AdminUser } from '@/features/admin/use-admin-users'

/**
 * Admin user management page that combines search, status filtering, approval,
 * activation control, and role changes in one route-level container.
 */
export function AdminUsersPage() {
  const { t, i18n } = useTranslation()
  const roleOptions = [
    { value: 'USER', label: t('adminUsers.roleUser') },
    { value: 'SKILL_ADMIN', label: t('adminUsers.roleReviewer') },
    { value: 'USER_ADMIN', label: t('adminUsers.roleUserAdmin') },
    { value: 'AUDITOR', label: t('adminUsers.roleAuditor') },
    { value: 'SUPER_ADMIN', label: t('adminUsers.roleSuperAdmin') },
  ]
  const [search, setSearch] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [page, setPage] = useState(0)
  const [selectedUser, setSelectedUser] = useState<AdminUser | null>(null)
  const [roleDialogOpen, setRoleDialogOpen] = useState(false)
  const [newRole, setNewRole] = useState('')
  const [confirmDialogOpen, setConfirmDialogOpen] = useState(false)
  const [actionType, setActionType] = useState<'ban' | 'unban'>('ban')

  const { data, isLoading } = useAdminUsers({
    search,
    status: statusFilter || undefined,
    page,
    size: 20,
  })

  const updateRoleMutation = useUpdateUserRole()
  const approveUserMutation = useApproveUser()
  const disableUserMutation = useDisableUser()
  const enableUserMutation = useEnableUser()

  const formatDate = (dateString: string) => {
    return formatLocalDateTime(dateString, i18n.language)
  }

  useEffect(() => {
    setPage(0)
  }, [search, statusFilter])

  const applySearch = () => {
    setSearch(searchInput.trim())
  }

  const clearSearch = () => {
    setSearchInput('')
    setSearch('')
  }

  const handleSearchKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      applySearch()
    }
  }

  const handleChangeRole = (user: AdminUser) => {
    setSelectedUser(user)
    // The current backend model effectively treats the first platform role as
    // the primary editable role in this screen.
    setNewRole(user.platformRoles[0] || 'USER')
    setRoleDialogOpen(true)
  }

  const handleToggleStatus = (user: AdminUser, action: 'ban' | 'unban') => {
    setSelectedUser(user)
    setActionType(action)
    setConfirmDialogOpen(true)
  }

  const confirmRoleChange = async () => {
    if (!selectedUser || !newRole || newRole === (selectedUser.platformRoles[0] || 'USER')) return
    try {
      await updateRoleMutation.mutateAsync({ userId: selectedUser.userId, role: newRole })
      setRoleDialogOpen(false)
      setSelectedUser(null)
    } catch (error) {
      console.error('Failed to update role:', error)
    }
  }

  const confirmStatusChange = async () => {
    if (!selectedUser) return
    try {
      if (actionType === 'ban') {
        await disableUserMutation.mutateAsync(selectedUser.userId)
      } else {
        await enableUserMutation.mutateAsync(selectedUser.userId)
      }
      setConfirmDialogOpen(false)
      setSelectedUser(null)
    } catch (error) {
      console.error('Failed to update status:', error)
    }
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="text-4xl font-bold font-heading mb-2">{t('adminUsers.title')}</h1>
        <p className="text-muted-foreground text-lg">{t('adminUsers.subtitle')}</p>
      </div>

      <Card className="p-5">
        <div className="grid gap-4 md:grid-cols-[minmax(0,1.6fr)_220px]">
          <div className="space-y-2">
            <Label htmlFor="admin-user-search">{t('adminUsers.searchLabel')}</Label>
            <div className="flex gap-2">
              <Input
                id="admin-user-search"
                placeholder={t('adminUsers.searchPlaceholder')}
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                onKeyDown={handleSearchKeyDown}
                className="flex-1"
              />
              <Button type="button" onClick={applySearch}>
                {t('adminUsers.searchAction')}
              </Button>
              <Button type="button" variant="outline" onClick={clearSearch} disabled={!searchInput && !search}>
                {t('adminUsers.clearSearch')}
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">{t('adminUsers.searchHint')}</p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="admin-user-status">{t('adminUsers.filterLabel')}</Label>
            <Select id="admin-user-status" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="">{t('adminUsers.filterAll')}</option>
              <option value="ACTIVE">{t('adminUsers.filterActive')}</option>
              <option value="PENDING">{t('adminUsers.filterPending')}</option>
              <option value="DISABLED">{t('adminUsers.filterDisabled')}</option>
            </Select>
          </div>
        </div>
      </Card>

      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-14 animate-shimmer rounded-lg" />
          ))}
        </div>
      ) : !data || data.items.length === 0 ? (
        <Card className="p-12 text-center">
          <p className="text-muted-foreground">{t('adminUsers.empty')}</p>
        </Card>
      ) : (
        <>
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('adminUsers.colUsername')}</TableHead>
                  <TableHead>{t('adminUsers.colEmail')}</TableHead>
                  <TableHead>{t('adminUsers.colStatus')}</TableHead>
                  <TableHead>{t('adminUsers.colRole')}</TableHead>
                  <TableHead>{t('adminUsers.colCreatedAt')}</TableHead>
                  <TableHead>{t('adminUsers.colActions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((user) => (
                  <TableRow key={user.userId}>
                    <TableCell className="font-medium">{user.username}</TableCell>
                    <TableCell>{user.email || '-'}</TableCell>
                    <TableCell>
                      <span
                        className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${
                          user.status === 'ACTIVE'
                            ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                            : user.status === 'PENDING'
                              ? 'bg-amber-500/10 text-amber-400 border-amber-500/20'
                              : 'bg-red-500/10 text-red-400 border-red-500/20'
                        }`}
                      >
                        {user.status === 'ACTIVE' ? t('adminUsers.statusActive') : user.status === 'PENDING' ? t('adminUsers.statusPending') : t('adminUsers.statusDisabled')}
                      </span>
                    </TableCell>
                    <TableCell>{user.platformRoles.join(', ')}</TableCell>
                    <TableCell>{formatDate(user.createdAt)}</TableCell>
                    <TableCell>
                      <div className="flex gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleChangeRole(user)}
                        >
                          {t('adminUsers.changeRole')}
                        </Button>
                        {user.status === 'PENDING' && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => approveUserMutation.mutate(user.userId)}
                          >
                            {t('adminUsers.approveUser')}
                          </Button>
                        )}
                        {user.status === 'ACTIVE' ? (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleToggleStatus(user, 'ban')}
                          >
                            {t('adminUsers.disable')}
                          </Button>
                        ) : (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleToggleStatus(user, 'unban')}
                          >
                            {t('adminUsers.enable')}
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>

          <div className="flex justify-between items-center">
            <p className="text-sm text-muted-foreground">
              {t('adminUsers.totalRecords', { total: data.total, page: page + 1 })}
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                {t('adminUsers.prevPage')}
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={(page + 1) * 20 >= data.total}
                onClick={() => setPage(page + 1)}
              >
                {t('adminUsers.nextPage')}
              </Button>
            </div>
          </div>
        </>
      )}

   <Dialog open={roleDialogOpen} onOpenChange={setRoleDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('adminUsers.changeRoleTitle')}</DialogTitle>
            <DialogDescription>
              {t('adminUsers.changeRoleDesc', { username: selectedUser?.username })}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="role">{t('adminUsers.roleLabel')}</Label>
              <Select id="role" value={newRole} onChange={(e) => setNewRole(e.target.value)}>
                {roleOptions.map((roleOption) => (
                  <option key={roleOption.value} value={roleOption.value}>
                    {roleOption.label}
                  </option>
                ))}
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRoleDialogOpen(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button
              onClick={confirmRoleChange}
              disabled={
                updateRoleMutation.isPending
                || !selectedUser
                || !newRole
                || newRole === (selectedUser.platformRoles[0] || 'USER')
              }
            >
              {t('dialog.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={confirmDialogOpen} onOpenChange={setConfirmDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('adminUsers.confirmAction')}</DialogTitle>
            <DialogDescription>
              {actionType === 'ban' ? t('adminUsers.confirmDisable', { username: selectedUser?.username }) : t('adminUsers.confirmEnable', { username: selectedUser?.username })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmDialogOpen(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button onClick={confirmStatusChange} disabled={disableUserMutation.isPending || enableUserMutation.isPending}>
              {t('dialog.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
