import { ArrowLeft } from 'lucide-react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { cn } from '@/shared/lib/utils'

export function NamespaceManagementBack() {
  const { t } = useTranslation()
  return (
    <Link data-analytics-id="namespace.list.open" to="/dashboard/namespaces" className="inline-flex items-center gap-2 rounded-md px-2 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
      <ArrowLeft className="h-4 w-4" aria-hidden="true" />
      {t('namespaceManagement.back')}
    </Link>
  )
}

export function NamespaceManagementNav({ slug, active }: { slug: string; active: 'skills' | 'members' | 'reviews' | 'settings' }) {
  const { t } = useTranslation()
  const { hasRole } = useAuth()
  const { data: namespaces } = useMyNamespaces()
  const namespace = namespaces?.find((item) => item.slug === slug)
  const canReview = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')
    || (namespace?.type === 'TEAM' && (namespace.currentUserRole === 'OWNER' || namespace.currentUserRole === 'ADMIN'))
  const tabs = [
    { key: 'skills', path: '/dashboard/namespaces/$slug', analyticsId: 'namespace.skills.open', visible: true },
    { key: 'members', path: '/dashboard/namespaces/$slug/members', analyticsId: 'namespace.members.open', visible: namespace?.type === 'TEAM' && !!namespace.currentUserRole },
    { key: 'reviews', path: '/dashboard/namespaces/$slug/reviews', analyticsId: 'namespace.reviews.open', visible: canReview },
    { key: 'settings', path: '/dashboard/namespaces/$slug/settings', analyticsId: 'namespace.settings.open', visible: !!namespace },
  ] as const
  return (
    <div className="space-y-3">
      <nav aria-label={t('namespaceManagement.navigation')} className="flex gap-1 overflow-x-auto border-b border-border">
        {tabs.filter((tab) => tab.visible).map((tab) => (
          <Link data-analytics-id={tab.analyticsId} key={tab.key} to={tab.path} params={{ slug }}
            aria-current={active === tab.key ? 'page' : undefined}
            className={cn('shrink-0 border-b-2 px-4 py-3 text-sm font-medium transition-colors', active === tab.key ? 'border-primary text-primary' : 'border-transparent text-muted-foreground hover:text-foreground')}>
            {t(`namespaceManagement.${tab.key}`)}
          </Link>
        ))}
      </nav>
    </div>
  )
}
