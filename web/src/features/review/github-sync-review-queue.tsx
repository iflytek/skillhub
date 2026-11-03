import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import type { GithubSyncReviewQueueItem } from '@/api/types'
import { useGithubSyncReviewQueue } from '@/features/review/use-github-sync-review-queue'
import { buildNamespaceReviewDetailPath } from '@/features/review/review-paths'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Card } from '@/shared/ui/card'
import { Pagination } from '@/shared/components/pagination'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUPERSEDED'
const REVIEW_STATUSES: ReviewStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'SUPERSEDED']
const initialPages = (): Record<ReviewStatus, number> => ({
  PENDING: 0, APPROVED: 0, REJECTED: 0, SUPERSEDED: 0,
})

function groupByRepositoryRun(items: GithubSyncReviewQueueItem[]) {
  const groups = new Map<string, GithubSyncReviewQueueItem[]>()
  for (const item of items) {
    const key = `${item.review.namespace}/${item.repositoryId}/${item.runId}`
    const group = groups.get(key)
    if (group) group.push(item)
    else groups.set(key, [item])
  }
  return [...groups.values()]
}

export function GithubSyncReviewQueue({ namespaceId, namespaceSlug, enabled = true }: {
  namespaceId?: number
  namespaceSlug?: string
  enabled?: boolean
}) {
  const { t, i18n } = useTranslation()
  const [status, setStatus] = useState<ReviewStatus>('PENDING')
  const [sortDirection, setSortDirection] = useState<'ASC' | 'DESC'>('DESC')
  const [pages, setPages] = useState<Record<ReviewStatus, number>>(initialPages)
  const query = useGithubSyncReviewQueue(status, namespaceId, pages[status], sortDirection, enabled)
  const totalPages = query.data && query.data.size > 0 ? Math.ceil(query.data.total / query.data.size) : 0

  return (
    <Card className="space-y-5 p-5 md:p-7">
      <div>
        <h2 className="text-xl font-bold font-heading">{t('reviews.syncTitle')}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{t('reviews.syncDescription')}</p>
      </div>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div role="group" aria-label={t('reviews.syncTitle')} className="flex flex-wrap gap-1 rounded-lg bg-muted p-1">
          {REVIEW_STATUSES.map((value) => <button data-analytics-id="review.sync.status" key={value}
            type="button" aria-pressed={status === value} onClick={() => setStatus(value)}
            className="rounded-md px-3 py-2 text-sm aria-pressed:bg-card aria-pressed:font-semibold aria-pressed:shadow-sm">
            {t(value === 'PENDING' ? 'reviews.tabPending' : value === 'APPROVED'
              ? 'reviews.tabApproved' : value === 'REJECTED' ? 'reviews.tabRejected' : 'reviews.tabSuperseded')}
          </button>)}
        </div>
        <div className="w-full max-w-48">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">{t('reviews.sortLabel')}</p>
          <Select value={sortDirection} onValueChange={(value) => {
            setSortDirection(value as 'ASC' | 'DESC')
            setPages(initialPages())
          }}>
            <SelectTrigger data-analytics-id="review.sync.sort.interact"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem data-analytics-id="review.sync.sort" value="DESC">{t('reviews.sortNewest')}</SelectItem>
              <SelectItem data-analytics-id="review.sync.sort" value="ASC">{t('reviews.sortOldest')}</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
      {query.isLoading ? <div className="h-32 animate-shimmer rounded-lg" />
        : query.isError ? <p className="text-sm text-destructive">{t('reviews.syncError')}</p>
          : !query.data?.items.length ? <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">{t('reviews.syncEmpty')}</p>
            : <div className="space-y-5">
              {groupByRepositoryRun(query.data.items).map((group) => {
                const first = group[0]
                return <section key={`${first.review.namespace}/${first.repositoryId}/${first.runId}`} className="overflow-hidden rounded-xl border">
                  <div className="bg-muted/30 px-4 py-3">
                    <h3 className="font-semibold break-all">{first.ownerLogin && first.repositoryName
                      ? `${first.ownerLogin}/${first.repositoryName}` : `#${first.repositoryId}`}</h3>
                    <p className="mt-1 font-mono text-xs text-muted-foreground break-all">
                      {first.review.namespace} · {t('reviews.syncRun')} {first.runId} · {t('reviews.syncGroupCount', { count: group.length })}
                    </p>
                  </div>
                  <div className="divide-y">
                    {group.map((item) => <div key={item.review.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
                      <div className="min-w-0">
                        <p className="font-medium break-all">@{item.review.namespace}/{item.review.skillSlug}</p>
                        <p className="mt-1 font-mono text-xs text-muted-foreground break-all">{item.skillPath} · {item.commitSha.slice(0, 12)}</p>
                        <p className="mt-1 text-xs text-muted-foreground">
                          {formatLocalDateTime(item.review.reviewedAt ?? item.review.submittedAt, i18n.language)}
                        </p>
                      </div>
                      <Link data-analytics-id="review.detail.open" to={namespaceSlug
                        ? buildNamespaceReviewDetailPath(namespaceSlug, item.review.id)
                        : `/dashboard/reviews/${item.review.id}`}
                        className="inline-flex shrink-0 items-center rounded-md border px-3 py-2 text-sm hover:bg-accent">
                        {t('reviews.syncOpenReview')}
                      </Link>
                    </div>)}
                  </div>
                </section>
              })}
              {totalPages > 1 && <Pagination analyticsId="review.sync.paginate" page={pages[status]} totalPages={totalPages}
                onPageChange={(page) => setPages((current) => ({ ...current, [status]: page }))} />}
              <p className="text-xs text-muted-foreground">{t('reviews.pageSummary', { total: query.data.total, page: pages[status] + 1 })}</p>
            </div>}
    </Card>
  )
}
