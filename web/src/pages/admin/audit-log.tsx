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
import { useAuditLog } from '@/features/admin/use-audit-log'

export function AuditLogPage() {
  const [actionFilter, setActionFilter] = useState<string>('')
  const [userIdFilter, setUserIdFilter] = useState('')
  const [page, setPage] = useState(0)

  const { data, isLoading } = useAuditLog({
    action: actionFilter || undefined,
    userId: userIdFilter || undefined,
    page,
    size: 20,
  })

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('zh-CN')
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="text-4xl font-bold font-heading mb-2">审计日志</h1>
        <p className="text-muted-foreground text-lg">查看系统操作记录</p>
      </div>

      <Card className="p-5">
        <div className="flex gap-4">
          <Select value={actionFilter} onChange={(e) => setActionFilter(e.target.value)} className="w-[200px]">
            <option value="">全部</option>
            <option value="CLI_PUBLISH">CLI 发布</option>
            <option value="COMPAT_PUBLISH">Compat 发布</option>
            <option value="REVIEW_APPROVE">审核通过</option>
            <option value="REVIEW_REJECT">审核拒绝</option>
            <option value="PROMOTION_APPROVE">提升通过</option>
            <option value="YANK_SKILL_VERSION">版本撤回</option>
          </Select>
          <Input
            placeholder="用户 ID..."
            value={userIdFilter}
            onChange={(e) => setUserIdFilter(e.target.value)}
            className="w-[200px]"
          />
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
          <p className="text-muted-foreground">暂无审计日志</p>
        </Card>
      ) : (
        <>
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>时间</TableHead>
                  <TableHead>操作</TableHead>
                  <TableHead>用户 ID</TableHead>
                  <TableHead>用户名</TableHead>
                  <TableHead>IP 地址</TableHead>
                  <TableHead>详情</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell>{formatDate(log.timestamp)}</TableCell>
                    <TableCell className="font-medium">{log.action}</TableCell>
                    <TableCell>{log.userId || '-'}</TableCell>
                    <TableCell>{log.resourceType || '-'}</TableCell>
                    <TableCell>{log.ipAddress || '-'}</TableCell>
                    <TableCell className="max-w-md truncate">
                      {log.resourceId || '-'}
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
    </div>
  )
}
