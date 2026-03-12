import { useParams } from '@tanstack/react-router'
import { NamespaceHeader } from '@/features/namespace/namespace-header'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { useNamespaceDetail, useNamespaceMembers } from '@/shared/hooks/use-skill-queries'

export function NamespaceMembersPage() {
  const { slug } = useParams({ from: '/dashboard/namespaces/$slug/members' })

  const { data: namespace, isLoading: isLoadingNamespace } = useNamespaceDetail(slug)
  const { data: members, isLoading: isLoadingMembers } = useNamespaceMembers(slug)

  if (isLoadingNamespace) {
    return <div className="animate-pulse">加载中...</div>
  }

  if (!namespace) {
    return <div>命名空间不存在</div>
  }

  return (
    <div className="space-y-6">
      <NamespaceHeader namespace={namespace} />

      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold">成员管理</h2>
          <Button disabled>添加成员</Button>
        </div>

        {isLoadingMembers ? (
          <div className="animate-pulse">加载中...</div>
        ) : members && members.length > 0 ? (
          <Card>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b">
                    <th className="text-left p-4 font-medium">用户 ID</th>
                    <th className="text-left p-4 font-medium">角色</th>
                    <th className="text-left p-4 font-medium">加入时间</th>
                    <th className="text-right p-4 font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {members.map((member) => (
                    <tr key={member.id} className="border-b last:border-b-0">
                      <td className="p-4">{member.userId}</td>
                      <td className="p-4">
                        <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium bg-blue-100 text-blue-800">
                          {member.role}
                        </span>
                      </td>
                      <td className="p-4 text-sm text-muted-foreground">
                        {new Date(member.createdAt).toLocaleDateString('zh-CN')}
                      </td>
                      <td className="p-4 text-right">
                        <Button variant="destructive" size="sm" disabled>
                          移除
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        ) : (
          <Card className="p-6 text-center text-muted-foreground">
            暂无成员
          </Card>
        )}
      </div>
    </div>
  )
}
