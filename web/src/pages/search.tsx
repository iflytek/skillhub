import { useNavigate, useSearch } from '@tanstack/react-router'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { Button } from '@/shared/ui/button'

export function SearchPage() {
  const navigate = useNavigate()
  const searchParams = useSearch({ from: '/search' })

  const q = searchParams.q || ''
  const sort = searchParams.sort || 'relevance'
  const page = searchParams.page ?? 0

  const { data, isLoading } = useSearchSkills({
    q,
    sort,
    page,
    size: 12,
  })

  const handleSearch = (query: string) => {
    navigate({ to: '/search', search: { q: query, sort, page: 0 } })
  }

  const handleSortChange = (newSort: string) => {
    navigate({ to: '/search', search: { q, sort: newSort, page: 0 } })
  }

  const handlePageChange = (newPage: number) => {
    navigate({ to: '/search', search: { q, sort, page: newPage } })
  }

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${slug}` })
  }

  const totalPages = data ? Math.ceil(data.total / data.size) : 0

  return (
    <div className="space-y-8 animate-fade-up">
      {/* Search Bar */}
      <div className="max-w-3xl mx-auto">
        <SearchBar defaultValue={q} onSearch={handleSearch} />
      </div>

      {/* Sort Selector */}
      <div className="flex items-center justify-between flex-wrap gap-4">
        <div className="flex items-center gap-3">
          <span className="text-sm font-medium text-muted-foreground">排序:</span>
          <div className="flex gap-2">
            <Button
              variant={sort === 'relevance' ? 'default' : 'outline'}
              size="sm"
              onClick={() => handleSortChange('relevance')}
            >
              相关性
            </Button>
            <Button
              variant={sort === 'downloads' ? 'default' : 'outline'}
              size="sm"
              onClick={() => handleSortChange('downloads')}
            >
              下载量
            </Button>
            <Button
              variant={sort === 'newest' ? 'default' : 'outline'}
              size="sm"
              onClick={() => handleSortChange('newest')}
            >
              最新
            </Button>
          </div>
        </div>

        {data && data.total > 0 && (
          <div className="text-sm text-muted-foreground">
            找到 <span className="text-primary font-semibold">{data.total}</span> 个结果
          </div>
        )}
      </div>

      {/* Results */}
      {isLoading ? (
        <SkeletonList count={12} />
      ) : data && data.items.length > 0 ? (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {data.items.map((skill, idx) => (
              <div key={skill.id} className={`animate-fade-up delay-${Math.min(idx % 6 + 1, 6)}`}>
                <SkillCard
                  skill={skill}
                  onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                />
              </div>
            ))}
          </div>
          {totalPages > 1 && (
            <Pagination
              page={page}
              totalPages={totalPages}
              onPageChange={handlePageChange}
            />
          )}
        </>
      ) : (
        <EmptyState
          title="未找到结果"
          description={q ? `没有找到与 "${q}" 相关的技能` : '请输入搜索关键词'}
        />
      )}
    </div>
  )
}
