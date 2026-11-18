import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowUpRight, Download, Search } from 'lucide-react'
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
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'
import '@/features/github-sync/github-sync-colors.css'

const PAGE_SIZE = 12

export function GithubCatalogSkillRow({ skill, onOpen }: { skill: SkillSummary; onOpen: () => void }) {
  const { t } = useTranslation()
  const version = getHeadlineVersion(skill)

  return <article className="group grid gap-4 border-b border-[var(--sync-line)] py-6 last:border-b-0 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
    <div className="min-w-0 space-y-2 border-l-[3px] border-[var(--sync-blue)] pl-4">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <h3 className="break-words text-lg font-semibold leading-tight text-[var(--sync-ink)]">{skill.displayName}</h3>
        <span className="font-mono text-xs text-[var(--sync-blue)]">@github</span>
      </div>
      <p className="line-clamp-2 max-w-3xl text-sm leading-6 text-muted-foreground">{skill.summary || t('githubCatalog.noSummary')}</p>
      <div className="flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
        {version?.syncRevision ? <span>{t('review.githubRevisionValue', { count: version.syncRevision })}</span> : version ? <span>v{version.version}</span> : null}
        <span className="inline-flex items-center gap-1"><Download className="h-3.5 w-3.5" aria-hidden="true" />{formatCompactCount(skill.downloadCount)}</span>
      </div>
    </div>
    <Button data-analytics-id="skill.detail.open" variant="outline" size="sm" className="w-fit border-[var(--sync-line)] text-[var(--sync-blue)] hover:bg-[var(--sync-panel)]" onClick={onOpen}>
      {t('githubCatalog.viewSkill')} <ArrowUpRight className="ml-1 h-4 w-4" aria-hidden="true" />
    </Button>
  </article>
}

export function GithubSkillsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { q, sort, page, view, repositoryId, repositoryPage, memberQuery, snapshotId, repositoryTab } = useSearch({ from: '/github' })
  const [input, setInput] = useState(q)
  useEffect(() => setInput(q), [q])
  const skills = useSearchSkills({ source: 'github', namespace: 'github', q, sort, page, size: PAGE_SIZE }, !view)
  const submit = (event: FormEvent) => {
    event.preventDefault()
    navigate({ to: '/github', search: { q: input.trim(), sort, page: 0 } })
  }

  if (view && repositoryId) {
    return <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <GithubRepositoryResults repositoryId={repositoryId} page={page} size={PAGE_SIZE}
        q={q} sort={sort} repositoryPage={repositoryPage} memberQuery={memberQuery} snapshotId={snapshotId} repositoryTab={repositoryTab} />
    </div>
  }

  return <div className={cn(APP_SHELL_PAGE_CLASS_NAME, 'github-sync-surface space-y-6')}>
    <header className="space-y-2">
      <h1 className="font-heading text-3xl font-bold tracking-tight sm:text-4xl">{t('githubCatalog.title')}</h1>
      <p className="max-w-xl text-sm text-muted-foreground">{t('githubCatalog.catalogIntro')}</p>
    </header>
    <section className="space-y-4">
      <nav aria-label={t('githubCatalog.browseBy')} className="flex items-center gap-6 border-b border-border">
        <Link to="/github" search={{ q: view ? '' : q, sort, page: 0 }} aria-current={!view ? 'page' : undefined}
          className={cn('border-b-2 px-1 pb-3 text-sm font-medium', !view ? 'border-foreground text-foreground' : 'border-transparent text-muted-foreground')}>
          {t('githubCatalog.bySkill')}
        </Link>
        <Link to="/github" search={{ q: view ? q : '', sort, page: 0, view: 'repositories' }} aria-current={view ? 'page' : undefined}
          className={cn('border-b-2 px-1 pb-3 text-sm font-medium', view ? 'border-foreground text-foreground' : 'border-transparent text-muted-foreground')}>
          {t('githubCatalog.byRepository')}
        </Link>
      </nav>
      {view ? (
        <GithubRepositoryResults q={q} sort={sort} page={page} size={PAGE_SIZE} />
      ) : <>
        <form data-analytics-id="github.catalog.search" onSubmit={submit} className="flex flex-wrap items-center gap-2 rounded-lg border border-border bg-muted/30 p-3">
          <div className="relative min-w-44 max-w-xl flex-1"><Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-[var(--sync-blue)]" aria-hidden="true" /><Input className="pl-9" value={input} onChange={(event) => setInput(event.target.value)} placeholder={t('githubCatalog.searchPlaceholder')} aria-label={t('githubCatalog.searchPlaceholder')} /></div>
          <Button type="submit">{t('githubCatalog.search')}</Button>
          <Select value={sort} onValueChange={value => void navigate({ to: '/github', search: { q, sort: value, page: 0 } })}>
            <SelectTrigger data-analytics-id="github.catalog.sort" aria-label={t('githubCatalog.sortLabel')} className="w-36"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="newest">{t('githubCatalog.newest')}</SelectItem>
              <SelectItem value="downloads">{t('githubCatalog.mostDownloaded')}</SelectItem>
            </SelectContent>
          </Select>
        </form>
        {skills.data ? <p className="text-sm tabular-nums text-muted-foreground">{t('githubCatalog.resultCount', { count: skills.data.total })}</p> : null}
        {skills.isLoading ? <p className="py-10 text-center text-sm text-muted-foreground">{t('githubCatalog.loading')}</p> : null}
        {skills.error ? <p role="alert" className="py-8 text-sm text-destructive">{t('githubCatalog.loadFailed')}: {skills.error.message}</p> : null}
        {!skills.isLoading && !skills.error && !skills.data?.items.length ? <div className="border-l-[3px] border-[var(--sync-amber)] bg-[var(--sync-panel)] p-6 text-sm text-muted-foreground">{t('githubCatalog.empty')}</div> : null}
        {skills.data?.items.length ? <div>{skills.data.items.map((skill) => <GithubCatalogSkillRow key={skill.id} skill={skill} onOpen={() => navigate({ to: `/space/${skill.namespace}/${encodeURIComponent(skill.slug)}`, search: { returnTo: '/github' } })} />)}</div> : null}
        {skills.data && skills.data.total > PAGE_SIZE ? <Pagination analyticsId="github.catalog.paginate" page={page} totalPages={Math.ceil(skills.data.total / PAGE_SIZE)} onPageChange={(next) => navigate({ to: '/github', search: { q, sort, page: next } })} /> : null}
      </>}
    </section>
  </div>
}
