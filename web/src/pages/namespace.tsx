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
    navigate({ to: `/@${namespace}/${slug}` })
  }

  if (isLoadingNamespace) {
    return (
      <div className="space-y-6 animate-fade-up">
        <div className="h-12 w-48 animate-shimmer rounded-lg" />
        <div className="h-6 w-96 animate-shimmer rounded-md" />
      </div>
    )
  }

  if (!namespaceData) {
    return <EmptyState title="命名空间不存在" />
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <NamespaceHeader namespace={namespaceData} />

      <div className="space-y-6">
        <h2 className="text-2xl font-bold font-heading">技能列表</h2>
        {isLoadingSkills ? (
          <SkeletonList count={6} />
        ) : skillsData && skillsData.items.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {skillsData.items.map((skill, idx) => (
              <div key={skill.id} className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                <SkillCard
                  skill={skill}
                  onClick={() => handleSkillClick(skill.slug)}
                />
              </div>
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
