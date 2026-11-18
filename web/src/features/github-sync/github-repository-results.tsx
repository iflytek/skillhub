import { useEffect, useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useLocation, useNavigate } from '@tanstack/react-router'
import { ArrowLeft, ArrowRight, ArrowUpRight, Github, Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { fetchJson, WEB_API_PREFIX } from '@/api/client'
import type { components } from '@/api/generated/schema'
import { useAuth } from '@/features/auth/use-auth'
import { SkillAvatar } from '@/features/skill/skill-avatar'
import { Pagination } from '@/shared/components/pagination'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { ApiError } from '@/shared/lib/api-error'
import { MAX_SEARCH_QUERY_LENGTH } from '@/shared/lib/search-query'

interface Props {
  repositoryId?: string
  repositoryPage?: number
  memberQuery?: string
  q: string
  sort: string
  page: number
  size: number
}

export function GithubRepositoryResults({ repositoryId, repositoryPage, memberQuery, q, sort, page, size }: Props) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { pathname, searchStr } = useLocation()
  const { user, isLoading: authLoading } = useAuth()
  const activeQuery = repositoryId ? (memberQuery ?? '') : q
  const [input, setInput] = useState(activeQuery)
  useEffect(() => setInput(activeQuery), [activeQuery])

  const params = new URLSearchParams({ page: String(page), size: String(size), q: activeQuery })
  const endpoint = `${WEB_API_PREFIX}/github-repositories`
  const list = useQuery({
    queryKey: ['skills', 'github-repositories', user?.userId, q, page, size],
    queryFn: ({ signal }) => fetchJson<components['schemas']['GithubCatalogRepositoryPage']>(`${endpoint}?${params}`, { signal }),
    enabled: !authLoading && !repositoryId,
  })
  const detail = useQuery({
    queryKey: ['skills', 'github-repository', user?.userId, repositoryId, memberQuery, page, size],
    queryFn: ({ signal }) => fetchJson<components['schemas']['GithubCatalogRepositoryDetail']>(`${endpoint}/${repositoryId}?${params}`, { signal }),
    enabled: !authLoading && !!repositoryId,
    retry: (count, error) => (!(error instanceof ApiError) || error.status !== 404) && count < 2,
  })
  const query = repositoryId ? detail : list
  const repository = detail.isSuccess ? detail.data.repository : undefined
  const repositories = list.isSuccess ? list.data.items : []
  const members = detail.isSuccess ? detail.data.items : []
  const total = query.data?.total ?? 0
  const notFound = query.error instanceof ApiError && query.error.status === 404
  const listSearch = { q, sort, view: 'repositories' as const, page: repositoryPage ?? 0 }
  const currentSearch = { ...listSearch, repositoryId, repositoryPage, memberQuery, page }
  const submitSearch = (text: string) => void navigate({
    to: '/github',
    search: {
      ...currentSearch,
      q: repositoryId ? q : text,
      memberQuery: repositoryId ? (text || undefined) : undefined,
      page: 0,
    },
  })
  const searchLabel = t(repositoryId ? 'githubCatalog.searchMembers' : 'githubCatalog.searchRepositories')
  const searchForm = <form className="flex flex-wrap items-center gap-2" onSubmit={(event: FormEvent) => {
    event.preventDefault()
    submitSearch(input.trim())
  }}>
    <div className="relative min-w-36 max-w-xl flex-1">
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
      <Input className="pl-9" value={input} maxLength={MAX_SEARCH_QUERY_LENGTH} aria-label={searchLabel}
        placeholder={searchLabel} onChange={event => setInput(event.target.value)} />
    </div>
    <Button type="submit" variant="outline">{t('githubCatalog.search')}</Button>
  </form>

  return <div className="space-y-4">
    {repositoryId ? <Link to="/github" search={listSearch} className={buttonVariants({ variant: 'ghost', size: 'sm', className: '-ml-2.5' })}>
      <ArrowLeft className="size-4" aria-hidden="true" />{t('githubCatalog.backToRepositories')}
    </Link> : <div className="rounded-lg border border-border bg-muted/30 p-3">{searchForm}</div>}
    {repository && <header className="flex flex-wrap items-end justify-between gap-3 border-b border-border pb-4">
      <div className="min-w-0 space-y-3">
        <span className="badge-soft badge-soft-blue inline-flex items-center gap-1.5"><Github className="size-3.5" aria-hidden="true" />{t('githubCatalog.repositorySource')}</span>
        <div className="flex items-center gap-3">
          <SkillAvatar name={repository.name} seed={repository.repositoryId} />
          <div className="min-w-0">
            <h1 className="break-words font-heading text-2xl font-bold [overflow-wrap:anywhere] sm:text-3xl">{repository.name}</h1>
            <p className="break-all font-mono text-xs text-muted-foreground">{repository.owner}/{repository.name}</p>
          </div>
        </div>
      </div>
      <a className={buttonVariants({ variant: 'outline' })} target="_blank" rel="noreferrer"
        href={`https://github.com/${encodeURIComponent(repository.owner)}/${encodeURIComponent(repository.name)}`}>
        {t('githubCatalog.openSourceRepository')} <ArrowUpRight className="size-4" aria-hidden="true" />
      </a>
    </header>}
    {authLoading || query.isLoading ? <p role="status" className="py-10 text-center text-sm text-muted-foreground">{t('githubCatalog.repositoryLoading')}</p> : null}
    {query.isError ? <Card role="alert" className="flex flex-wrap items-center gap-3 p-4 text-sm text-muted-foreground">
      {t(notFound ? 'githubCatalog.repositoryUnavailable' : 'githubCatalog.repositoryLoadFailed')}
      {!notFound && <Button variant="outline" size="sm" onClick={() => void query.refetch()}>{t('githubCatalog.repositoryRetry')}</Button>}
    </Card> : null}
    {!repositoryId && query.isSuccess ? <>
      <p className="text-sm text-muted-foreground">{t('githubCatalog.repositoryCount', { count: total })}</p>
      {repositories.length ? <ul className="grid gap-4 md:grid-cols-2">
        {repositories.map(item => <li key={item.repositoryId} className="min-w-0">
          <Card className="space-y-4 border-border p-4 sm:p-5">
            <div className="flex min-w-0 items-center gap-3">
              <SkillAvatar name={item.name} seed={item.repositoryId} />
              <div className="min-w-0">
                <h2 className="break-words font-semibold [overflow-wrap:anywhere]">{item.name}</h2>
                <p className="break-all font-mono text-xs text-muted-foreground">{item.owner}/{item.name}</p>
              </div>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border pt-3">
              <span className="text-xs text-muted-foreground">{t('githubCatalog.repositoryMemberCount', { count: item.memberCount })}</span>
              <Link to="/github" search={{ ...listSearch, repositoryId: item.repositoryId, repositoryPage: page || undefined, page: 0 }}
                className={buttonVariants({ variant: 'ghost', size: 'sm' })} aria-label={`${t('githubCatalog.viewRepository')} ${item.owner}/${item.name}`}>
                {t('githubCatalog.viewRepository')} <ArrowRight className="size-4" aria-hidden="true" />
              </Link>
            </div>
          </Card>
        </li>)}
      </ul> : null}
    </> : null}
    {repository ? <Card className="overflow-hidden border-border">
      <div className="flex flex-col gap-3 border-b border-border bg-muted/30 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="font-semibold">{t('githubCatalog.repositoryMembers')}</h2>
          <p className="text-xs text-muted-foreground">
            {activeQuery ? `${t('githubCatalog.resultCount', { count: total })} · ` : null}
            {t('githubCatalog.repositoryMemberCount', { count: repository.memberCount })}
          </p>
        </div>
        {searchForm}
      </div>
      {members.length ? <ul className="divide-y divide-border">
        {members.map(member => <li key={member.id} className="flex min-w-0 flex-wrap items-center gap-3 px-4 py-3">
          <SkillAvatar name={member.displayName} seed={member.slug} />
          <div className="min-w-0 flex-1">
            <h3 className="break-words text-sm font-semibold [overflow-wrap:anywhere]">{member.displayName}</h3>
            {member.summary ? <p className="line-clamp-2 text-xs leading-5 text-muted-foreground">{member.summary}</p> : null}
          </div>
          <div className="ml-auto flex items-center gap-2">
            <code className="rounded-md bg-secondary px-2 py-1 text-xs">{member.syncRevision
              ? t('review.githubRevisionValue', { count: member.syncRevision })
              : `v${member.version}`}</code>
            <Link to="/space/$namespace/$slug" params={{ namespace: 'github', slug: member.slug }}
              className={buttonVariants({ variant: 'ghost', size: 'sm' })}
              search={{ returnTo: `${pathname}${searchStr}` }}>
              {t('githubCatalog.viewSkill')} <ArrowUpRight className="size-4" aria-hidden="true" />
            </Link>
          </div>
        </li>)}
      </ul> : null}
      {members.length === 0 && <p className="p-4 text-sm text-muted-foreground">{t('githubCatalog.repositoryEmpty')}</p>}
    </Card> : null}
    {!repositoryId && query.isSuccess && repositories.length === 0 ? <Card className="space-y-3 p-5 text-sm text-muted-foreground">
      <p>{t('githubCatalog.repositoryEmpty')}</p>
      {activeQuery && <Button variant="outline" size="sm" onClick={() => submitSearch('')}>{t('githubCatalog.clearRepositorySearch')}</Button>}
    </Card> : null}
    {query.isSuccess && page > 0 && page >= Math.ceil(total / size) ? <Link
      to="/github" search={{ ...currentSearch, page: 0 }} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
      {t('githubCatalog.repositoryFirstPage')}
    </Link> : null}
    {query.isSuccess && total > size && page < Math.ceil(total / size) ? <Pagination page={page} totalPages={Math.ceil(total / size)}
      mobileCompact onPageChange={next => void navigate({ to: '/github', search: { ...currentSearch, page: next } })} /> : null}
  </div>
}
