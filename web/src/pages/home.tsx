import { useNavigate } from '@tanstack/react-router'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'

export function HomePage() {
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
    navigate({ to: '/search', search: { q: query, sort: 'relevance', page: 1 } })
  }

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: '/@$namespace/$slug', params: { namespace, slug } })
  }

  return (
    <div className="space-y-12">
      {/* Hero Section */}
      <div className="text-center space-y-6 py-12">
        <div className="space-y-2">
          <h1 className="text-5xl font-bold">SkillHub</h1>
          <p className="text-xl text-muted-foreground">技能注册中心</p>
        </div>
        <div className="max-w-2xl mx-auto">
          <SearchBar onSearch={handleSearch} />
        </div>
      </div>

      {/* Popular Downloads Section */}
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold">热门下载</h2>
        {isLoadingPopular ? (
          <SkeletonList count={6} />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {popularSkills?.items.map((skill) => (
              <SkillCard
                key={skill.id}
                skill={skill}
                onClick={() => handleSkillClick(skill.namespace, skill.slug)}
              />
            ))}
          </div>
        )}
      </section>

      {/* Latest Releases Section */}
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold">最新发布</h2>
        {isLoadingLatest ? (
          <SkeletonList count={6} />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {latestSkills?.items.map((skill) => (
              <SkillCard
                key={skill.id}
                skill={skill}
                onClick={() => handleSkillClick(skill.namespace, skill.slug)}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
