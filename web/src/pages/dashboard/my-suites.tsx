import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Boxes } from 'lucide-react'
import { useMySuites } from '@/shared/hooks/use-suite-queries'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { suiteStatusLabel } from '@/features/suite/suite-labels'

const PAGE_SIZE = 12

export function MySuitesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const { data, isLoading } = useMySuites(query.trim(), page, PAGE_SIZE)

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
