import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { AlertTriangle, Boxes, Loader2 } from 'lucide-react'
import { useActiveSuiteBundleOperations, useMySuites } from '@/shared/hooks/use-suite-queries'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { suiteStatusLabel } from '@/features/suite/suite-labels'
import { rememberSuiteBundleOperation } from '@/features/suite/suite-bundle-import'

const PAGE_SIZE = 12

export function MySuitesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const { data, isLoading } = useMySuites(query.trim(), page, PAGE_SIZE)
  const { data: activeOperations, isLoading: isLoadingOperations } = useActiveSuiteBundleOperations()

  const continueOperation = (operation: NonNullable<typeof activeOperations>[number]) => {
    rememberSuiteBundleOperation(operation.mode, operation.targetCoordinate, operation.operationId)
    const coordinate = operation.targetCoordinate.match(/^@([^/]+)\/(.+)$/)
    if (operation.mode === 'UPDATE' && coordinate) {
      navigate({
        to: `/dashboard/suites/${coordinate[1]}/${encodeURIComponent(coordinate[2])}/new-version`,
        search: { sourceVersion: undefined },
      })
      return
    }
    navigate({ to: '/dashboard/suites/new' })
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader
        title={t('suite.myTitle')}
        subtitle={t('suite.myDescription')}
        actions={<Button onClick={() => navigate({ to: '/dashboard/suites/new' })}>{t('suite.create')}</Button>}
      />
      <Input
        className="max-w-xl"
        value={query}
        placeholder={t('suite.searchPlaceholder')}
        onChange={(event) => {
          setQuery(event.target.value)
          setPage(0)
        }}
      />
      {isLoadingOperations ? (
        <div className="h-28 animate-shimmer rounded-xl" />
      ) : activeOperations?.length ? (
        <section className="space-y-3" aria-labelledby="active-suite-operations-title">
          <div>
            <h2 id="active-suite-operations-title" className="text-lg font-semibold">
              {t('suite.bundle.activeTitle')}
            </h2>
            <p className="text-sm text-muted-foreground">{t('suite.bundle.activeDescription')}</p>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            {activeOperations.map(operation => (
              <Card key={operation.operationId} className="p-5">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <p className="truncate font-mono text-sm font-semibold">
                      {operation.targetCoordinate}@{operation.targetVersion}
                    </p>
                    <p className="mt-2 text-sm text-muted-foreground">
                      {t(`suite.bundle.status.${operation.status}`)}
                    </p>
                  </div>
                  {operation.status === 'BLOCKED_RETRYABLE' ? (
                    <AlertTriangle className="h-5 w-5 shrink-0 text-amber-600" aria-hidden="true" />
                  ) : (
                    <Loader2 className="h-5 w-5 shrink-0 animate-spin text-primary" aria-hidden="true" />
                  )}
                </div>
                <p className="mt-3 text-xs text-muted-foreground">
                  {t('suite.bundle.memberProgress', {
                    completed: operation.completedMembers,
                    total: operation.totalMembers,
                    waiting: operation.waitingMembers,
                  })}
                </p>
                <div className="mt-4">
                  <Button variant="outline" size="sm" onClick={() => continueOperation(operation)}>
                    {t('suite.bundle.continueOperation')}
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        </section>
      ) : null}
      {isLoading ? (
        <div className="h-40 animate-shimmer rounded-xl" />
      ) : data?.items.length ? (
        <>
          <div className="grid gap-4 md:grid-cols-2">
            {data.items.map((suite) => (
              <Card key={suite.id} className="p-5">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <p className="flex items-center gap-2 font-semibold"><Boxes className="h-4 w-4" />{suite.displayName}</p>
                    <p className="mt-1 truncate font-mono text-xs text-muted-foreground">@{suite.namespace}/{suite.slug}@{suite.version}</p>
                  </div>
                  <span className="rounded-full bg-secondary px-2 py-1 text-xs">{suiteStatusLabel(t, suite.versionStatus)}</span>
                </div>
                <p className="mt-3 line-clamp-2 min-h-10 text-sm text-muted-foreground">{suite.summary || t('suite.noSummary')}</p>
                <div className="mt-4 flex gap-2">
                  <Button variant="outline" size="sm" onClick={() => navigate({
                    to: `/suite/${suite.namespace}/${encodeURIComponent(suite.slug)}`,
                    search: { version: suite.version },
                  })}>{t('suite.view')}</Button>
                </div>
              </Card>
            ))}
          </div>
          <Pagination
            page={page}
            totalPages={Math.max(1, Math.ceil(data.total / data.size))}
            onPageChange={setPage}
          />
        </>
      ) : (
        <EmptyState title={t('suite.myEmpty')} description={t('suite.myEmptyDescription')} />
      )}
    </div>
  )
}
