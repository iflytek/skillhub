import { useState } from 'react'
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

export function AdminUsersPage() {
  const [search, setSearch] = useState('')
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
    return new Date(dateString).toLocaleString('zh-CN')
  }

  const handleChangeRole = (user: AdminUser) => {
    setSelectedUser(user)
    setNewRole(user.platformRoles[0] || '')
    setRoleDialogOpen(true)
  }

  const handleToggleStatus = (user: AdminUser, action: 'ban' | 'unban') => {
    setSelectedUser(user)
    setActionType(action)
    setConfirmDialogOpen(true)
  }

  const confirmRoleChange = async () => {
    if (!selectedUser) return
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
        <h1 className="text-4xl font-bold font-heading mb-2">用户管理</h1>
        <p className="text-muted-foreground text-lg">管理平台用户和权限</p>
      </div>

      <Card className="p-5">
        <div className="flex gap-4">
          <Input
            placeholder="搜索用户名或邮箱..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="flex-1"
          />
          <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="">全部</option>
            <option value="ACTIVE">活跃</option>
            <option value="PENDING">待审批</option>
            <option value="DISABLED">已禁用</option>
          </Select>
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
          <p className="text-muted-foreground">暂无用户数据</p>
        </Card>
      ) : (
        <>
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>用户名</TableHead>
                  <TableHead>邮箱</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>角色</TableHead>
                  <TableHead>创建时间</TableHead>
                  <TableHead>操作</TableHead>
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
                        {user.status === 'ACTIVE' ? '活跃' : user.status === 'PENDING' ? '待审批' : '已禁用'}
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
                          修改角色
                        </Button>
                        {user.status === 'PENDING' && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => approveUserMutation.mutate(user.userId)}
                          >
                            审批通过
                          </Button>
                        )}
                        {user.status === 'ACTIVE' ? (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleToggleStatus(user, 'ban')}
                          >
                            禁用
                          </Button>
                        ) : (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleToggleStatus(user, 'unban')}
                          >
                            启用
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
              共 {data.total} 条记录，第 {page + 1} 页
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                上一页
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={(page + 1) * 20 >= data.total}
                onClick={() => setPage(page + 1)}
              >
                下一页
              </Button>
            </div>
          </div>
        </>
      )}

   <Dialog open={roleDialogOpen} onOpenChange={setRoleDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>修改用户角色</DialogTitle>
            <DialogDescription>
              为用户 {selectedUser?.username} 分配新角色
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="role">角色</Label>
              <Select id="role" value={newRole} onChange={(e) => setNewRole(e.target.value)}>
                <option value="">选择角色</option>
                <option value="USER">普通用户</option>
                <option value="REVIEWER">审核员</option>
                <option value="USER_ADMIN">用户管理员</option>
                <option value="AUDITOR">审计员</option>
                <option value="SUPER_ADMIN">超级管理员</option>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRoleDialogOpen(false)}>
              取消
            </Button>
            <Button onClick={confirmRoleChange} disabled={updateRoleMutation.isPending}>
              确认
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={confirmDialogOpen} onOpenChange={setConfirmDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认操作</DialogTitle>
            <DialogDescription>
              确定要{actionType === 'ban' ? '禁用' : '启用'}用户 {selectedUser?.username} 吗？
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmDialogOpen(false)}>
              取消
            </Button>
            <Button onClick={confirmStatusChange} disabled={disableUserMutation.isPending || enableUserMutation.isPending}>
              确认
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
