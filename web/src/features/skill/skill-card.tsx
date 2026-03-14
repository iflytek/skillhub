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
      className="h-full p-5 cursor-pointer group relative overflow-hidden"
      onClick={onClick}
    >
      {/* Hover gradient border effect */}
      <div className="absolute inset-0 rounded-xl opacity-0 group-hover:opacity-100 transition-opacity duration-300 bg-gradient-to-br from-primary/20 via-transparent to-accent/20 pointer-events-none" />

      <div className="relative z-10 flex h-full flex-col">
        <div className="flex items-start justify-between mb-3">
          <h3 className="font-semibold font-heading text-lg text-foreground group-hover:text-primary transition-colors">
            {skill.displayName}
          </h3>
          <NamespaceBadge type="TEAM" name={`@${skill.namespace}`} />
        </div>

        {skill.summary && (
          <p className="text-sm text-muted-foreground mb-4 line-clamp-2 leading-relaxed">
            {skill.summary}
          </p>
        )}

        <div className="mt-auto flex items-center gap-4 text-xs text-muted-foreground">
          {skill.latestVersion && (
            <span className="px-2.5 py-1 rounded-full bg-secondary/60 font-mono">
              v{skill.latestVersion}
            </span>
          )}
          <span className="flex items-center gap-1">
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
            </svg>
            {skill.downloadCount}
          </span>
          {skill.ratingAvg !== undefined && skill.ratingCount > 0 && (
            <span className="flex items-center gap-1">
              <svg className="w-3.5 h-3.5 text-primary" fill="currentColor" viewBox="0 0 20 20">
                <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
              </svg>
              {skill.ratingAvg.toFixed(1)} ({skill.ratingCount})
            </span>
          )}
        </div>
      </div>
    </Card>
  )
}
