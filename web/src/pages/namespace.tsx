import { useNavigate, useParams } from '@tanstack/react-router'
import { NamespaceHeader } from '@/features/namespace/namespace-header'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { EmptyState } from '@/shared/components/empty-state'
import { useNamespaceDetail, useSearchSkills } from '@/shared/hooks/use-skill-queries'

export function NamespacePage() {
  const navigate = useNavigate()
  const { namespace } = useParams({ from: '/@$namespace' })

  const { data: namespaceData, isLoading: isLoadingNamespace } = useNamespaceDetail(namespace)
  const { data: skillsData, isLoading: isLoadingSkills } = useSearchSkills({
    namespace,
    size: 20,
  })

  const handleSkillClick = (slug: string) => {
    navigate({ to: '/@$namespace/$slug', params: { namespace, slug } })
  }

  if (isLoadingNamespace) {
    return <div className="animate-pulse">加载中...</div>
  }

  if (!namespaceData) {
    return <EmptyState title="命名空间不存在" />
  }

  return (
    <div className="space-y-6">
      <NamespaceHeader namespace={namespaceData} />

      <div className="space-y-4">
        <h2 className="text-xl font-semibold">技能列表</h2>
        {isLoadingSkills ? (
          <SkeletonList count={6} />
        ) : skillsData && skillsData.items.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {skillsData.items.map((skill) => (
              <SkillCard
                key={skill.id}
                skill={skill}
                onClick={() => handleSkillClick(skill.slug)}
              />
            ))}
          </div>
        ) : (
          <EmptyState
            title="暂无技能"
            description="该命名空间下还没有发布任何技能"
          />
        )}
      </div>
    </div>
  )
}
