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
        <h2 className="text-2xl font-bold font-heading mb-2">命名空间不存在</h2>
      </div>
    )
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <NamespaceHeader namespace={namespace} />

      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-bold font-heading">成员管理</h2>
          <Button disabled>添加成员</Button>
        </div>

        {isLoadingMembers ? (
          <div className="space-y-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="h-14 animate-shimmer rounded-lg" />
            ))}
          </div>
        ) : members && members.length > 0 ? (
          <Card className="overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-border/40">
                    <th className="text-left p-4 font-medium font-heading text-sm text-muted-foreground">用户 ID</th>
                    <th className="text-left p-4 font-medium font-heading text-sm text-muted-foreground">角色</th>
                    <th className="text-left p-4 font-medium font-heading text-sm text-muted-foreground">加入时间</th>
                    <th className="text-right p-4 font-medium font-heading text-sm text-muted-foreground">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {members.map((member) => (
                    <tr key={member.id} className="border-b border-border/40 last:border-b-0 hover:bg-secondary/30 transition-colors">
                      <td className="p-4 font-medium">{member.userId}</td>
                      <td className="p-4">
                        <span className="inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium bg-accent/10 text-accent border border-accent/20">
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
