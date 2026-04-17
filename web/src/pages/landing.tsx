import { Link, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { SearchBar } from '@/features/search/search-bar'
import { LandingQuickStartSection } from '@/shared/components/landing-quick-start'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { normalizeSearchQuery } from '@/shared/lib/search-query'

/**
 * Home route for anonymous and first-time visitors.
 *
 * Keep this page intentionally minimal: search + actions + quick start + skill lists.
 */
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
    navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}` })
  }

  const handleSearch = (query: string) => {
    const normalized = normalizeSearchQuery(query)
    navigate({
      to: '/search',
      search: { q: normalized, sort: 'relevance', page: 0, starredOnly: false },
    })
  }

  return (
    <div className="space-y-8 md:space-y-10">
      <section className="text-center space-y-6 py-6 md:py-8 animate-fade-up">
        <div className="max-w-2xl mx-auto animate-fade-up delay-1">
          <SearchBar onSearch={handleSearch} placeholder={t('landing.hero.searchPlaceholder')} />
        </div>

        <div className="flex flex-wrap justify-center gap-4 animate-fade-up delay-2">
          <Link
            to="/search"
            search={{ q: '', sort: 'relevance', page: 0, starredOnly: false }}
            className="px-8 py-3.5 rounded-xl text-base font-medium bg-primary text-primary-foreground shadow-sm hover:bg-primary/90 transition-colors"
          >
            {t('landing.hero.exploreSkills')}
          </Link>
          <Link
            to="/dashboard/publish"
            className="px-8 py-3.5 rounded-xl text-base font-medium border border-border bg-secondary text-secondary-foreground transition-colors hover:bg-secondary/80"
          >
            {t('landing.hero.publishSkill', { defaultValue: 'Publish Skill' })}
          </Link>
        </div>
      </section>

      <div>
        <LandingQuickStartSection />
      </div>

      <section className="relative z-10 w-full bg-background py-10 md:py-12 px-6 animate-fade-up">
        <div className="max-w-6xl mx-auto space-y-6">
          <h2 className="text-3xl font-bold tracking-tight text-foreground">
            {t('home.popularTitle')}
          </h2>
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

      <section className="relative z-10 w-full bg-background py-12 md:py-14 px-6 animate-fade-up">
        <div className="max-w-6xl mx-auto space-y-6">
          <h2 className="text-3xl font-bold tracking-tight text-foreground">
            {t('home.latestTitle')}
          </h2>
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
    </div>
  )
}
