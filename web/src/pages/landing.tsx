import { Link, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import {
  ArrowRight,
  CheckCircle2,
  Clock3,
  GitBranch,
  Lock,
  Monitor,
  PackageOpen,
  Search as SearchIcon,
  Server,
  Settings,
  Shield,
  Terminal,
  Users,
} from 'lucide-react'
import { BrandMark } from '@/shared/components/brand-mark'
import { LandingQuickStartSection } from '@/shared/components/landing-quick-start'
import { HomepageRecommendations } from '@/features/recommendation/recommendation-preview'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useInView } from '@/shared/hooks/use-in-view'
import { Button } from '@/shared/ui/button'
import { useSitePresentation, presentationText } from '@/shared/hooks/use-site-presentation'
import { presentationItemVisible, presentationLinkUrl } from '@/shared/lib/presentation-content'
import { APP_FRAME_CLASS_NAME } from '@/shared/lib/layout-frame'

interface HeroSkillItem {
  name: string
  namespace: string
  summaryKey: string
  version: string
  badgeClassName: string
  statusClassName: string
}

const HERO_SKILLS: HeroSkillItem[] = [
  {
    name: 'weather',
    namespace: 'global',
    summaryKey: 'landing.experience.demoSkills.weather',
    version: '1.3.0',
    badgeClassName: 'bg-indigo-50 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-300',
    statusClassName: 'bg-emerald-500',
  },
  {
    name: 'git-helper',
    namespace: 'devtools',
    summaryKey: 'landing.experience.demoSkills.gitHelper',
    version: '2.1.0',
    badgeClassName: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-300',
    statusClassName: 'bg-emerald-500',
  },
  {
    name: 'diagram-maker',
    namespace: 'global',
    summaryKey: 'landing.experience.demoSkills.diagramMaker',
    version: '0.9.2',
    badgeClassName: 'bg-amber-50 text-amber-600 dark:bg-amber-500/10 dark:text-amber-300',
    statusClassName: 'bg-amber-500',
  },
  {
    name: 'code-reviewer',
    namespace: 'devtools',
    summaryKey: 'landing.experience.demoSkills.codeReviewer',
    version: '1.5.0',
    badgeClassName: 'bg-cyan-50 text-cyan-600 dark:bg-cyan-500/10 dark:text-cyan-300',
    statusClassName: 'bg-emerald-500',
  },
]

function HeroBrowserMockup({ onSearch }: { onSearch: (query: string) => void }) {
  const { t, i18n } = useTranslation()
  const { settings } = useSitePresentation()
  const text = (section: Parameters<typeof presentationText>[1], slot: string) => presentationText(settings, section, slot, i18n.resolvedLanguage, t)

  return (
    <div className="relative mt-8 lg:mt-0">
      <div className="absolute -inset-3 -z-10 hidden rotate-1 rounded-2xl bg-secondary lg:block" />
      <div className="overflow-hidden rounded-xl border border-border/70 bg-card shadow-[var(--shadow-card)]">
        <div className="flex items-center justify-between border-b border-border/70 bg-secondary/70 px-4 py-3">
          <div className="flex items-center gap-2" aria-hidden>
            <span className="h-3 w-3 rounded-full bg-[#ff5f57]" />
            <span className="h-3 w-3 rounded-full bg-[#febc2e]" />
            <span className="h-3 w-3 rounded-full bg-[#28c840]" />
          </div>
          <span className="rounded-full bg-background px-3 py-1 text-[11px] font-medium text-muted-foreground ring-1 ring-border/70">
            {text('hero', 'previewTitle')}
          </span>
        </div>

        <div className="flex min-h-[360px] flex-col p-5">
          <div className="mb-4 flex items-center gap-2 rounded-lg border border-border/70 bg-secondary/70 px-3 py-2 transition-colors focus-within:border-ring focus-within:bg-background">
            <SearchIcon className="h-4 w-4 flex-shrink-0 text-muted-foreground" strokeWidth={1.5} />
            <input data-analytics-id="discovery.query.enter"
              type="text"
              placeholder={text('hero', 'searchPlaceholder')}
              aria-label={text('hero', 'searchPlaceholder')}
              className="min-w-0 flex-1 border-none bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground"
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  onSearch((event.target as HTMLInputElement).value)
                }
              }}
            />
          </div>

          <div className="space-y-2">
            {HERO_SKILLS.map((skill) => (
              <button data-analytics-id="discovery.suggestion.search"
                key={skill.name}
                type="button"
                className="group flex w-full items-center gap-3 rounded-lg border border-border/70 bg-background p-3 text-left transition-[transform,border-color,background-color,box-shadow] duration-150 hover:-translate-y-px hover:border-border hover:bg-secondary/60 hover:shadow-sm active:translate-y-0 active:scale-[0.995] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 motion-reduce:transform-none"
                onClick={() => onSearch(skill.name)}
              >
                <span className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg text-xs font-bold ${skill.badgeClassName}`}>
                  {skill.name.slice(0, 1).toUpperCase()}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-2">
                    <span className="truncate text-sm font-semibold text-foreground">{skill.name}</span>
                    <span className="font-mono text-xs text-muted-foreground">@{skill.namespace}</span>
                  </span>
                  <span className="block truncate text-xs text-muted-foreground">{t(skill.summaryKey)}</span>
                </span>
                <span className="flex-shrink-0 text-right">
                  <span className={`mb-1 inline-block h-2 w-2 rounded-full ${skill.statusClassName}`} />
                  <span className="block font-mono text-xs text-muted-foreground">v{skill.version}</span>
                </span>
              </button>
            ))}
          </div>

          <div className="mt-auto flex items-center justify-between border-t border-border/70 pt-3">
            <span className="text-xs text-muted-foreground">{text('hero', 'previewCaption')}</span>
            <Link data-analytics-id="navigation.skills.open"
              to="/search"
              search={{ q: '', sort: 'relevance', page: 0, starredOnly: false }}
              className="inline-flex items-center gap-1 text-xs font-medium text-foreground hover:text-primary"
            >
              {t('landing.experience.browseAll')} <ArrowRight className="h-3 w-3" />
            </Link>
          </div>
          {text('hero', 'previewCaption') !== t('landing.experience.demoSkills.caption') && <p className="mt-2 text-xs text-muted-foreground">{t('landing.experience.demoSkills.caption')}</p>}
        </div>
      </div>

      <div className="absolute -bottom-3 -right-3 hidden items-center gap-3 rounded-xl border border-border/70 bg-background p-3 shadow-lg sm:flex">
        <BrandMark className="h-9 w-9 rounded-lg bg-background ring-1 ring-border/70" />
        <div>
          <div className="text-sm font-semibold text-foreground">{text('hero', 'previewPublishTitle')}</div>
          <div className="text-xs text-muted-foreground">{text('hero', 'previewPublishDescription')}</div>
        </div>
      </div>
    </div>
  )
}

function EnterpriseSection() {
  const { t, i18n } = useTranslation()
  const { settings } = useSitePresentation()
  const text = (section: Parameters<typeof presentationText>[1], slot: string) => presentationText(settings, section, slot, i18n.resolvedLanguage, t)

  return (
    <section className="[overflow-wrap:anywhere] w-full bg-background py-16 md:py-20">
      <div className={APP_FRAME_CLASS_NAME}>
        <div className="text-center mb-12">
          <p className="mb-3 text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">{text('enterprise', 'eyebrow')}</p>
          <h2 className="mb-3 text-2xl font-medium tracking-tight text-foreground md:text-3xl">{text('enterprise', 'title')}</h2>
          <p className="text-muted-foreground">{text('enterprise', 'description')}</p>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          {presentationItemVisible(settings, 'enterprise', 'deployment') && <div className="md:col-span-2 md:row-span-2 flex flex-col rounded-xl border border-border/70 bg-card p-6 shadow-[var(--shadow-card)]">
            <div className="mb-3 flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-neutral-900 text-white">
                <Server className="h-4 w-4" />
              </div>
              <h3 className="font-semibold text-foreground">{text('enterprise', 'deploymentTitle')}</h3>
            </div>
            <p className="mb-4 text-sm leading-relaxed text-muted-foreground">
              {text('enterprise', 'deploymentDescription')}
            </p>
            <div className="flex-1 rounded-lg border border-border/70 bg-secondary/50 p-4">
              <div className="mb-3 flex flex-col items-stretch justify-between gap-2 sm:flex-row sm:items-center">
                <div className="flex flex-col gap-1.5 sm:flex-shrink-0">
                  <div className="mb-0.5 text-center text-[10px] text-muted-foreground">{text('enterprise', 'clientLayer')}</div>
                  <div className="rounded-md border border-border/70 bg-background px-3 py-1.5 text-center text-[11px] font-medium text-muted-foreground">Agent</div>
                  <div className="rounded-md border border-border/70 bg-background px-3 py-1.5 text-center text-[11px] font-medium text-muted-foreground">CLI</div>
                  <div className="rounded-md border border-border/70 bg-background px-3 py-1.5 text-center text-[11px] font-medium text-muted-foreground">Web</div>
                </div>
                <div className="flex h-8 items-center justify-center sm:h-auto sm:min-w-0 sm:flex-1">
                  <svg className="h-4 w-16 rotate-90 text-border sm:w-full sm:max-w-16 sm:rotate-0" fill="none" stroke="currentColor" viewBox="0 0 64 16"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" strokeDasharray="3 3" d="M0 8h54" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M50 4l6 4-6 4" /></svg>
                </div>
                <div className="flex flex-col items-center sm:flex-shrink-0">
                  <div className="mb-0.5 text-center text-[10px] text-muted-foreground">{text('enterprise', 'serviceLayer')}</div>
                  <div className="rounded-lg bg-neutral-900 px-3.5 py-3 text-center text-xs font-semibold leading-tight text-white">{text('enterprise', 'registryLabel').split('\n').map((line, index) => <span key={index} className="block [overflow-wrap:anywhere]">{line}</span>)}</div>
                </div>
                <div className="flex h-8 items-center justify-center sm:h-auto sm:min-w-0 sm:flex-1">
                  <svg className="h-4 w-16 rotate-90 text-border sm:w-full sm:max-w-16 sm:rotate-0" fill="none" stroke="currentColor" viewBox="0 0 64 16"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" strokeDasharray="3 3" d="M0 8h54" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M50 4l6 4-6 4" /></svg>
                </div>
                <div className="flex flex-col gap-1.5 sm:flex-shrink-0">
                  <div className="mb-0.5 text-center text-[10px] text-muted-foreground">{text('enterprise', 'storageLayer')}</div>
                  <div className="rounded-md border border-border/70 bg-background px-3 py-1.5 text-center text-[11px] font-medium text-muted-foreground">PostgreSQL</div>
                  <div className="rounded-md border border-border/70 bg-background px-3 py-1.5 text-center text-[11px] font-medium text-muted-foreground">Redis</div>
                  <div className="rounded-md border border-border/70 bg-background px-3 py-1.5 text-center text-[11px] font-medium text-muted-foreground">MinIO / S3</div>
                </div>
              </div>
              <div className="border-t border-dashed border-border/70 pt-2.5 text-center text-[10px] text-muted-foreground">{text('enterprise', 'networkBoundary')}</div>
            </div>
          </div>}

          {presentationItemVisible(settings, 'enterprise', 'security') && <div className="rounded-xl border border-border/70 bg-card p-5 shadow-[var(--shadow-card)]">
            <div className="mb-2 flex items-center gap-3">
              <Shield className="h-5 w-5 text-foreground" />
              <h3 className="text-sm font-semibold text-foreground">{text('enterprise', 'securityTitle')}</h3>
            </div>
            <p className="text-sm leading-relaxed text-muted-foreground">{text('enterprise', 'securityDescription')}</p>
          </div>}

          {presentationItemVisible(settings, 'enterprise', 'rbac') && <div className="rounded-xl border border-border/70 bg-card p-5 shadow-[var(--shadow-card)]">
            <div className="mb-2 flex items-center gap-3">
              <Lock className="h-5 w-5 text-foreground" />
              <h3 className="text-sm font-semibold text-foreground">{text('enterprise', 'rbacTitle')}</h3>
            </div>
            <p className="text-sm leading-relaxed text-muted-foreground">{text('enterprise', 'rbacDescription')}</p>
          </div>}

          {presentationItemVisible(settings, 'enterprise', 'access') && <div className="rounded-xl border border-border/70 bg-card p-5 shadow-[var(--shadow-card)]">
            <div className="mb-2 flex items-center gap-3">
              <Settings className="h-5 w-5 text-foreground" />
              <h3 className="text-sm font-semibold text-foreground">{text('enterprise', 'accessTitle')}</h3>
            </div>
            <p className="text-sm leading-relaxed text-muted-foreground">{text('enterprise', 'accessDescription')}</p>
          </div>}

          {presentationItemVisible(settings, 'enterprise', 'audit') && <div className="rounded-xl border border-border/70 bg-card p-5 shadow-[var(--shadow-card)]">
            <div className="mb-2 flex items-center gap-3">
              <Monitor className="h-5 w-5 text-foreground" />
              <h3 className="text-sm font-semibold text-foreground">{text('enterprise', 'auditTitle')}</h3>
            </div>
            <p className="text-sm leading-relaxed text-muted-foreground">{text('enterprise', 'auditDescription')}</p>
          </div>}

          {presentationItemVisible(settings, 'enterprise', 'openSource') && <div className="rounded-xl border border-border/70 bg-card p-5 shadow-[var(--shadow-card)]">
            <div className="mb-2 flex items-center gap-3">
              <GitBranch className="h-5 w-5 text-foreground" />
              <h3 className="text-sm font-semibold text-foreground">{text('enterprise', 'openSourceTitle')}</h3>
            </div>
            <p className="text-sm leading-relaxed text-muted-foreground">{text('enterprise', 'openSourceDescription')}</p>
          </div>}
        </div>
      </div>
    </section>
  )
}

/**
 * Marketing-style landing page for unauthenticated and first-time visitors.
 *
 * The page mixes static positioning content with live skill queries so popular and latest skills
 * stay aligned with the current registry state.
 */
export function LandingPage() {
  return <LandingIntroduction />
}

function LandingIntroduction() {
  const { t, i18n } = useTranslation()
  const { settings } = useSitePresentation()
  const text = (section: Parameters<typeof presentationText>[1], slot: string) => presentationText(settings, section, slot, i18n.resolvedLanguage, t)
  const navigate = useNavigate()

  const { data: popularSkills, isLoading: isLoadingPopular } = useSearchSkills({
    sort: 'downloads',
    size: 6,
  }, settings.sections.popular.enabled)

  const { data: latestSkills, isLoading: isLoadingLatest } = useSearchSkills({
    sort: 'newest',
    size: 6,
  }, settings.sections.latest.enabled)

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}` })
  }

  const heroView = useInView()
  const featuresView = useInView()
  const quickStartView = useInView()
  const enterpriseView = useInView()
  const popularView = useInView()
  const latestView = useInView()

  const handleSearch = (query: string) => {
    const normalized = normalizeSearchQuery(query)
    navigate({
      to: '/search',
      search: { q: normalized, sort: 'relevance', page: 0, starredOnly: false },
    })
  }

  const features = [
    {
      icon: <Shield className="h-5 w-5" strokeWidth={1.75} />,
      id: 'secure',
      title: text('capabilities', 'secureTitle'),
      description: text('capabilities', 'secureDescription'),
    },
    {
      icon: <Users className="h-5 w-5" strokeWidth={1.75} />,
      id: 'community',
      title: text('capabilities', 'communityTitle'),
      description: text('capabilities', 'communityDescription'),
    },
    {
      icon: <PackageOpen className="h-5 w-5" strokeWidth={1.75} />,
      id: 'integration',
      title: text('capabilities', 'integrationTitle'),
      description: text('capabilities', 'integrationDescription'),
    },
    {
      icon: <GitBranch className="h-5 w-5" strokeWidth={1.75} />,
      id: 'versionControl',
      title: text('capabilities', 'versionControlTitle'),
      description: text('capabilities', 'versionControlDescription'),
    },
    {
      icon: <Terminal className="h-5 w-5" strokeWidth={1.75} />,
      id: 'cli',
      title: text('capabilities', 'cliTitle'),
      description: text('capabilities', 'cliDescription'),
    },
    {
      icon: <Settings className="h-5 w-5" strokeWidth={1.75} />,
      id: 'governance',
      title: text('capabilities', 'governanceTitle'),
      description: text('capabilities', 'governanceDescription'),
    },
  ]

  const visibleFeatures = features.filter(feature => presentationItemVisible(settings, 'capabilities', feature.id))
  const featureColumns = visibleFeatures.length === 1
    ? 'md:grid-cols-1 lg:grid-cols-1'
    : [2, 4].includes(visibleFeatures.length) ? 'md:grid-cols-2 lg:grid-cols-2' : 'md:grid-cols-2 lg:grid-cols-3'

  const heroHighlights = [
    { icon: <PackageOpen className="h-4 w-4" strokeWidth={1.5} />, label: text('hero', 'highlightSelfHosted') },
    { icon: <Shield className="h-4 w-4" strokeWidth={1.5} />, label: text('hero', 'highlightGovernance') },
    { icon: <Terminal className="h-4 w-4" strokeWidth={1.5} />, label: text('hero', 'highlightMultiClient') },
    { icon: <Clock3 className="h-4 w-4" strokeWidth={1.5} />, label: text('hero', 'highlightTraceable') },
  ]

  const popularTitle = text('popular', 'title')
  const popularDescription = text('popular', 'description')
  const latestTitle = text('latest', 'title')
  const latestDescription = text('latest', 'description')

  return (
    <>
      {settings.sections.hero.enabled && <main ref={heroView.ref} className={`[overflow-wrap:anywhere] relative z-10 w-full overflow-hidden py-16 scroll-fade-up${heroView.inView ? ' in-view' : ''} md:py-20`}>
        <div className="absolute inset-0 -z-10">
          <div className="absolute left-1/2 top-0 h-[400px] w-[900px] -translate-x-1/2 rounded-full bg-secondary blur-3xl" />
        </div>
        <div className={`${APP_FRAME_CLASS_NAME} grid grid-cols-1 items-start gap-12 ${presentationItemVisible(settings, 'hero', 'preview') ? 'lg:grid-cols-2' : ''} lg:items-center lg:gap-16`}>
          <div>
            <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-border/70 bg-secondary/80 px-3 py-1.5 text-xs font-medium text-muted-foreground">
              <span className="h-1.5 w-1.5 rounded-full bg-blue-500" />
              {text('hero', 'badge')}
            </div>

            <h1 className="mb-5 max-w-2xl whitespace-pre-wrap [overflow-wrap:anywhere] text-4xl font-medium leading-[1.12] tracking-tight text-foreground md:text-5xl">
              {text('hero', 'title')}
            </h1>

            <p className="mb-8 max-w-xl whitespace-pre-wrap [overflow-wrap:anywhere] text-lg leading-relaxed text-muted-foreground">
              {text('hero', 'description')}
            </p>

            <div className="mb-8 flex flex-wrap gap-3">
              {presentationItemVisible(settings, 'hero', 'primaryAction') && <Link data-analytics-id="skill.publish.open" to="/dashboard/publish" className="btn-pill btn-pill-primary">
                {text('hero', 'primaryActionLabel')}
              </Link>}
              {presentationItemVisible(settings, 'hero', 'secondaryAction') && <Link data-analytics-id="navigation.skills.open"
                to="/search"
                search={{ q: '', sort: 'relevance', page: 0, starredOnly: false }}
                className="group btn-pill btn-pill-outline inline-flex items-center gap-2"
              >
                {text('hero', 'secondaryActionLabel')} <ArrowRight className="h-4 w-4 transition-transform duration-150 group-hover:translate-x-0.5 motion-reduce:transform-none" />
              </Link>}
            </div>

            {presentationItemVisible(settings, 'hero', 'highlights') && <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm text-muted-foreground">
              {heroHighlights.map((item) => (
                <div key={item.label} className="flex items-center gap-2">
                  <span className="text-muted-foreground">{item.icon}</span>
                  <span>{item.label}</span>
                </div>
              ))}
            </div>}
          </div>

          {presentationItemVisible(settings, 'hero', 'preview') && <HeroBrowserMockup onSearch={handleSearch} />}
        </div>
      </main>}

      {settings.sections.hero.enabled && <div className={APP_FRAME_CLASS_NAME}><div className="h-px bg-border/70" /></div>}

      {settings.sections.capabilities.enabled && <section ref={featuresView.ref} className={`[overflow-wrap:anywhere] relative z-10 w-full bg-background py-16 scroll-fade-up${featuresView.inView ? ' in-view' : ''} md:py-20`}>
        <div className={APP_FRAME_CLASS_NAME}>
          <div className="mb-12 max-w-3xl">
            <p className="mb-3 text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">{text('capabilities', 'eyebrow')}</p>
            <h2 className="mb-4 text-3xl font-medium tracking-tight text-foreground md:text-4xl">
              {text('capabilities', 'title')}
            </h2>
            <p className="max-w-2xl text-lg leading-relaxed text-muted-foreground">
              {text('capabilities', 'description')}
            </p>
          </div>

          {visibleFeatures.length > 0 && <div className={`grid grid-cols-1 overflow-hidden rounded-2xl border border-border/70 bg-border/70 ${featureColumns}`}>
            {visibleFeatures.map((feature, index) => (
              <div key={feature.id} className={`bg-card p-7 transition-colors hover:bg-secondary/50${index === visibleFeatures.length - 1 && visibleFeatures.length === 3 ? ' md:col-span-2 lg:col-span-1' : index === visibleFeatures.length - 1 && visibleFeatures.length === 5 ? ' md:col-span-2 lg:col-span-2' : ''}`}>
                <div className="mb-5 flex h-10 w-10 items-center justify-center rounded-lg bg-secondary text-foreground">
                  {feature.icon}
                </div>
                <h3 className="mb-2 text-base font-semibold text-foreground">{feature.title}</h3>
                <p className="text-sm leading-relaxed text-muted-foreground">{feature.description}</p>
              </div>
            ))}
          </div>}
        </div>
      </section>}

      {settings.sections.quickStart.enabled && <div ref={quickStartView.ref} className={`scroll-fade-up${quickStartView.inView ? ' in-view' : ''}`}>
        <LandingQuickStartSection />
      </div>}

      {settings.sections.curated?.enabled !== false && <HomepageRecommendations />}

      {settings.sections.popular.enabled && <section ref={popularView.ref} className={`[overflow-wrap:anywhere] relative z-10 w-full bg-background py-16 scroll-fade-up${popularView.inView ? ' in-view' : ''} md:py-20`}>
        <div className={`${APP_FRAME_CLASS_NAME} space-y-6`}>
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="mb-3 text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">{text('popular', 'eyebrow')}</p>
              <h2 className="mb-2 text-3xl font-medium tracking-tight text-foreground">{popularTitle}</h2>
              <p className="text-sm text-muted-foreground">{popularDescription}</p>
            </div>
            <Button data-analytics-id="discovery.popular.open"
              variant="ghost"
              className="h-auto min-h-9 whitespace-normal"
              onClick={() => navigate({ to: '/search', search: { q: '', sort: 'downloads', page: 0, starredOnly: false } })}
            >
              {text('popular', 'viewAllLabel')}
            </Button>
          </div>
          {isLoadingPopular ? (
            <SkeletonList count={6} />
          ) : (
            <div className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3">
              {popularSkills?.items.map((skill, idx) => (
                <div key={skill.id} className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                  <SkillCard analyticsId="skill.card.open" skill={skill} onClick={() => handleSkillClick(skill.namespace, skill.slug)} />
                </div>
              ))}
            </div>
          )}
        </div>
      </section>}

      {settings.sections.latest.enabled && <section ref={latestView.ref} className={`[overflow-wrap:anywhere] relative z-10 w-full border-y border-border/70 bg-secondary/70 py-16 scroll-fade-up${latestView.inView ? ' in-view' : ''} md:py-20`}>
        <div className={`${APP_FRAME_CLASS_NAME} space-y-6`}>
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="mb-3 text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">{text('latest', 'eyebrow')}</p>
              <h2 className="mb-2 text-3xl font-medium tracking-tight text-foreground">{latestTitle}</h2>
              <p className="text-sm text-muted-foreground">{latestDescription}</p>
            </div>
            <Button data-analytics-id="discovery.latest.open"
              variant="ghost"
              className="h-auto min-h-9 whitespace-normal"
              onClick={() => navigate({ to: '/search', search: { q: '', sort: 'newest', page: 0, starredOnly: false } })}
            >
              {text('latest', 'viewAllLabel')}
            </Button>
          </div>
          {isLoadingLatest ? (
            <SkeletonList count={6} />
          ) : (
            <div className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3">
              {latestSkills?.items.map((skill, idx) => (
                <div key={skill.id} className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                  <SkillCard analyticsId="skill.card.open" skill={skill} onClick={() => handleSkillClick(skill.namespace, skill.slug)} />
                </div>
              ))}
            </div>
          )}
        </div>
      </section>}

      {settings.sections.enterprise.enabled && <div ref={enterpriseView.ref} className={`scroll-fade-up${enterpriseView.inView ? ' in-view' : ''}`}>
        <EnterpriseSection />
      </div>}

      {settings.sections.cta.enabled && <section className="[overflow-wrap:anywhere] relative z-10 w-full border-t border-border/70 bg-background py-16 text-center md:py-20">
        <div className={APP_FRAME_CLASS_NAME}>
          <h2 className="mb-3 text-2xl font-medium tracking-tight text-foreground md:text-3xl">{text('cta', 'title')}</h2>
          <p className="mb-8 text-muted-foreground">{text('cta', 'description')}</p>
          <div className="flex flex-wrap items-center justify-center gap-3">
            {presentationItemVisible(settings, 'cta', 'primaryAction') && <a data-analytics-id="navigation.repository.open" href={presentationLinkUrl(settings, 'repository')} target="_blank" rel="noreferrer" className="btn-pill btn-pill-primary">
              {text('cta', 'primaryActionLabel')}
            </a>}
            {presentationItemVisible(settings, 'cta', 'secondaryAction') && <a data-analytics-id="navigation.documentation.open" href={presentationLinkUrl(settings, 'documentation')} target="_blank" rel="noreferrer" className="group btn-pill btn-pill-outline inline-flex items-center gap-2">
              {text('cta', 'secondaryActionLabel')} <CheckCircle2 className="h-4 w-4" />
            </a>}
          </div>
        </div>
      </section>}
    </>
  )
}
