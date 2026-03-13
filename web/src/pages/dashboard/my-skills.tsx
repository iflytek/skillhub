import { useNavigate } from '@tanstack/react-router'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { EmptyState } from '@/shared/components/empty-state'
import { useMySkills } from '@/shared/hooks/use-skill-queries'

export function MySkillsPage() {
  const navigate = useNavigate()
  const { data: skills, isLoading } = useMySkills()

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${slug}` })
  }

  if (isLoading) {
    return (
      <div className="space-y-4 animate-fade-up">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="h-24 animate-shimmer rounded-xl" />
        ))}
      </div>
    )
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-4xl font-bold font-heading mb-2">我的技能</h1>
          <p className="text-muted-foreground text-lg">管理你发布的技能</p>
        </div>
        <Button size="lg" onClick={() => navigate({ to: '/dashboard/publish' })}>
          发布新技能
        </Button>
      </div>

      {skills && skills.length > 0 ? (
        <div className="grid grid-cols-1 gap-4">
          {skills.map((skill, idx) => (
            <Card
              key={skill.id}
              className={`p-5 cursor-pointer group animate-fade-up delay-${Math.min(idx + 1, 6)}`}
              onClick={() => handleSkillClick(skill.namespace, skill.slug)}
            >
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <h3 className="font-semibold font-heading text-lg mb-1 group-hover:text-primary transition-colors">
                    {skill.displayName}
                  </h3>
                  {skill.summary && (
                    <p className="text-sm text-muted-foreground mb-3 leading-relaxed">{skill.summary}</p>
                  )}
                  <div className="flex items-center gap-4 text-sm text-muted-foreground">
                    <span className="px-2.5 py-0.5 rounded-full bg-secondary/60 text-xs">@{skill.namespace}</span>
                    {skill.latestVersion && (
                      <span className="font-mono text-xs">v{skill.latestVersion}</span>
                    )}
                    <span className="flex items-center gap-1">
                      <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
                      </svg>
                      {skill.downloadCount}
                    </span>
                  </div>
                </div>
                <svg className="w-5 h-5 text-muted-foreground group-hover:text-primary transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <EmptyState
          title="还没有技能"
          description="开始发布你的第一个技能吧"
          action={
            <Button size="lg" onClick={() => navigate({ to: '/dashboard/publish' })}>
              发布技能
            </Button>
          }
        />
      )}
    </div>
  )
}
