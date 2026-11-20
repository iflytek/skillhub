import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useLocation, useNavigate } from '@tanstack/react-router'
import { ArrowLeft, ArrowRight, ArrowUpRight, Clock3, GitCommitHorizontal, Github, History, Layers, Search, Package } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { fetchJson, WEB_API_PREFIX } from '@/api/client'
import type { components } from '@/api/generated/schema'
import { useAuth } from '@/features/auth/use-auth'
import { SkillAvatar } from '@/features/skill/skill-avatar'
import { Pagination } from '@/shared/components/pagination'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { ApiError } from '@/shared/lib/api-error'
import { MAX_SEARCH_QUERY_LENGTH } from '@/shared/lib/search-query'
import { GithubSnapshotDownload } from './github-snapshot-download'
import { GithubRepositoryInstall } from './github-repository-install'

interface Props {
  navigation?: ReactNode
  repositoryId?: string
  repositoryPage?: number
  memberQuery?: string
  snapshotId?: string
  repositoryTab?: 'history'
  q: string
  sort: string
  page: number
  size: number
}

export function GithubRepositoryResults({ repositoryId, repositoryPage, memberQuery, snapshotId, repositoryTab, q, sort, page, size, navigation }: Props) {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { pathname, searchStr } = useLocation()
  const { user, isLoading: authLoading } = useAuth()
  const activeQuery = repositoryId ? (memberQuery ?? '') : q
  const [input, setInput] = useState(activeQuery)
  useEffect(() => setInput(activeQuery), [activeQuery])

  const params = new URLSearchParams({ page: String(page), size: String(size), q: activeQuery })
  if (snapshotId) params.set('snapshotId', snapshotId)
  const endpoint = `${WEB_API_PREFIX}/github-repositories`
  const list = useQuery({
    queryKey: ['skills', 'github-repositories', user?.userId, q, page, size],
    queryFn: ({ signal }) => fetchJson<components['schemas']['GithubCatalogRepositoryPage']>(`${endpoint}?${params}`, { signal }),
    enabled: !authLoading && !repositoryId,
  })
  const detail = useQuery({
    queryKey: ['skills', 'github-repository', user?.userId, repositoryId, snapshotId, memberQuery, page, size],
    queryFn: ({ signal }) => fetchJson<components['schemas']['GithubCatalogRepositoryDetail']>(`${endpoint}/${repositoryId}?${params}`, { signal }),
    enabled: !authLoading && !!repositoryId,
    retry: (count, error) => (!(error instanceof ApiError) || error.status !== 404) && count < 2,
  })
  const query = repositoryId ? detail : list
  const repository = detail.isSuccess ? detail.data.repository : undefined
  const repositories = list.isSuccess ? list.data.items : []
  const snapshots = detail.data?.snapshots ?? []
  const selected = snapshots.find(item => item.id === detail.data?.selectedSnapshotId)
  const latest = snapshots[0]
  const comparison = detail.data?.comparison
  const dateFormat = new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium', timeStyle: 'short' })
  const members = detail.isSuccess ? detail.data.items : []
  const total = query.data?.total ?? 0
  const notFound = query.error instanceof ApiError && query.error.status === 404
  const listSearch = { q, sort, view: 'repositories' as const, page: repositoryPage ?? 0 }
  const currentSearch = { ...listSearch, repositoryId, repositoryPage, memberQuery, snapshotId, repositoryTab, page }
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
    </Link> : null}
    {repository && <header className="flex flex-wrap items-end justify-between gap-3 pb-3">
      <div className="min-w-0 space-y-2">
        <span className="badge-soft badge-soft-blue inline-flex items-center gap-1.5"><Github className="size-3.5" aria-hidden="true" />{t('githubCatalog.repositorySource')}</span>
        <div className="flex items-center gap-3">
          <SkillAvatar name={repository.name} seed={repository.repositoryId} />
          <div className="min-w-0">
            <h1 className="break-words font-heading text-2xl font-bold [overflow-wrap:anywhere] sm:text-3xl">{repository.name}</h1>
            <p className="break-all font-mono text-xs text-muted-foreground">{repository.owner}/{repository.name}</p>
          </div>
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <a className={buttonVariants({ variant: 'outline', size: 'sm' })} target="_blank" rel="noreferrer"
          href={`https://github.com/${encodeURIComponent(repository.owner)}/${encodeURIComponent(repository.name)}`}>
          {t('githubCatalog.openSourceRepository')} <ArrowUpRight className="size-4" aria-hidden="true" />
        </a>
      </div>
    </header>}
    {authLoading || query.isLoading ? <p role="status" className="py-10 text-center text-sm text-muted-foreground">{t('githubCatalog.repositoryLoading')}</p> : null}
    {query.isError ? <Card role="alert" className="flex flex-wrap items-center gap-3 p-4 text-sm text-muted-foreground">
      {t(notFound ? 'githubCatalog.repositoryUnavailable' : 'githubCatalog.repositoryLoadFailed')}
      {!notFound && <Button variant="outline" size="sm" onClick={() => void query.refetch()}>{t('githubCatalog.repositoryRetry')}</Button>}
    </Card> : null}
    {!repositoryId && <div className="flex flex-wrap items-center gap-4 border-t border-border pt-4">
      {navigation}{query.isSuccess && <span className="text-xs text-muted-foreground">{t('githubCatalog.repositoryCount', { count: total })}</span>}
    </div>}
    {!repositoryId && query.isSuccess ? <>
      {repositories.length ? <ul className="grid gap-4 md:grid-cols-2">
        {repositories.map(item => <li key={item.repositoryId} className="min-w-0">
          <Card className="space-y-5 rounded-xl border-border p-5 sm:p-6">
            <div className="flex min-w-0 items-center gap-3">
              <SkillAvatar name={item.name} seed={item.repositoryId} />
              <div className="min-w-0">
                <h2 className="break-words text-base font-semibold [overflow-wrap:anywhere]">{item.name}</h2>
                <p className="break-all font-mono text-xs text-muted-foreground">{item.owner}/{item.name}</p>
              </div>
            </div>
            <div className="min-h-20 rounded-lg border border-border/60 bg-muted/30 px-3 py-2">
              {item.preview?.map(member => <div key={member.id} className="flex min-w-0 items-center gap-2 py-1.5 text-xs">
                <Package className="size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
                <span className="min-w-0 truncate">{member.displayName}</span>
                <code className="ml-auto max-w-28 shrink-0 truncate text-muted-foreground" title={member.version}>
                  {member.syncRevision ? t('review.githubRevisionValue', { count: member.syncRevision }) : member.version}
                </code>
              </div>)}
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
    {repository ? <GithubRepositoryInstall key={repository.repositoryId} repositoryId={repository.repositoryId} /> : null}
    {repository ? <Tabs value={repositoryTab ?? 'members'} className="github-repository-layout" onValueChange={tab => {
      void navigate({ to: '/github', search: { ...currentSearch, repositoryTab: tab === 'history' ? 'history' : undefined } })
    }}>
      <aside className="github-repository-sidebar space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card p-5">
        <div className="flex min-w-0 flex-wrap items-center gap-2 text-sm">
          <GitCommitHorizontal className="size-4" aria-hidden="true" />
          <h2 className="font-medium">{t('githubCatalog.repositorySnapshotCurrent')}</h2>
          {selected ? <>
            <code>{selected.commit.slice(0, 10)}</code>
            <span className="text-xs text-muted-foreground">{t('githubCatalog.repositoryMemberCount', { count: selected.memberCount })}</span>
            <span className={selected.status === 'AVAILABLE' ? 'badge-soft badge-soft-green' : 'badge-soft badge-soft-amber'}>
              {t(`githubCatalog.repositorySnapshotStatus.${selected.status}`)}
            </span>
          </> : <p className="text-xs text-muted-foreground">{t('githubCatalog.repositorySnapshotMissing')}</p>}
        </div>
        {selected && <time className="block w-full text-xs text-muted-foreground" dateTime={selected.capturedAt}>{dateFormat.format(new Date(selected.capturedAt))}</time>}
        {selected?.status === 'AVAILABLE' ? <GithubSnapshotDownload key={selected.id}
          repositoryId={repository.repositoryId} repositoryName={`${repository.owner}/${repository.name}`} snapshot={selected} /> : null}

      </div>
      {latest?.status === 'PENDING' ? <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-500/35 bg-amber-500/5 px-4 py-3">
        <div className="flex items-start gap-2.5">
          <Clock3 className="mt-0.5 size-4 shrink-0 text-amber-700 dark:text-amber-300" aria-hidden="true" />
          <div>
            <p className="text-sm font-medium">{t('githubCatalog.repositorySnapshotStatus.PENDING')} · {latest.availableCount} / {latest.memberCount}</p>
            {latest.id !== selected?.id && selected?.status === 'AVAILABLE' ? <p className="text-xs text-muted-foreground">
              {t('githubCatalog.repositorySnapshotLatestPending')}
            </p> : null}
          </div>
        </div>
        {latest.id !== selected?.id ? <Link to="/github"
          search={{ ...currentSearch, snapshotId: latest.id, repositoryTab: undefined, memberQuery: undefined, page: 0 }}
          className={buttonVariants({ variant: 'ghost', size: 'sm' })}>
          {t('githubCatalog.repositorySnapshotView')} <ArrowRight className="size-4" aria-hidden="true" />
        </Link> : snapshots.some(item => item.status === 'AVAILABLE') ? <Link to="/github"
          search={{ ...currentSearch, snapshotId: undefined, repositoryTab: undefined, memberQuery: undefined, page: 0 }}
          className={buttonVariants({ variant: 'outline', size: 'sm' })}>
          {t('githubCatalog.repositorySnapshotReturn')}
        </Link> : null}
      </div> : null}
      </aside>
      <div className="github-repository-body">
      <TabsList className="flex gap-4 overflow-x-auto [scrollbar-width:none] sm:gap-6">
        <TabsTrigger value="members" className="gap-1.5 sm:gap-2"><Layers className="hidden size-4 sm:block" aria-hidden="true" />{t('githubCatalog.repositoryMembers')} <span className="text-xs tabular-nums text-muted-foreground">{selected?.availableCount ?? repository.memberCount}</span></TabsTrigger>
        <TabsTrigger value="history" className="gap-1.5 sm:gap-2"><History className="hidden size-4 sm:block" aria-hidden="true" />{t('githubCatalog.repositorySnapshotHistory')} <span className="text-xs tabular-nums text-muted-foreground">{snapshots.length}</span></TabsTrigger>
      </TabsList>
      <TabsContent value="history" className="space-y-3 p-5">
        <div>
          {snapshots.length ? <ul>
            {snapshots.map((item, index) => <li key={item.id} className="github-history-item">
              <span className="flex size-8 shrink-0 items-center justify-center rounded-full border border-border bg-muted/30"><GitCommitHorizontal className="size-4 text-muted-foreground" aria-hidden="true" /></span>
              <div className="flex min-w-0 flex-1 flex-col items-start gap-3">
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <code className="font-semibold">{item.commit.slice(0, 10)}</code>
                {index === 0 && <span className="badge-soft badge-soft-blue">{t('githubCatalog.repositorySnapshotLatest')}</span>}
                {item.id === selected?.id && <span className="badge-soft">{t('githubCatalog.repositorySnapshotSelected')}</span>}
                <time className="text-xs text-muted-foreground" dateTime={item.capturedAt}>{dateFormat.format(new Date(item.capturedAt))}</time>
              </div>
              <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                {t(`githubCatalog.repositorySnapshotStatus.${item.status}`)}
                <Link to="/github" search={{ ...currentSearch, snapshotId: item.id, repositoryTab: undefined, memberQuery: undefined, page: 0 }}
                  className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                  {t('githubCatalog.repositorySnapshotView')} <ArrowRight className="size-4" aria-hidden="true" />
                </Link>
              </div>
              </div>
            </li>)}
          </ul> : <p className="p-4 text-sm text-muted-foreground">{t('githubCatalog.repositorySnapshotMissing')}</p>}
        </div>
        {comparison && selected ? <Card className="space-y-3 border-border p-4">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-sm font-semibold">{t('githubCatalog.repositorySnapshotChanges')}</h2>
            <code className="text-xs text-muted-foreground">{comparison.baseCommit.slice(0, 10)} → {selected.commit.slice(0, 10)}</code>
          </div>
          {comparison.items.length ? <ul className="divide-y divide-border text-sm">
            {comparison.items.map(item => <li key={item.slug} className="flex flex-wrap items-center gap-2 py-2">
              <span className="badge-soft">{t(`githubCatalog.repositorySnapshotChangeKind.${item.kind}`)}</span>
              <span className="min-w-0 break-words font-medium">{item.slug}</span>
              <code className="text-xs text-muted-foreground">{[item.previousVersion, item.currentVersion].filter(Boolean).join(' → ')}</code>
            </li>)}
          </ul> : <p className="text-xs text-muted-foreground">{t('githubCatalog.repositorySnapshotNoChanges')}</p>}
          {comparison.total > comparison.items.length && <p className="text-xs text-muted-foreground">
            {t('githubCatalog.repositorySnapshotChangesLimited', { shown: comparison.items.length, total: comparison.total })}
          </p>}
        </Card> : null}
      </TabsContent>
      <TabsContent value="members">
        <div>
          <div className="flex flex-col gap-3 border-b border-border bg-muted/30 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="font-semibold">{t('githubCatalog.repositoryMembers')}</h2>
              <p className="text-xs text-muted-foreground">
                {activeQuery ? `${t('githubCatalog.resultCount', { count: total })} · ` : null}
                {t('githubCatalog.repositoryMemberCount', { count: selected?.availableCount ?? repository.memberCount })}
              </p>
            </div>
            {searchForm}
          </div>
          {members.length ? <ul className="divide-y divide-border">
            {members.map(member => <li key={member.id} className="flex min-w-0 flex-wrap items-center gap-3 px-5 py-5">
              <SkillAvatar name={member.displayName} seed={member.slug} />
              <div className="min-w-0 flex-1">
                <h3 className="break-words text-sm font-semibold [overflow-wrap:anywhere]">{member.displayName}</h3>
                {member.summary ? <p className="line-clamp-2 text-xs leading-5 text-muted-foreground">{member.summary}</p> : null}
              </div>
              <div className="ml-auto flex items-center gap-1">
                <code className="rounded-md bg-secondary px-2 py-1 text-xs">{member.syncRevision
                  ? t('review.githubRevisionValue', { count: member.syncRevision })
                  : `v${member.version}`}</code>
                <Link to="/space/$namespace/$slug" params={{ namespace: 'github', slug: member.slug }}
                  aria-label={t('githubCatalog.viewSkill') + ' ' + member.displayName}
                  className={buttonVariants({ variant: 'ghost', size: 'sm' })}
                  search={{ returnTo: `${pathname}${searchStr}`, version: selected ? member.version : undefined }}>
                  <span className="sr-only sm:not-sr-only">{t('githubCatalog.viewSkill')}</span> <ArrowUpRight className="size-4" aria-hidden="true" />
                </Link>
              </div>
            </li>)}
          </ul> : null}
          {members.length === 0 && <p className="p-4 text-sm text-muted-foreground">{t('githubCatalog.repositoryEmpty')}</p>}
        </div>
      </TabsContent>
      </div>
    </Tabs> : null}
    {!repositoryId && query.isSuccess && repositories.length === 0 ? <Card className="flex min-h-64 flex-col items-center justify-center gap-3 p-5 text-sm text-muted-foreground">
      <Search className="size-7" aria-hidden="true" />
      <p>{t('githubCatalog.repositoryEmpty')}</p>
      {activeQuery && <Button variant="outline" size="sm" onClick={() => submitSearch('')}>{t('githubCatalog.clearRepositorySearch')}</Button>}
    </Card> : null}
    {!repositoryTab && query.isSuccess && page > 0 && page >= Math.ceil(total / size) ? <Link
      to="/github" search={{ ...currentSearch, page: 0 }} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
      {t('githubCatalog.repositoryFirstPage')}
    </Link> : null}
    {!repositoryTab && query.isSuccess && total > size && page < Math.ceil(total / size) ? <Pagination page={page} totalPages={Math.ceil(total / size)}
      mobileCompact onPageChange={next => void navigate({ to: '/github', search: { ...currentSearch, page: next } })} /> : null}
  </div>
}
