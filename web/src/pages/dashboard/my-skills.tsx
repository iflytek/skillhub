import { useNavigate } from '@tanstack/react-router'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { EmptyState } from '@/shared/components/empty-state'
import { useMySkills } from '@/shared/hooks/use-skill-queries'

export function MySkillsPage() {
  const navigate = useNavigate()
  const { data: skills, isLoading } = useMySkills()

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: '/@$namespace/$slug', params: { namespace, slug } })
  }

  if (isLoading) {
    return <div className="animate-pulse">加载中...</div>
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold mb-2">我的技能</h1>
          <p className="text-muted-foreground">管理你发布的技能</p>
        </div>
        <Button onClick={() => navigate({ to: '/dashboard/publish' })}>
          发布新技能
        </Button>
      </div>

      {skills && skills.length > 0 ? (
        <div className="grid grid-cols-1 gap-4">
          {skills.map((skill) => (
            <Card
              key={skill.id}
              className="p-4 hover:shadow-md transition-shadow cursor-pointer"
              onClick={() => handleSkillClick(skill.namespace, skill.slug)}
            >
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <h3 className="font-semibold text-lg mb-1">{skill.displayName}</h3>
                  {skill.summary && (
                    <p className="text-sm text-muted-foreground mb-2">{skill.summary}</p>
                  )}
                  <div className="flex items-center gap-4 text-sm text-muted-foreground">
                    <span>@{skill.namespace}</span>
                    {skill.latestVersion && <span>v{skill.latestVersion}</span>}
                    <span>{skill.downloadCount} 下载</span>
                  </div>
                </div>
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <EmptyState
          title="还没有技能"
          description="开始发布你的第一个技能吧"
          action={
            <Button onClick={() => navigate({ to: '/dashboard/publish' })}>
              发布技能
            </Button>
          }
        />
      )}
    </div>
  )
}
