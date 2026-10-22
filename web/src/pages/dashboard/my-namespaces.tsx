import { useEffect, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { CreateNamespaceDialog } from '@/features/namespace/create-namespace-dialog'
import { useMyNamespacesPage } from '@/shared/hooks/use-namespace-queries'
export { executeNamespaceAction, resolveNamespaceActionCopy } from '@/features/namespace/namespace-actions'

/** Namespace summaries; management actions live inside the namespace. */
const PAGE_SIZE = 10

export function MyNamespacesPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { hasRole } = useAuth()
  const canCreateNamespace = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')
  const [page, setPage] = useState(0)
  const { data: namespacePage, isLoading } = useMyNamespacesPage({ page, size: PAGE_SIZE })
  const namespaces = namespacePage?.items ?? []
  const totalPages = namespacePage ? Math.max(Math.ceil(namespacePage.total / namespacePage.size), 1) : 1

  useEffect(() => {
    if (page >= totalPages) {
      setPage(Math.max(totalPages - 1, 0))
    }
  }, [page, totalPages])
  const handleNamespaceClick = (slug: string) => {
    navigate({ to: `/dashboard/namespaces/${encodeURIComponent(slug)}` })
  }

  const handleGithubSourcesClick = (slug: string, e: React.MouseEvent) => {
    e.stopPropagation()
    navigate({ to: `/dashboard/namespaces/${encodeURIComponent(slug)}/github-sources` })
  }

  const resolveStatusLabel = (status: string) => {
    if (status === 'FROZEN') {
      return t('namespaceStatus.frozen')
    }
    if (status === 'ARCHIVED') {
      return t('namespaceStatus.archived')
    }
    return t('namespaceStatus.active')
  }

  const resolveStatusClassName = (status: string) => {
    if (status === 'FROZEN') {
      return 'bg-amber-500/10 text-amber-500 border-amber-500/20'
    }
    if (status === 'ARCHIVED') {
      return 'bg-slate-500/10 text-slate-500 border-slate-500/20'
    }
    return 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20'
  }

  const resolveHint = (status: string, type: string) => {
    if (type === 'GLOBAL') {
      return t('myNamespaces.immutableHint')
    }
    if (status === 'FROZEN') {
      return t('myNamespaces.frozenHint')
    }
    if (status === 'ARCHIVED') {
      return t('myNamespaces.archivedHint')
    }
    return t('myNamespaces.activeHint')
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
      <DashboardPageHeader
        title={t('myNamespaces.title')}
        subtitle={t('myNamespaces.subtitle')}
        actions={canCreateNamespace ? (
          <CreateNamespaceDialog>
            <Button data-analytics-id="namespace.create.open">{t('myNamespaces.create')}</Button>
          </CreateNamespaceDialog>
        ) : undefined}
      />

      {namespaces && namespaces.length > 0 ? (
        <>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          {namespaces.map((namespace, idx) => (
            <Card
              key={namespace.id}
              data-testid={`namespace-card-${namespace.slug}`}
              className={`flex h-full flex-col p-5 group animate-fade-up delay-${Math.min(idx + 1, 6)}`}
            >
              <div className="flex h-full flex-col gap-5">
                <div className="flex flex-1 items-start">
                  <div className="flex min-w-0 flex-1 flex-col">
                    <div className="mb-3 flex flex-wrap items-center gap-2">
                      <h3 className="font-semibold font-heading text-lg leading-tight group-hover:text-primary transition-colors">
                        {namespace.displayName}
                      </h3>
                      <NamespaceBadge
                        type={namespace.type}
                        name={namespace.type === 'GLOBAL' ? t('myNamespaces.typeGlobal') : t('myNamespaces.typeTeam')}
                      />
                      <span className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-medium ${resolveStatusClassName(namespace.status)}`}>
                        {resolveStatusLabel(namespace.status)}
                      </span>
                    </div>
                    <p className="mb-3 min-h-10 text-sm leading-relaxed text-muted-foreground">
                      {namespace.description ?? ''}
                    </p>
                    <div className="text-sm font-mono text-muted-foreground">@{namespace.slug}</div>
                    <div className="mt-4 rounded-lg border border-border/50 bg-secondary/40 px-3 py-2.5 text-sm leading-relaxed text-muted-foreground">
                      {resolveHint(namespace.status, namespace.type)}
                    </div>
                    <div className="mt-3 border-t border-border/50 pt-3 text-xs uppercase tracking-[0.18em] text-muted-foreground/80">
                      {t('myNamespaces.roleLabel')}: {namespace.currentUserRole === 'OWNER' ? t('members.roleOwner') : namespace.currentUserRole === 'ADMIN' ? t('members.roleAdmin') : namespace.currentUserRole === 'MEMBER' ? t('members.roleMember') : t('myNamespaces.roleUnknown')}
                    </div>
                  </div>
                </div>
                <div className="flex flex-wrap gap-2 border-t border-border/50 pt-4">
                  <Button data-analytics-id="namespace.management.open" variant="outline" size="sm" onClick={() => handleNamespaceClick(namespace.slug)}>
                    {t('namespaceManagement.enter')}
                  </Button>
                  {namespace.type === 'TEAM' && (namespace.currentUserRole === 'OWNER' || namespace.currentUserRole === 'ADMIN') && (
                    <Button data-analytics-id="github.namespace.open" variant="outline" size="sm" onClick={(e) => handleGithubSourcesClick(namespace.slug, e)}>
                      {t('githubSync.teamLink')}
                    </Button>
                  )}
                </div>
              </div>
            </Card>
          ))}
        </div>
        {totalPages > 1 ? (
          <Pagination analyticsId="namespace.list.paginate" page={page} totalPages={totalPages} onPageChange={setPage} />
        ) : null}
        </>
      ) : (
        <EmptyState
          title={t('myNamespaces.emptyTitle')}
          description={t('myNamespaces.emptyDescription')}
          action={canCreateNamespace ? (
            <CreateNamespaceDialog>
              <Button data-analytics-id="namespace.create.open">{t('myNamespaces.create')}</Button>
            </CreateNamespaceDialog>
          ) : undefined}
        />
      )}

    </div>
  )
}
