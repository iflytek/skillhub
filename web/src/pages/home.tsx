import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { QuickStartSection } from '@/shared/components/quick-start'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { normalizeSearchQuery } from '@/shared/lib/search-query'

export function HomePage() {
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

  const handleSearch = (query: string) => {
    navigate({ to: '/search', search: { q: normalizeSearchQuery(query), sort: 'relevance', page: 0, starredOnly: false } })
  }

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}` })
  }

  return (
    <div className="space-y-16">
      <div className="text-center space-y-8 py-10 animate-fade-up">
        <div className="max-w-2xl mx-auto animate-fade-up delay-1">
          <SearchBar onSearch={handleSearch} />
        </div>

        <div className="flex items-center justify-center gap-4 animate-fade-up delay-2">
          <button
            className="px-8 py-3.5 rounded-xl text-base font-medium text-white bg-brand-gradient shadow-sm hover:opacity-95 transition-opacity"
            onClick={() => navigate({ to: '/search', search: { q: '', sort: 'relevance', page: 0, starredOnly: false } })}
          >
            {t('home.browseSkills')}
          </button>
          <button
            className="px-8 py-3.5 rounded-xl text-base font-medium border transition-colors"
            style={{ background: 'var(--bg-secondary-btn, #F7FAFC)', borderColor: 'hsl(var(--muted-foreground))', color: 'hsl(var(--muted-foreground))' }}
            onClick={() => navigate({ to: '/dashboard/publish' })}
          >
            {t('home.publishSkill')}
          </button>
        </div>
      </div>

      {/* Popular Downloads Section */}
      <section className="space-y-6 animate-fade-up">
        <h2 className="text-3xl font-bold tracking-tight" style={{ color: 'hsl(var(--foreground))' }}>
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
      </section>

      {/* Latest Releases Section */}
      <section className="space-y-6 animate-fade-up">
        <h2 className="text-3xl font-bold tracking-tight" style={{ color: 'hsl(var(--foreground))' }}>
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
      </section>

      {/* Quick Start Section */}
      <QuickStartSection ns="home" />
    </div>
  )
}
