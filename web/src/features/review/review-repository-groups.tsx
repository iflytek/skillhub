import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowRight, GitCommitHorizontal, GitFork } from 'lucide-react'
import { reviewApi } from '@/api/client'
import type { ReviewCenterFilters } from './review-center-filters'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Pagination } from '@/shared/components/pagination'
import { formatLocalDateTime } from '@/shared/lib/date-time'

interface Props {
  filters: ReviewCenterFilters
  namespaceId?: number
  enabled: boolean
  page: number
  onGroup: (id: number) => void
  onPage: (page: number) => void
  onReady?: () => void
}

/** A repository card opens a dedicated page; this list never expands into member content. */
export function ReviewRepositoryGroups({ filters, namespaceId, enabled, page, onGroup, onPage, onReady }: Props) {
  const { t, i18n } = useTranslation()
  const params = { status: filters.status, namespaceId, q: filters.q, reviewKind: filters.reviewKind, sortDirection: filters.sortDirection, page, size: 20 }
  const query = useQuery({ queryKey: ['reviews', 'repository-groups', params], queryFn: () => reviewApi.syncGroups(params), enabled })
  useEffect(() => { if (enabled && query.isSuccess) onReady?.() }, [enabled, query.isSuccess, query.data, onReady])
  if (!enabled) return null
  return <section className="space-y-4 [&_button]:min-h-11 sm:[&_button]:min-h-8" aria-busy={query.isFetching} aria-label={t('reviewGroups.title')}>
    <h2 id="review-results" tabIndex={-1} className="text-lg font-semibold">{t('reviewGroups.title')}</h2>
    {query.isLoading ? <div className="h-40 animate-shimmer rounded-lg" aria-label={t('reviewCenter.loading')} />
      : query.isError ? <Card className="space-y-3 p-6" role="alert"><p>{t('reviewCenter.loadFailed')}</p><Button variant="outline" onClick={() => void query.refetch()}>{t('reviewCenter.retry')}</Button></Card>
        : !query.data?.items.length ? <Card className="space-y-3 p-8 text-center"><p>{t('reviewGroups.empty')}</p><p className="text-sm text-muted-foreground">{t('reviewGroups.historyHint')}</p>{page > 0 && <Button variant="outline" onClick={() => onPage(0)}>{t('reviewCenter.firstPage')}</Button>}</Card>
          : <>
            {query.data.items.map(group => <Card key={group.snapshotId} className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0 space-y-2"><h3 className="flex items-start gap-2 font-semibold"><GitFork aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" /><span className="break-all">{group.owner}/{group.repository}</span></h3>
                <p className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground"><span>@{group.namespace}</span><GitCommitHorizontal aria-hidden="true" className="h-4 w-4" /><code>{group.commitSha.slice(0,12)}</code><span>{formatLocalDateTime(group.capturedAt, i18n.language)}</span></p>
                <p className="text-sm text-muted-foreground">{t('reviewGroups.summary', { total: group.total, passed: group.scanPassed, waiting: group.scanWaiting, blocked: group.scanBlocked })}</p>
              </div>
              <Button className="min-h-11 shrink-0 sm:min-h-8" onClick={() => onGroup(group.snapshotId)}>{t('reviewGroups.open')}<ArrowRight className="ml-2 h-4 w-4" /></Button>
            </Card>)}
            <Pagination analyticsId="review.groups.paginate" page={page} totalPages={Math.max(1,Math.ceil(query.data.total / query.data.size))} onPageChange={onPage} />
          </>}
  </section>
}
