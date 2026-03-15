import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { SkillCard } from '@/features/skill/skill-card'
import { useMyStars } from '@/shared/hooks/use-skill-queries'
import { Card } from '@/shared/ui/card'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'

export function MyStarsPage() {
  const { t } = useTranslation()
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
      <DashboardPageHeader title={t('stars.title')} subtitle={t('stars.subtitle')} />

      {!skills || skills.length === 0 ? (
        <Card className="p-12 text-center text-muted-foreground">{t('stars.empty')}</Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {skills.map((skill) => (
            <SkillCard
              key={skill.id}
              skill={skill}
              onClick={() => navigate({ to: `/space/${skill.namespace}/${skill.slug}` })}
            />
          ))}
        </div>
      )}
    </div>
  )
}
