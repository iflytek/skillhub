import { useQuery } from '@tanstack/react-query'
import { Link, useLocation, useNavigate } from '@tanstack/react-router'
import { ArrowLeft, ArrowUpRight, GitBranch } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { fetchJson, WEB_API_PREFIX } from '@/api/client'
import type { components } from '@/api/generated/schema'
import { useAuth } from '@/features/auth/use-auth'
import { Pagination } from '@/shared/components/pagination'
import { Button, buttonVariants } from '@/shared/ui/button'
import { ApiError } from '@/shared/lib/api-error'

interface Props {
  repositoryId?: string
  page: number
  size: number
}

export function GithubRepositoryResults({ repositoryId, page, size }: Props) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { pathname, searchStr } = useLocation()
  const { user, isLoading: authLoading } = useAuth()
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  const endpoint = `${WEB_API_PREFIX}/github-repositories`
  const list = useQuery({
    queryKey: ['skills', 'github-repositories', user?.userId, page, size],
    queryFn: ({ signal }) => fetchJson<components['schemas']['GithubCatalogRepositoryPage']>(`${endpoint}?${params}`, { signal }),
    enabled: !authLoading && !repositoryId,
  })
  const detail = useQuery({
    queryKey: ['skills', 'github-repository', user?.userId, repositoryId, page, size],
    queryFn: ({ signal }) => fetchJson<components['schemas']['GithubCatalogRepositoryDetail']>(`${endpoint}/${repositoryId}?${params}`, { signal }),
    enabled: !authLoading && !!repositoryId,
    retry: (count, error) => (!(error instanceof ApiError) || error.status !== 404) && count < 2,
  })
  const query = repositoryId ? detail : list
  const repositories = list.isSuccess ? list.data.items : []
  const members = detail.isSuccess ? detail.data.items : []
  const repository = detail.isSuccess ? detail.data.repository : undefined
  const total = repositoryId ? (repository?.memberCount ?? 0) : (list.data?.total ?? 0)
  const notFound = query.error instanceof ApiError && query.error.status === 404

  const search = {
    q: '',
    sort: 'newest',
    view: 'repositories' as const,
    page: 0,
  }

  return <div className="space-y-4">
    {repositoryId && <Link to="/github" search={search} className={buttonVariants({ variant: 'ghost', size: 'sm' })}>
      <ArrowLeft className="h-4 w-4" aria-hidden="true" />{t('githubCatalog.backToRepositories')}
    </Link>}
    <div className="border-b border-[var(--sync-line)] pb-4">
      <h2 className="break-words text-xl font-semibold text-[var(--sync-ink)]">
        {repository ? `${repository.owner}/${repository.name}` : t('githubCatalog.repositories')}
      </h2>
      {repository && <p className="mt-1 text-sm text-muted-foreground">
        {t('githubCatalog.publishedCount')} · {repository.memberCount}
      </p>}
    </div>
    {authLoading || query.isLoading ? <p role="status" className="py-10 text-center text-sm text-muted-foreground">{t('githubCatalog.repositoryLoading')}</p> : null}
    {query.isError ? <div role="alert" className="flex flex-wrap items-center gap-3 py-6 text-sm text-muted-foreground">
      {t(notFound ? 'githubCatalog.repositoryUnavailable' : 'githubCatalog.repositoryLoadFailed')}
      {!notFound && <Button variant="outline" size="sm" onClick={() => void query.refetch()}>{t('githubCatalog.repositoryRetry')}</Button>}
    </div> : null}
    {query.isSuccess && !repositories.length && !members.length ? <div className="space-y-3 py-8 text-sm text-muted-foreground">
      <p>{t('githubCatalog.repositoryEmpty')}</p>
      {page > 0 && <Link to="/github" search={{ ...search, repositoryId }}
        className={buttonVariants({ variant: 'outline', size: 'sm' })}>
        {t('githubCatalog.repositoryFirstPage')}
      </Link>}
    </div> : null}
    {!repositoryId && repositories.length > 0 ? <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {repositories.map(item => <li key={item.repositoryId} className="min-w-0">
        <Link to="/github" search={{ ...search, repositoryId: item.repositoryId }}
          className="flex h-full min-w-0 items-start gap-3 rounded-lg border border-[var(--sync-line)] bg-background p-4 hover:bg-[var(--sync-panel)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring">
          <GitBranch className="mt-0.5 h-4 w-4 shrink-0 text-[var(--sync-blue)]" aria-hidden="true" />
          <div className="min-w-0 flex-1">
            <h3 className="break-words font-medium text-[var(--sync-ink)]">{item.owner}/{item.name}</h3>
            <p className="mt-1 text-sm text-muted-foreground">{t('githubCatalog.publishedCount')} · {item.memberCount}</p>
          </div>
          <ArrowUpRight className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
        </Link>
      </li>)}
    </ul> : null}
    {repositoryId && members.length > 0 ? <ul className="divide-y divide-[var(--sync-line)]">
      {members.map(member => <li key={member.id} className="flex flex-wrap items-start justify-between gap-3 py-4 sm:items-center">
        <div className="min-w-0 flex-1 basis-56 space-y-1">
          <h3 className="break-words font-medium text-[var(--sync-ink)]">{member.displayName}</h3>
          <p className="line-clamp-2 text-sm text-muted-foreground">{member.summary || t('githubCatalog.noSummary')}</p>
          <p className="text-xs text-muted-foreground">{member.syncRevision
            ? t('review.githubRevisionValue', { count: member.syncRevision })
            : `v${member.version}`}</p>
        </div>
        <Link to="/space/$namespace/$slug" params={{ namespace: 'github', slug: member.slug }}
          className={buttonVariants({ variant: 'outline', size: 'sm' })}
          search={{ returnTo: `${pathname}${searchStr}` }}>
          {t('githubCatalog.viewSkill')} <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
        </Link>
      </li>)}
    </ul> : null}
    {query.isSuccess && total > size ? <Pagination page={page} totalPages={Math.ceil(total / size)}
      mobileCompact onPageChange={next => void navigate({ to: '/github', search: { ...search, repositoryId, page: next } })} /> : null}
  </div>
}
