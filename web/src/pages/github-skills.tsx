import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowUpRight, Download, GitBranch, Search } from 'lucide-react'
import type { SkillSummary } from '@/api/types'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { Pagination } from '@/shared/components/pagination'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { getHeadlineVersion } from '@/shared/lib/skill-lifecycle'
import { formatCompactCount } from '@/shared/lib/number-format'
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
  const { q, sort, page } = useSearch({ from: '/github' })
  const [input, setInput] = useState(q)
  useEffect(() => setInput(q), [q])
  const catalog = useSearchSkills({ namespace: 'github', sort: 'newest', page: 0, size: 1 })
  const skills = useSearchSkills({ namespace: 'github', q, sort, page, size: PAGE_SIZE })
  const submit = (event: FormEvent) => {
    event.preventDefault()
    navigate({ to: '/github', search: { q: input.trim(), sort, page: 0 } })
  }

  return <div className={`github-sync-surface ${APP_SHELL_PAGE_CLASS_NAME}`}>
    <section className="overflow-hidden border border-[var(--sync-line)] bg-[var(--sync-canvas)]">
      <div className="grid lg:grid-cols-[minmax(0,1fr)_250px]">
        <div className="space-y-6 p-6 sm:p-9 lg:p-11">
          <p className="flex items-center gap-2 text-xs font-semibold tracking-[0.12em] text-[var(--sync-blue)]"><GitBranch className="h-4 w-4" aria-hidden="true" />{t('githubCatalog.sourceLabel')}</p>
          <div className="max-w-3xl space-y-3">
            <h1 className="text-3xl font-semibold tracking-tight text-[var(--sync-ink)] sm:text-4xl">{t('githubCatalog.title')}</h1>
            <p className="text-sm leading-7 text-muted-foreground sm:text-base">{t('githubCatalog.description')}</p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Button data-analytics-id="github.catalog.explore" onClick={() => document.getElementById('github-skills-results')?.scrollIntoView({ behavior: 'smooth' })}>{t('githubCatalog.explore')}</Button>
            <Button data-analytics-id="navigation.skills.open" variant="outline" onClick={() => navigate({ to: '/search', search: { q: '', sort: 'newest', page: 0, starredOnly: false } })}>{t('githubCatalog.allSkills')}</Button>
          </div>
        </div>
        <div className="flex flex-col justify-between gap-8 border-t border-[var(--sync-line)] bg-[var(--sync-panel)] p-6 sm:p-9 lg:border-l lg:border-t-0 lg:p-8">
          <p className="text-xs font-semibold tracking-[0.12em] text-[var(--sync-subtle)]">@github</p>
          <div>
            <p className="text-5xl font-semibold tabular-nums text-[var(--sync-teal)]">{catalog.isLoading ? '—' : (catalog.data?.total ?? '—')}</p>
            <p className="mt-2 text-sm font-medium text-[var(--sync-ink)]">{t('githubCatalog.publishedCount')}</p>
            <p className="mt-3 text-xs leading-5 text-[var(--sync-subtle)]">{t('githubCatalog.countNote')}</p>
          </div>
        </div>
      </div>
    </section>

    <section id="github-skills-results" className="scroll-mt-24 space-y-5">
      <div className="flex flex-col gap-2 border-b border-[var(--sync-line)] pb-4 sm:flex-row sm:items-end sm:justify-between">
        <div><h2 className="text-xl font-semibold text-[var(--sync-ink)]">{t('githubCatalog.resultsTitle')}</h2><p className="mt-1 text-sm text-muted-foreground">{t('githubCatalog.resultsDescription')}</p></div>
        {skills.data ? <p className="text-sm tabular-nums text-muted-foreground">{t('githubCatalog.resultCount', { count: skills.data.total })}</p> : null}
      </div>
      <form data-analytics-id="github.catalog.search" onSubmit={submit} className="flex flex-col gap-3 sm:flex-row">
        <div className="relative min-w-0 flex-1"><Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-[var(--sync-blue)]" aria-hidden="true" /><Input className="pl-9" value={input} onChange={(event) => setInput(event.target.value)} placeholder={t('githubCatalog.searchPlaceholder')} aria-label={t('githubCatalog.searchPlaceholder')} /></div>
        <Button type="submit">{t('githubCatalog.search')}</Button>
        <select data-analytics-id="github.catalog.sort" aria-label={t('githubCatalog.sortLabel')} value={sort} onChange={(event) => navigate({ to: '/github', search: { q, sort: event.target.value, page: 0 } })} className="h-9 rounded-md border border-[var(--sync-line)] bg-background px-3 text-sm"><option value="newest">{t('githubCatalog.newest')}</option><option value="downloads">{t('githubCatalog.mostDownloaded')}</option></select>
      </form>
      {skills.isLoading ? <p className="py-10 text-center text-sm text-muted-foreground">{t('githubCatalog.loading')}</p> : null}
      {skills.error ? <p role="alert" className="py-8 text-sm text-destructive">{t('githubCatalog.loadFailed')}: {skills.error.message}</p> : null}
      {!skills.isLoading && !skills.error && !skills.data?.items.length ? <div className="border-l-[3px] border-[var(--sync-amber)] bg-[var(--sync-panel)] p-6 text-sm text-muted-foreground">{t('githubCatalog.empty')}</div> : null}
      {skills.data?.items.length ? <div>{skills.data.items.map((skill) => <GithubCatalogSkillRow key={skill.id} skill={skill} onOpen={() => navigate({ to: `/space/${skill.namespace}/${encodeURIComponent(skill.slug)}`, search: { returnTo: '/github' } })} />)}</div> : null}
      {skills.data && skills.data.total > PAGE_SIZE ? <Pagination analyticsId="github.catalog.paginate" page={page} totalPages={Math.ceil(skills.data.total / PAGE_SIZE)} onPageChange={(next) => navigate({ to: '/github', search: { q, sort, page: next } })} /> : null}
    </section>
  </div>
}
