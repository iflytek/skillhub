import { useNavigate } from '@tanstack/react-router'
import { SkillCard } from '@/features/skill/skill-card'
import { useMyStars } from '@/shared/hooks/use-skill-queries'
import { Card } from '@/shared/ui/card'

export function MyStarsPage() {
  const navigate = useNavigate()
  const { data: skills, isLoading } = useMyStars()

  if (isLoading) {
    return (
      <div className="space-y-4 animate-fade-up">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="h-32 animate-shimmer rounded-xl" />
        ))}
      </div>
    )
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="text-4xl font-bold font-heading mb-2">我的收藏</h1>
        <p className="text-muted-foreground text-lg">查看你标记过的技能</p>
      </div>

      {!skills || skills.length === 0 ? (
        <Card className="p-12 text-center text-muted-foreground">还没有收藏任何技能</Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {skills.map((skill) => (
            <SkillCard
              key={skill.id}
              skill={skill}
              onClick={() => navigate({ to: '/@$namespace/$slug', params: { namespace: skill.namespace, slug: skill.slug } })}
            />
          ))}
        </div>
      )}
    </div>
  )
}
