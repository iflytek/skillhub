import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { LayoutGrid } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { useMyCollections } from '@/shared/hooks/use-collection-queries'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { buttonVariants } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

/**
 * Authenticated list of collections the user owns or contributes to.
 */
export function CollectionsListPage() {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const { data, isLoading, error } = useMyCollections({ page: 0, size: 48 })

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <DashboardPageHeader
        title={t('collections.listTitle')}
        subtitle={t('collections.listSubtitle')}
        actions={(
          <Link to="/dashboard/collections/new" className={buttonVariants()}>
            {t('collections.create')}
          </Link>
        )}
      />

      {isLoading ? (
        <p className="text-sm text-muted-foreground">{t('collections.loading')}</p>
      ) : null}

      {error ? (
        <p className="text-sm text-destructive">{t('collections.loadError')}</p>
      ) : null}

      {!isLoading && !error && (data?.items.length ?? 0) === 0 ? (
        <Card>
          <CardHeader>
            <CardTitle>{t('collections.emptyTitle')}</CardTitle>
            <CardDescription>{t('collections.emptyDescription')}</CardDescription>
          </CardHeader>
          <CardContent>
            <Link to="/dashboard/collections/new" className={buttonVariants()}>
              {t('collections.create')}
            </Link>
          </CardContent>
        </Card>
      ) : null}

      {data && data.items.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          {data.items.map((item) => {
            const id = item.id
            if (id == null) {
              return null
            }
            const isOwner = user?.userId != null && item.ownerId === user.userId
            const roleLabel = isOwner ? t('collections.roleOwner') : t('collections.roleContributor')
            const updated = item.updatedAt
              ? formatLocalDateTime(item.updatedAt, i18n.language)
              : null
            return (
              <Card key={String(id)}>
                <CardHeader className="space-y-2">
                  <div className="flex items-start justify-between gap-2">
                    <CardTitle className="text-lg leading-snug">{item.title ?? t('collections.untitled')}</CardTitle>
                    <LayoutGrid className="h-5 w-5 shrink-0 text-muted-foreground" aria-hidden />
                  </div>
                  <CardDescription className="flex flex-wrap gap-2 text-xs">
                    <span
                      className={`rounded-full border px-2 py-0.5 font-medium ${
                        item.visibility === 'PUBLIC'
                          ? 'border-emerald-500/40 text-emerald-700 dark:text-emerald-400'
                          : 'border-border text-muted-foreground'
                      }`}
                    >
                      {item.visibility === 'PUBLIC' ? t('collections.visibilityPublic') : t('collections.visibilityPrivate')}
                    </span>
                    <span className="rounded-full border border-border px-2 py-0.5 text-muted-foreground">{roleLabel}</span>
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-3">
                  {updated ? (
                    <p className="text-xs text-muted-foreground">{t('collections.updatedAt', { date: updated })}</p>
                  ) : null}
                  <Link
                    to="/dashboard/collections/$collectionId/edit"
                    params={{ collectionId: String(id) }}
                    className="inline-flex text-sm font-semibold text-primary hover:underline"
                  >
                    {t('collections.manage')}
                  </Link>
                </CardContent>
              </Card>
            )
          })}
        </div>
      ) : null}
    </div>
  )
}
