import { useNavigate } from '@tanstack/react-router'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { Button } from '@/shared/ui/button'

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
    navigate({ to: '/search', search: { q: query, sort: 'relevance', page: 0 } })
  }

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${slug}` })
  }

  return (
    <div className="space-y-20">
      {/* Hero Section */}
      <div className="text-center space-y-8 py-16 animate-fade-up">
        <div className="space-y-4">
          <h1 className="text-6xl md:text-7xl lg:text-8xl font-bold font-display text-gradient-hero leading-tight">
            SkillHub
          </h1>
          <p className="text-xl md:text-2xl text-muted-foreground font-light max-w-2xl mx-auto">
            现代化的技能注册中心
          </p>
          <p className="text-base text-muted-foreground/80 max-w-xl mx-auto">
            为开发者提供高效的技能管理、分发和协作平台
          </p>
        </div>

        <div className="max-w-2xl mx-auto animate-fade-up delay-1">
          <SearchBar onSearch={handleSearch} />
        </div>

        <div className="flex items-center justify-center gap-4 animate-fade-up delay-2">
          <Button size="lg" onClick={() => navigate({ to: '/search', search: { q: '', sort: 'relevance', page: 0 } })}>
            浏览技能
          </Button>
          <Button size="lg" variant="outline" onClick={() => navigate({ to: '/dashboard/publish' })}>
            发布技能
          </Button>
        </div>
      </div>

      {/* Popular Downloads Section */}
      <section className="space-y-6 animate-fade-up delay-3">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-3xl font-bold font-heading text-foreground mb-2">热门下载</h2>
            <p className="text-muted-foreground">社区最受欢迎的技能</p>
          </div>
          <Button
            variant="ghost"
            onClick={() => navigate({ to: '/search', search: { q: '', sort: 'downloads', page: 0 } })}
          >
            查看全部 →
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
      </section>

      {/* Latest Releases Section */}
      <section className="space-y-6 animate-fade-up delay-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-3xl font-bold font-heading text-foreground mb-2">最新发布</h2>
            <p className="text-muted-foreground">刚刚发布的新技能</p>
          </div>
          <Button
            variant="ghost"
            onClick={() => navigate({ to: '/search', search: { q: '', sort: 'newest', page: 0 } })}
          >
            查看全部 →
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
      </section>
    </div>
  )
}
