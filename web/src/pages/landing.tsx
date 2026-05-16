import { useMemo, useState } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { Search as SearchIcon } from 'lucide-react'
import { LandingQuickStartSection } from '@/shared/components/landing-quick-start'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useVisibleLabels } from '@/shared/hooks/use-label-queries'

const PRIMARY_FILTERS = [
  { key: 'all', sort: 'relevance' as const, titleKey: 'landing.category.all', fallback: 'All' },
  { key: 'downloads', sort: 'downloads' as const, titleKey: 'landing.category.downloads', fallback: 'Popular' },
  { key: 'newest', sort: 'newest' as const, titleKey: 'landing.category.newest', fallback: 'Newest' },
  { key: 'stars', sort: 'relevance' as const, titleKey: 'landing.category.stars', fallback: 'Featured' },
]

export function LandingPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [primaryFilter, setPrimaryFilter] = useState('downloads')
  const [selectedLabels, setSelectedLabels] = useState<string[]>([])

  const activePrimaryFilter = PRIMARY_FILTERS.find((item) => item.key === primaryFilter) ?? PRIMARY_FILTERS[0]
  const selectedLabel = selectedLabels[0]

  const { data: visibleLabels } = useVisibleLabels()
  const { data: skills, isLoading: isLoadingSkills } = useSearchSkills({
    sort: activePrimaryFilter.sort,
    size: 9,
    label: selectedLabel || undefined,
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

  const quickLabels = useMemo(() => (visibleLabels ?? []).slice(0, 9), [visibleLabels])

  return (
    <main className="relative z-10 px-4 pt-12 pb-20 md:px-8 lg:px-[60px] md:pt-20">
      <section className="max-w-7xl mx-auto pb-20">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-10 lg:gap-20 items-stretch">
          <div className="flex flex-col">
            <h1 className="text-6xl md:text-7xl font-bold tracking-tight text-brand-gradient mb-4">
              SkillHub
            </h1>
            <h2
              className="text-xl md:text-2xl font-semibold tracking-tight mb-3"
              style={{ color: 'hsl(var(--foreground))' }}
            >
              {t('landing.hero.title')}
            </h2>
            <p
              className="text-base md:text-lg max-w-2xl mb-8 leading-relaxed"
              style={{ color: 'hsl(var(--text-secondary))' }}
            >
              {t('landing.hero.subtitle')}
            </p>

            <div className="w-full max-w-xl mb-8">
              <div
                className="search-shell flex items-center bg-white rounded-xl border-2 shadow-sm px-5 py-3.5"
                style={{ borderColor: '#158940', boxShadow: '0 8px 24px rgba(15,23,42,0.06)' }}
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

            <div className="flex flex-wrap gap-4 mt-auto">
              <Link
                to="/search"
                search={{ q: '', sort: 'relevance', page: 0, starredOnly: false }}
                className="hover-lift px-8 py-3.5 rounded-xl text-base font-medium text-white bg-brand-gradient shadow-sm"
              >
                {t('landing.hero.exploreSkills')}
              </Link>
              <Link
                to="/dashboard/publish"
                className="hover-lift px-8 py-3.5 rounded-xl text-base font-medium border-2 bg-white"
                style={{ borderColor: '#158940', color: 'hsl(var(--primary))' }}
              >
                {t('landing.hero.publishSkill')}
              </Link>
            </div>
          </div>

          <div className="flex justify-center lg:justify-end">
            <LandingQuickStartSection compact />
          </div>
        </div>
      </section>

      <section className="max-w-7xl mx-auto space-y-6">
        <div className="flex items-center justify-between gap-2 flex-nowrap min-w-0">
          <div className="flex gap-1.5 flex-nowrap min-w-0">
            {PRIMARY_FILTERS.map((filter) => {
              const active = filter.key === primaryFilter
              return (
                <button
                  key={filter.key}
                  type="button"
                  onClick={() => setPrimaryFilter(filter.key)}
                  className="hover-lift px-3 py-1.5 rounded-full text-xs sm:text-sm sm:px-4 sm:py-2 font-medium border whitespace-nowrap"
                  style={{
                    background: active ? 'var(--brand-gradient)' : '#ffffff',
                    color: active ? '#ffffff' : 'hsl(var(--foreground))',
                    borderColor: active ? 'transparent' : 'hsl(var(--border))',
                  }}
                >
                  {t(filter.titleKey, { defaultValue: filter.fallback })}
                </button>
              )
            })}
          </div>
          <Link
            to="/search"
            search={{ q: '', sort: activePrimaryFilter.sort, page: 0, starredOnly: false }}
            className="flex-shrink-0 whitespace-nowrap text-sm hover:underline"
            style={{ color: 'hsl(var(--primary))' }}
          >
            {t('home.viewAll')}
          </Link>
        </div>

        <div className="flex flex-wrap gap-2">
          {quickLabels.map((label) => {
            const active = selectedLabels.includes(label.slug)
            return (
              <button
                key={label.slug}
                type="button"
                onClick={() => setSelectedLabels(active ? [] : [label.slug])}
                className="label-tag px-3 py-1.5 rounded-full text-xs font-medium border"
                style={{
                  background: active ? 'hsl(var(--primary))' : '#ffffff',
                  color: active ? '#ffffff' : 'hsl(var(--text-secondary))',
                  borderColor: active ? 'hsl(var(--primary))' : 'hsl(var(--border))',
                }}
              >
                {label.displayName}
              </button>
            )
          })}
        </div>

        {isLoadingSkills ? (
          <SkeletonList count={9} />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {skills?.items.map((skill) => (
              <SkillCard
                key={skill.id}
                skill={skill}
                onClick={() => handleSkillClick(skill.namespace, skill.slug)}
              />
            ))}
          </div>
        )}
      </section>
    </main>
  )
}
