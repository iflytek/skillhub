import { useNavigate } from '@tanstack/react-router'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { EmptyState } from '@/shared/components/empty-state'
import { useMyNamespaces } from '@/shared/hooks/use-skill-queries'

export function MyNamespacesPage() {
  const navigate = useNavigate()
  const { data: namespaces, isLoading } = useMyNamespaces()

  const handleNamespaceClick = (slug: string) => {
    navigate({ to: '/@$namespace', params: { namespace: slug } })
  }

  const handleMembersClick = (slug: string, e: React.MouseEvent) => {
    e.stopPropagation()
    navigate({ to: '/dashboard/namespaces/$slug/members', params: { slug } })
  }

  if (isLoading) {
    return <div className="animate-pulse">加载中...</div>
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold mb-2">我的命名空间</h1>
          <p className="text-muted-foreground">管理你的命名空间和团队</p>
        </div>
        <Button disabled>创建命名空间</Button>
      </div>

      {namespaces && namespaces.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {namespaces.map((namespace) => (
            <Card
              key={namespace.id}
              className="p-6 hover:shadow-md transition-shadow cursor-pointer"
              onClick={() => handleNamespaceClick(namespace.slug)}
            >
              <div className="space-y-3">
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-2">
                      <h3 className="font-semibold text-lg">{namespace.displayName}</h3>
                      <NamespaceBadge
                        type={namespace.type}
                        name={namespace.type === 'GLOBAL' ? '全局' : '团队'}
                      />
                    </div>
                    {namespace.description && (
                      <p className="text-sm text-muted-foreground mb-2">
                        {namespace.description}
                      </p>
                    )}
                    <div className="text-sm text-muted-foreground">@{namespace.slug}</div>
                  </div>
                </div>
                {namespace.type === 'TEAM' && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={(e) => handleMembersClick(namespace.slug, e)}
                  >
                    管理成员
                  </Button>
                )}
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <EmptyState
          title="还没有命名空间"
          description="创建一个命名空间来组织你的技能"
          action={<Button disabled>创建命名空间</Button>}
        />
      )}
    </div>
  )
}
