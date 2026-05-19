import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { SkillCard } from '@/features/skill/skill-card'
import { Pagination } from '@/shared/components/pagination'
import { useMySubscriptionsPage } from '@/shared/hooks/use-user-queries'
import { Card } from '@/shared/ui/card'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'

const PAGE_SIZE = 12

export function MySubscriptionsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const { data, isLoading } = useMySubscriptionsPage({ page, size: PAGE_SIZE })
  const skills = data?.items ?? []
  const totalPages = data ? Math.max(Math.ceil(data.total / data.size), 1) : 1

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
      <DashboardPageHeader title={t('subscriptions.title')} subtitle={t('subscriptions.subtitle')} />

      {!skills || skills.length === 0 ? (
        <Card className="p-12 text-center text-muted-foreground">{t('subscriptions.empty')}</Card>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {skills.map((skill) => (
              <SkillCard
                key={skill.id}
                skill={skill}
                onClick={() => navigate({ to: `/space/${skill.namespace}/${encodeURIComponent(skill.slug)}` })}
              />
            ))}
          </div>
          {data && data.total > PAGE_SIZE ? (
            <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
          ) : null}
        </>
      )}
    </div>
  )
}
