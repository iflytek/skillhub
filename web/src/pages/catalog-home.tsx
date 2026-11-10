import { useState } from 'react'
import { ArrowRight, ChevronDown, LayoutGrid, Search } from 'lucide-react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { BrandMark } from '@/shared/components/brand-mark'
import { QuickStartSection } from '@/shared/components/quick-start'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useVisibleLabels } from '@/shared/hooks/use-label-queries'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { localizedPresentationText, useSitePresentation } from '@/shared/hooks/use-site-presentation'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { Button } from '@/shared/ui/button'

type CatalogSort = 'relevance' | 'downloads' | 'newest'
type SectionKind = 'popular' | 'latest'
interface CatalogSectionProps {
  kind: SectionKind
  query: Pick<ReturnType<typeof useSearchSkills>, 'data' | 'isLoading' | 'isError' | 'refetch'>
  onBrowse: (sort: CatalogSort) => void
  onSkill: (namespace: string, slug: string) => void
}

function CatalogSkillSection({ kind, query, onBrowse, onSkill }: CatalogSectionProps) {
  const { t } = useTranslation()
  const sort = kind === 'popular' ? 'downloads' : 'newest'
  const items = query.data?.items ?? []
  return <section aria-labelledby={`catalog-${kind}-title`} className="space-y-5">
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div className="space-y-1.5">
        <h2 id={`catalog-${kind}-title`} className="text-xl font-semibold tracking-tight text-foreground md:text-2xl">{t(`home.${kind}Title`)}</h2>
        <p className="text-sm leading-relaxed text-muted-foreground">{t(`catalog.${kind}Description`)}</p>
      </div>
      <Button data-analytics-id={kind === 'popular' ? 'discovery.popular.open' : 'discovery.latest.open'} variant="ghost" size="sm" onClick={() => onBrowse(sort)}>
        {t('catalog.viewAll')}<ArrowRight aria-hidden className="h-4 w-4" />
      </Button>
    </div>
    {query.isLoading ? <div aria-busy="true"><span role="status" className="sr-only">{t('common.loading')}</span><SkeletonList count={3} /></div>
      : query.isError ? <div role="alert" className="flex flex-wrap items-center justify-between gap-4 rounded-lg border border-border px-5 py-6">
        <div className="space-y-1"><p className="text-sm font-medium text-foreground">{t('catalog.loadFailed')}</p><p className="text-sm text-muted-foreground">{t('catalog.loadFailedHelp')}</p></div>
        <Button data-analytics-id={kind === 'popular' ? 'discovery.popular.retry' : 'discovery.latest.retry'} variant="outline" size="sm" onClick={() => void query.refetch()}>{t('catalog.retry')}</Button>
      </div>
      : items.length === 0 ? <div className="flex items-start gap-3 rounded-lg border border-dashed border-border px-5 py-6">
        <LayoutGrid aria-hidden className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" />
        <div className="space-y-1"><h3 className="text-sm font-medium text-foreground">{t(`catalog.${kind}Empty`)}</h3><p className="text-sm leading-relaxed text-muted-foreground">{t('catalog.emptyHelp')}</p></div>
      </div>
      : <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">{items.map(skill => <SkillCard key={skill.id} analyticsId="skill.card.open" skill={skill} onClick={() => onSkill(skill.namespace, skill.slug)} />)}</div>}
  </section>
}

/** Root catalog composition; the existing landing and /skills pages remain independent. */
export function CatalogHomePage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { settings } = useSitePresentation()
  const [labelsExpanded, setLabelsExpanded] = useState(false)
  const popular = useSearchSkills({ sort: 'downloads', size: 6 }, settings.showPopular)
  const latest = useSearchSkills({ sort: 'newest', size: 6 }, settings.showLatest)
  const labelsQuery = useVisibleLabels()
  const labels = labelsQuery.data?.filter(label => label.slug.trim() && label.displayName.trim()) ?? []
  const title = localizedPresentationText(settings.heroTitle, i18n.resolvedLanguage) ?? settings.siteName ?? 'SkillHub'
  const description = localizedPresentationText(settings.heroDescription, i18n.resolvedLanguage) ?? t('catalog.description')
  const browse = (sort: CatalogSort = 'relevance', label = '') => navigate({ to: '/search', search: { q: '', label, sort, page: 0, starredOnly: false } })
  const search = (query: string) => navigate({ to: '/search', search: { q: normalizeSearchQuery(query), sort: 'relevance', page: 0, starredOnly: false } })
  const openSkill = (namespace: string, slug: string) => navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}` })

  return <div className="space-y-10 pb-6 lg:space-y-12">
    <header className="space-y-6 border-b border-border pb-8 pt-3 md:pb-10 md:pt-6">
      <div className="flex items-center gap-3"><BrandMark className="h-10 w-10 shrink-0 rounded-lg border border-border bg-card" /><p className="text-sm font-medium text-muted-foreground">{t('catalog.kicker')}</p></div>
      <div className="max-w-4xl space-y-4">
        <h1 className="whitespace-pre-wrap [overflow-wrap:anywhere] text-4xl font-semibold leading-tight tracking-tight text-foreground md:text-5xl">{title}</h1>
        <p className="max-w-3xl whitespace-pre-wrap [overflow-wrap:anywhere] text-base leading-relaxed text-muted-foreground md:text-lg">{description}</p>
      </div>
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:gap-4">
        <div className="w-full max-w-3xl [&_form]:items-center [&_form]:p-0.5 [&_form>button]:h-10"><SearchBar analyticsId="search.query.submit" inputLabel={t('catalog.searchLabel')} placeholder={t('catalog.searchPlaceholder')} onSearch={search} /></div>
        <Button data-analytics-id="navigation.skills.open" variant="outline" className="h-11 w-full shrink-0 sm:w-auto" onClick={() => browse()}>{t('catalog.browseAll')}<ArrowRight aria-hidden className="h-4 w-4" /></Button>
      </div>
    </header>

    {labels.length > 0 && <nav aria-label={t('catalog.labelsTitle')} className="space-y-3">
      <h2 className="text-sm font-medium text-muted-foreground">{t('catalog.labelsTitle')}</h2>
      <div id="catalog-label-options" className="flex flex-wrap gap-2">{labels.slice(0, labelsExpanded ? labels.length : 8).map(label => <Button data-analytics-id="search.label.filter" key={label.slug} variant="outline" size="sm" className="max-w-full min-w-0" title={label.displayName} onClick={() => browse('relevance', label.slug)}><span className="truncate">{label.displayName}</span></Button>)}</div>
      {labels.length > 8 && <Button variant="ghost" size="sm" aria-expanded={labelsExpanded} aria-controls="catalog-label-options" onClick={() => setLabelsExpanded(value => !value)}>{t(labelsExpanded ? 'catalog.collapseLabels' : 'catalog.expandLabels')}</Button>}
    </nav>}

    {settings.showPopular && <CatalogSkillSection kind="popular" query={popular} onBrowse={browse} onSkill={openSkill} />}
    {settings.showLatest && <CatalogSkillSection kind="latest" query={latest} onBrowse={browse} onSkill={openSkill} />}
    {!settings.showPopular && !settings.showLatest && <section aria-labelledby="catalog-search-start" className="flex items-start gap-4 py-3">
      <Search aria-hidden className="mt-1 h-5 w-5 shrink-0 text-muted-foreground" />
      <div className="space-y-2"><h2 id="catalog-search-start" className="text-lg font-semibold text-foreground">{t('catalog.searchStartTitle')}</h2><p className="max-w-2xl text-sm leading-relaxed text-muted-foreground">{t('catalog.searchStartHelp')}</p></div>
    </section>}

    {settings.showQuickStart && <details className="group rounded-xl border border-border bg-card">
      <summary className="flex cursor-pointer list-none flex-wrap items-center justify-between gap-3 rounded-xl px-5 py-5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring [&::-webkit-details-marker]:hidden">
        <span className="space-y-1"><span className="block text-base font-semibold text-foreground">{t('home.quickStart.title')}</span><span className="block text-sm leading-relaxed text-muted-foreground">{t('catalog.quickStartHelp')}</span></span>
        <ChevronDown aria-hidden className="h-4 w-4 shrink-0 text-muted-foreground group-open:rotate-180" />
      </summary>
      <div className="border-t border-border px-5 py-6"><QuickStartSection ns="home" hideHeading /></div>
    </details>}
  </div>
}
