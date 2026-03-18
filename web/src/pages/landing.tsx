import { Link, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { PackageOpen, Terminal, Shield, Users, GitBranch, Search as SearchIcon, Settings } from 'lucide-react'
import { QuickStartSection } from '@/shared/components/quick-start'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useInView } from '@/shared/hooks/use-in-view'
import { Button } from '@/shared/ui/button'

export function LandingPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const { data: popularSkills, isLoading: isLoadingPopular } = useSearchSkills({
    sort: 'downloads',
    size: 6,
  })

  const { data: latestSkills, isLoading: isLoadingLatest } = useSearchSkills({
    sort: 'newest',
    size: 6,
  })

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${slug}` })
  }

  const heroView = useInView()
  const statsView = useInView()
  const featuresView = useInView()
  const quickStartView = useInView()
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
      icon: <Shield className="w-6 h-6 text-white" strokeWidth={2} />,
      title: t('landing.features.secure.title'),
      description: t('landing.features.secure.description'),
    },
    {
      icon: <Users className="w-6 h-6 text-white" strokeWidth={2} />,
      title: t('landing.features.community.title'),
      description: t('landing.features.community.description'),
    },
    {
      icon: <PackageOpen className="w-6 h-6 text-white" strokeWidth={2} />,
      title: t('landing.features.integration.title'),
      description: t('landing.features.integration.description'),
    },
    {
      icon: <GitBranch className="w-6 h-6 text-white" strokeWidth={2} />,
      title: t('landing.features.versionControl.title', { defaultValue: '版本控制' }),
      description: t('landing.features.versionControl.description', { defaultValue: '完善的版本管理和发布流程，确保技能包的质量和可追溯性。' }),
    },
    {
      icon: <Terminal className="w-6 h-6 text-white" strokeWidth={2} />,
      title: t('landing.features.cli.title', { defaultValue: 'CLI 工具' }),
      description: t('landing.features.cli.description', { defaultValue: '强大的命令行工具，支持快速发布、安装和管理技能包。' }),
    },
    {
      icon: <Settings className="w-6 h-6 text-white" strokeWidth={2} />,
      title: t('landing.features.governance.title', { defaultValue: '审核治理' }),
      description: t('landing.features.governance.description', { defaultValue: '内置审核流程和权限管理，保障企业级技能质量。' }),
    },
  ]

  const stats = [
    { value: '1000+', label: t('landing.stats.skills', { defaultValue: '项目库' }) },
    { value: '50K+', label: t('landing.stats.downloads', { defaultValue: '下载量' }) },
    { value: '200+', label: t('landing.stats.teams', { defaultValue: '团队' }) },
  ]

  return (
    <>
      {/* Hero Section */}
      <main ref={heroView.ref} className={`relative z-10 flex flex-col items-center pt-16 pb-20 px-4 md:pt-24 scroll-fade-up${heroView.inView ? ' in-view' : ''}`}>
        <h1 className="text-5xl md:text-7xl font-bold tracking-tight text-brand-gradient mb-4">
          SkillHub
        </h1>
        <h2
          className="text-xl md:text-2xl font-semibold tracking-tight text-center mb-3"
          style={{ color: 'hsl(var(--foreground))' }}
        >
          {t('landing.hero.title')}
        </h2>
        <p
          className="text-base md:text-lg text-center max-w-2xl mb-10 leading-relaxed"
          style={{ color: 'hsl(var(--text-secondary))' }}
        >
          {t('landing.hero.subtitle')}
        </p>

        {/* Search box */}
        <div className="w-full max-w-2xl mb-8">
          <div
            className="flex items-center bg-white rounded-xl border shadow-sm px-5 py-3.5"
            style={{ borderColor: 'hsl(var(--border))' }}
          >
            <SearchIcon className="w-5 h-5 flex-shrink-0 mr-3" style={{ color: 'hsl(var(--text-placeholder))' }} strokeWidth={1.5} />
            <input
              type="text"
              placeholder={t('landing.hero.searchPlaceholder')}
              className="hero-input flex-1 bg-transparent outline-none text-base"
              style={{ color: 'hsl(var(--foreground))' }}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  handleSearch((e.target as HTMLInputElement).value)
                }
              }}
            />
          </div>
        </div>

        {/* CTA buttons */}
        <div className="flex flex-wrap justify-center gap-4 mb-14">
          <Link
            to="/search"
            search={{ q: '', sort: 'relevance', page: 0, starredOnly: false }}
            className="px-8 py-3.5 rounded-xl text-base font-medium text-white bg-brand-gradient shadow-sm hover:opacity-95 transition-opacity"
          >
            {t('landing.hero.exploreSkills')}
          </Link>
          <Link
            to="/dashboard/publish"
            className="px-8 py-3.5 rounded-xl text-base font-medium border transition-colors"
            style={{
              background: 'hsl(var(--secondary))',
              borderColor: 'hsl(var(--muted-foreground))',
              color: 'hsl(var(--muted-foreground))',
            }}
          >
            {t('landing.hero.publishSkill', { defaultValue: '开始构建' })}
          </Link>
        </div>

        {/* Stats */}
        <div ref={statsView.ref} className={`flex flex-row justify-center gap-16 md:gap-24 scroll-fade-up${statsView.inView ? ' in-view' : ''}`} style={{ transitionDelay: '0.15s' }}>
          {stats.map((stat) => (
            <div key={stat.label} className="flex flex-col items-center">
              <span className="text-3xl md:text-4xl font-bold tracking-tight text-brand-gradient mb-1">
                {stat.value}
              </span>
              <span className="text-sm font-normal" style={{ color: 'hsl(var(--foreground))' }}>
                {stat.label}
              </span>
            </div>
          ))}
        </div>
      </main>

      {/* Features Section */}
      <section ref={featuresView.ref} className={`relative z-10 w-full py-20 md:py-24 px-6 scroll-fade-up${featuresView.inView ? ' in-view' : ''}`} style={{ background: 'var(--bg-page, hsl(var(--background)))' }}>
        <div className="max-w-6xl mx-auto">
          <div className="text-center mb-14">
            <h2 className="text-3xl md:text-4xl font-bold tracking-tight mb-3" style={{ color: 'hsl(var(--foreground))' }}>
              {t('landing.whySkillHub.title', { defaultValue: '为什么选择 SkillHub' })}
            </h2>
            <p className="text-base md:text-lg max-w-2xl mx-auto leading-relaxed" style={{ color: 'hsl(var(--text-secondary))' }}>
              {t('landing.whySkillHub.subtitle', { defaultValue: '专为企业打造的私有化 Agent 技能管理平台' })}
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {features.map((feature) => (
              <div
                key={feature.title}
                className="bg-white rounded-xl p-8 border shadow-sm transition-shadow hover:shadow-md"
                style={{ borderColor: 'hsl(var(--border-card))' }}
              >
                <div className="feature-icon w-12 h-12 rounded-2xl flex items-center justify-center mb-6 mx-auto bg-brand-gradient">
                  {feature.icon}
                </div>
                <h3 className="text-lg font-semibold text-center mb-3" style={{ color: 'hsl(var(--foreground))' }}>
                  {feature.title}
                </h3>
                <p className="text-sm text-center leading-relaxed" style={{ color: 'hsl(var(--text-secondary))' }}>
                  {feature.description}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Quick Start */}
      <div ref={quickStartView.ref} className={`scroll-fade-up${quickStartView.inView ? ' in-view' : ''}`}>
        <QuickStartSection variant="landing" ns="landing" />
      </div>

      {/* Popular Downloads Section */}
      <section ref={popularView.ref} className={`relative z-10 w-full py-20 md:py-24 px-6 scroll-fade-up${popularView.inView ? ' in-view' : ''}`} style={{ background: 'var(--bg-page, hsl(var(--background)))' }}>
        <div className="max-w-6xl mx-auto space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-3xl font-bold tracking-tight mb-2" style={{ color: 'hsl(var(--foreground))' }}>
                {t('home.popularTitle')}
              </h2>
              <p style={{ color: 'hsl(var(--text-secondary))' }}>{t('home.popularDescription')}</p>
            </div>
            <Button
              variant="ghost"
              onClick={() => navigate({ to: '/search', search: { q: '', sort: 'downloads', page: 0, starredOnly: false } })}
            >
              {t('home.viewAll')}
            </Button>
          </div>
          {isLoadingPopular ? (
            <SkeletonList count={6} />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {popularSkills?.items.map((skill, idx) => (
                <div key={skill.id} className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                  <SkillCard
                    skill={skill}
                    onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                  />
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* Latest Releases Section */}
      <section ref={latestView.ref} className={`relative z-10 w-full py-20 md:py-24 px-6 scroll-fade-up${latestView.inView ? ' in-view' : ''}`} style={{ background: 'var(--bg-page, hsl(var(--background)))' }}>
        <div className="max-w-6xl mx-auto space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-3xl font-bold tracking-tight mb-2" style={{ color: 'hsl(var(--foreground))' }}>
                {t('home.latestTitle')}
              </h2>
              <p style={{ color: 'hsl(var(--text-secondary))' }}>{t('home.latestDescription')}</p>
            </div>
            <Button
              variant="ghost"
              onClick={() => navigate({ to: '/search', search: { q: '', sort: 'newest', page: 0, starredOnly: false } })}
            >
              {t('home.viewAll')}
            </Button>
          </div>
          {isLoadingLatest ? (
            <SkeletonList count={6} />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {latestSkills?.items.map((skill, idx) => (
                <div key={skill.id} className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                  <SkillCard
                    skill={skill}
                    onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                  />
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </>
  )
}
