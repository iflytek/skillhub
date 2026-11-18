import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowUpRight, Download, Search, Github, Layers, FolderGit2 } from 'lucide-react'
import type { SkillSummary } from '@/api/types'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { Pagination } from '@/shared/components/pagination'
import { Button } from '@/shared/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { cn } from '@/shared/lib/utils'
import { Input } from '@/shared/ui/input'
import { getHeadlineVersion } from '@/shared/lib/skill-lifecycle'
import { formatCompactCount } from '@/shared/lib/number-format'
import { GithubRepositoryResults } from '@/features/github-sync/github-repository-results'
import { SkillAvatar } from '@/features/skill/skill-avatar'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'
import '@/features/github-sync/github-catalog.css'

const PAGE_SIZE = 12

export function GithubCatalogSkillRow({ skill, onOpen }: { skill: SkillSummary; onOpen: () => void }) {
  const { t } = useTranslation()
  const version = getHeadlineVersion(skill)

  return <article className="rounded-xl border border-border bg-card p-5">
    <div className="flex items-center gap-3">
      <SkillAvatar name={skill.displayName} seed={skill.slug} />
      <div className="min-w-0">
        <h2 className="break-words text-sm font-semibold [overflow-wrap:anywhere]">{skill.displayName}</h2>
        <span className="text-xs text-muted-foreground">@github</span>
      </div>
    </div>
    <p className="my-4 line-clamp-2 min-h-10 text-sm leading-5 text-muted-foreground">{skill.summary || t('githubCatalog.noSummary')}</p>
    <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border pt-3">
      <div className="flex items-center gap-3 text-xs text-muted-foreground">
        {version?.syncRevision ? <span>{t('review.githubRevisionValue', { count: version.syncRevision })}</span> : version ? <code>v{version.version}</code> : null}
        <span className="inline-flex items-center gap-1"><Download className="size-3.5" aria-hidden="true" />{formatCompactCount(skill.downloadCount)}</span>
      </div>
      <Button data-analytics-id="skill.detail.open" variant="ghost" size="sm" onClick={onOpen}>
        {t('githubCatalog.viewSkill')} <ArrowUpRight className="size-4" aria-hidden="true" />
      </Button>
    </div>
  </article>
}

export function GithubSkillsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { pathname, searchStr } = useLocation()
  const { q, sort, page, view, repositoryId, repositoryPage, memberQuery, snapshotId, repositoryTab } = useSearch({ from: '/github' })
  const [input, setInput] = useState(q)
  useEffect(() => setInput(q), [q])
  const skills = useSearchSkills({ source: 'github', namespace: 'github', q, sort, page, size: PAGE_SIZE }, !view)
  const submit = (event: FormEvent) => {
    event.preventDefault()
    navigate({ to: '/github', search: { q: input.trim(), sort, page: 0, view } })
  }

  const browseNavigation = <nav aria-label={t('githubCatalog.browseBy')} className="inline-flex rounded-lg bg-muted p-1">
          <Link to="/github" search={{ q: '', sort, page: 0 }} aria-current={!view ? 'page' : undefined}
            className={cn('flex items-center gap-2 rounded-md px-3 py-1.5 text-sm', !view ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground')}>
            <Layers className="size-4" aria-hidden="true" />{t('githubCatalog.bySkill')}
          </Link>
          <Link to="/github" search={{ q: '', sort, page: 0, view: 'repositories' }} aria-current={view ? 'page' : undefined}
            className={cn('flex items-center gap-2 rounded-md px-3 py-1.5 text-sm', view ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground')}>
            <FolderGit2 className="size-4" aria-hidden="true" />{t('githubCatalog.byRepository')}
          </Link>
        </nav>

  if (view && repositoryId) {
    return <div className={cn(APP_SHELL_PAGE_CLASS_NAME, 'github-catalog-page')}>
      <GithubRepositoryResults repositoryId={repositoryId} page={page} size={PAGE_SIZE}
        q={q} sort={sort} repositoryPage={repositoryPage} memberQuery={memberQuery} snapshotId={snapshotId} repositoryTab={repositoryTab} />
    </div>
  }

  return <div className={cn(APP_SHELL_PAGE_CLASS_NAME, 'github-catalog-page space-y-6')}>
    <header className="max-w-3xl space-y-3 pb-2">
      <p className="flex items-center gap-2 text-xs text-muted-foreground"><Github className="size-4" aria-hidden="true" />GitHub</p>
      <h1 className="font-heading text-3xl font-bold tracking-tight sm:text-4xl">{t('githubCatalog.title')}</h1>
      <p className="max-w-xl text-sm text-muted-foreground">{t('githubCatalog.catalogIntro')}</p>
    </header>
    <section className="space-y-4">
      <form data-analytics-id="github.catalog.search" onSubmit={submit} className="mb-7 flex max-w-3xl items-center gap-3 rounded-xl border border-border bg-card p-2.5 shadow-sm">
        <Search className="ml-2 size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
        <Input className="min-w-0 border-0 bg-transparent shadow-none" maxLength={120} value={input} onChange={event => setInput(event.target.value)}
          placeholder={t(view ? 'githubCatalog.searchRepositories' : 'githubCatalog.searchPlaceholder')} aria-label={t(view ? 'githubCatalog.searchRepositories' : 'githubCatalog.searchPlaceholder')} />
        <Button type="submit">{t('githubCatalog.search')}</Button>
      </form>
      {!view && <div className="flex flex-wrap items-center gap-4 border-t border-border pt-4">
        {browseNavigation}
        {!view && skills.data ? <span className="text-xs tabular-nums text-muted-foreground">{t('githubCatalog.resultCount', { count: skills.data.total })}</span> : null}
        {!view && <Select value={sort} onValueChange={value => void navigate({ to: '/github', search: { q, sort: value, page: 0 } })}>
          <SelectTrigger data-analytics-id="github.catalog.sort" aria-label={t('githubCatalog.sortLabel')} className="ml-auto w-36"><SelectValue /></SelectTrigger>
          <SelectContent><SelectItem value="newest">{t('githubCatalog.newest')}</SelectItem><SelectItem value="downloads">{t('githubCatalog.mostDownloaded')}</SelectItem></SelectContent>
        </Select>}
      </div>}
      {view ? (
        <GithubRepositoryResults navigation={browseNavigation} q={q} sort={sort} page={page} size={PAGE_SIZE} />
      ) : <>
        {skills.isLoading ? <p className="py-10 text-center text-sm text-muted-foreground">{t('githubCatalog.loading')}</p> : null}
        {skills.error ? <p role="alert" className="py-8 text-sm text-destructive">{t('githubCatalog.loadFailed')}: {skills.error.message}</p> : null}
        {!skills.isLoading && !skills.error && !skills.data?.items.length ? <div className="flex min-h-64 flex-col items-center justify-center gap-3 rounded-xl border border-border bg-card p-6 text-sm text-muted-foreground">
          <Search className="size-7" aria-hidden="true" /><p>{t('githubCatalog.empty')}</p>
          {q && <Button variant="outline" size="sm" onClick={() => void navigate({ to: '/github', search: { q: '', sort, page: 0 } })}>{t('githubCatalog.clearRepositorySearch')}</Button>}
        </div> : null}
        {skills.data?.items.length ? <div className="grid gap-4 md:grid-cols-2">{skills.data.items.map((skill) => <GithubCatalogSkillRow key={skill.id} skill={skill} onOpen={() => navigate({ to: `/space/${skill.namespace}/${encodeURIComponent(skill.slug)}`, search: { returnTo: pathname + searchStr } })} />)}</div> : null}
        {skills.data && skills.data.total > PAGE_SIZE ? <Pagination analyticsId="github.catalog.paginate" page={page} totalPages={Math.ceil(skills.data.total / PAGE_SIZE)} onPageChange={(next) => navigate({ to: '/github', search: { q, sort, page: next } })} /> : null}
      </>}
    </section>
  </div>
}
