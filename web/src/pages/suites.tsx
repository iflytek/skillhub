import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Boxes } from 'lucide-react'
import { ResourceCard } from '@/features/suite/resource-card'
import { useResourceSearch } from '@/shared/hooks/use-suite-queries'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

const PAGE_SIZE = 12

export function SuitesPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const { data, isLoading } = useResourceSearch({
    q: query.trim() || undefined,
    resourceType: 'SUITE',
    sort: 'newest',
    page,
    size: PAGE_SIZE,
  })

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <div className="mb-2 inline-flex items-center gap-2 text-sm font-medium text-primary">
            <Boxes className="h-4 w-4" /> {t('suite.listTitle')}
          </div>
          <h1 className="text-4xl font-bold">{t('suite.listTitle')}</h1>
          <p className="mt-2 text-muted-foreground">{t('suite.listDescription')}</p>
        </div>
        <Button onClick={() => navigate({ to: '/dashboard/suites/new' })}>{t('suite.create')}</Button>
      </div>

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
        <SkeletonList count={PAGE_SIZE} />
      ) : data?.items.length ? (
        <>
          <div className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3">
            {data.items.map((suite) => (
              <ResourceCard
                key={suite.id}
                resource={suite}
                onClick={() => navigate({
                  to: `/suite/${suite.namespace}/${encodeURIComponent(suite.slug)}`,
                })}
              />
            ))}
          </div>
          <Pagination
            page={page}
            totalPages={Math.max(1, Math.ceil(data.total / data.size))}
            onPageChange={setPage}
          />
        </>
      ) : (
        <EmptyState title={t('suite.emptyTitle')} description={t('suite.emptyDescription')} />
      )}
    </div>
  )
}
