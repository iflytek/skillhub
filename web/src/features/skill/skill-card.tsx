import type { SkillSummary } from '@/api/types'
import { Card } from '@/shared/ui/card'
import { NamespaceBadge } from '@/shared/components/namespace-badge'

interface SkillCardProps {
  skill: SkillSummary
  onClick?: () => void
}

export function SkillCard({ skill, onClick }: SkillCardProps) {
  return (
    <Card
      className="p-4 hover:shadow-md transition-shadow cursor-pointer"
      onClick={onClick}
    >
      <div className="flex items-start justify-between mb-2">
        <h3 className="font-semibold text-lg">{skill.displayName}</h3>
        <NamespaceBadge type="TEAM" name={`@${skill.namespace}`} />
      </div>

      {skill.summary && (
        <p className="text-sm text-muted-foreground mb-3 line-clamp-2">
          {skill.summary}
        </p>
      )}

      <div className="flex items-center gap-4 text-xs text-muted-foreground">
        {skill.latestVersion && (
          <span>v{skill.latestVersion}</span>
        )}
        <span>{skill.downloadCount} 下载</span>
        {skill.ratingAvg !== undefined && skill.ratingCount > 0 && (
          <span>⭐ {skill.ratingAvg.toFixed(1)} ({skill.ratingCount})</span>
        )}
      </div>
    </Card>
  )
}
