import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { EmptyState } from '@/shared/components/empty-state'
import { useMyNamespaces } from '@/shared/hooks/use-skill-queries'

export function MyNamespacesPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { data: namespaces, isLoading } = useMyNamespaces()

  const handleNamespaceClick = (slug: string) => {
    navigate({ to: `/space/${slug}` })
  }

  const handleMembersClick = (slug: string, e: React.MouseEvent) => {
    e.stopPropagation()
    navigate({ to: `/dashboard/namespaces/${slug}/members` })
  }

  const handleReviewsClick = (slug: string, e: React.MouseEvent) => {
    e.stopPropagation()
    navigate({ to: `/dashboard/namespaces/${slug}/reviews` })
  }

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
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-4xl font-bold font-heading mb-2">{t('myNamespaces.title')}</h1>
          <p className="text-muted-foreground text-lg">{t('myNamespaces.subtitle')}</p>
        </div>
        <Button disabled>{t('myNamespaces.create')}</Button>
      </div>

      {namespaces && namespaces.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          {namespaces.map((namespace, idx) => (
            <Card
              key={namespace.id}
              className={`p-6 cursor-pointer group animate-fade-up delay-${Math.min(idx + 1, 6)}`}
              onClick={() => handleNamespaceClick(namespace.slug)}
            >
              <div className="space-y-4">
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-3 mb-2">
                      <h3 className="font-semibold font-heading text-lg group-hover:text-primary transition-colors">
                        {namespace.displayName}
                      </h3>
                      <NamespaceBadge
                        type={namespace.type}
                        name={namespace.type === 'GLOBAL' ? t('myNamespaces.typeGlobal') : t('myNamespaces.typeTeam')}
                      />
                    </div>
                    {namespace.description && (
                      <p className="text-sm text-muted-foreground mb-2 leading-relaxed">
                        {namespace.description}
                      </p>
                    )}
                    <div className="text-sm text-muted-foreground font-mono">@{namespace.slug}</div>
                  </div>
                </div>
                <div className="flex gap-3">
                  {namespace.type === 'TEAM' && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={(e) => handleMembersClick(namespace.slug, e)}
                    >
                      {t('myNamespaces.manageMembers')}
                    </Button>
                  )}
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={(e) => handleReviewsClick(namespace.slug, e)}
                  >
                    {t('myNamespaces.reviewTasks')}
                  </Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <EmptyState
          title={t('myNamespaces.emptyTitle')}
          description={t('myNamespaces.emptyDescription')}
          action={<Button disabled>{t('myNamespaces.create')}</Button>}
        />
      )}
    </div>
  )
}
