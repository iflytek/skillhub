import type { SkillSummary } from '@/api/types'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { useStarredIdSet } from '@/features/social/use-star'
import { Card } from '@/shared/ui/card'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { getHeadlineVersion } from '@/shared/lib/skill-lifecycle'
import { formatCompactCount } from '@/shared/lib/number-format'
import { formatRelativeTime } from '@/shared/lib/format-relative-time'
import { Bookmark, ShieldCheck, User, Clock } from 'lucide-react'

interface SkillCardProps {
  skill: SkillSummary
  onClick?: () => void
  highlightStarred?: boolean
}

/**
 * Reusable card for displaying one skill in lists such as landing, namespace, search, and stars.
 */
export function SkillCard({ skill, onClick, highlightStarred = true }: SkillCardProps) {
  const { t, i18n } = useTranslation()
  const { isAuthenticated } = useAuth()
  // Batch highlight via shared ['skills','stars'] — never N× useStar per grid row.
  const { starredIds } = useStarredIdSet(highlightStarred && isAuthenticated)
  const showStarredHighlight = highlightStarred && isAuthenticated && starredIds.has(skill.id)
  const headlineVersion = getHeadlineVersion(skill)
  const isInteractive = typeof onClick === 'function'
  const complianceItems = skill.complianceSnapshot?.items?.filter((item) => item.standard || item.controlId) ?? []
  const downloadLabel = t('skillCard.downloads', { value: formatCompactCount(skill.downloadCount) })
  const starLabel = t('skillCard.stars', { count: skill.starCount })
  const ratingLabel = t('skillCard.rating', { rating: skill.ratingAvg?.toFixed(1) ?? '0.0' })

  return (
    <Card
      className="group relative h-full cursor-pointer overflow-hidden rounded-md border border-border/60 bg-card p-4 text-card-foreground transition-[transform,box-shadow,border-color] duration-150 ease-out card-hover focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring/15"
      style={{ borderColor: 'hsl(var(--border-card))', boxShadow: 'var(--shadow-card)' }}
      onClick={onClick}
      onKeyDown={(event) => {
        if (!isInteractive) {
          return
        }

        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onClick()
        }
      }}
      role={isInteractive ? 'link' : undefined}
      tabIndex={isInteractive ? 0 : undefined}
    >
      <div className="flex h-full flex-col">
        <div className="flex items-start justify-between mb-3">
          <div className="space-y-2">
            <h3 className="font-semibold text-base group-hover:text-primary transition-colors duration-150" style={{ color: 'hsl(var(--foreground))' }}>
              {skill.displayName}
            </h3>
          </div>
          <div className="flex items-center gap-2">
            <NamespaceBadge type="TEAM" name={`@${skill.namespace}`} />
          </div>
        </div>

        {skill.summary && (
          <p
            className="skill-card-summary mb-3 text-xs leading-5 text-muted-foreground [overflow-wrap:anywhere]"
            title={skill.summary}
          >
            {skill.summary}
          </p>
        )}

        {complianceItems.length > 0 ? (
          <div className="mb-4 flex flex-wrap gap-1.5">
            {complianceItems.slice(0, 2).map((item, index) => (
              <span
                key={`${item.standard ?? 'standard'}-${item.controlId ?? index}`}
                className="inline-flex items-center gap-1 rounded-full border border-emerald-500/20 bg-emerald-500/10 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:text-emerald-300"
                title={item.title}
              >
                <ShieldCheck className="h-3 w-3" />
                {[item.standard, item.controlId].filter(Boolean).join(' · ')}
              </span>
            ))}
            {complianceItems.length > 2 ? (
              <span className="rounded-full bg-secondary px-2 py-0.5 text-xs text-secondary-foreground">
                +{complianceItems.length - 2}
              </span>
            ) : null}
          </div>
        ) : null}

        <div className="mt-auto flex items-center gap-4 text-xs text-muted-foreground">
          {headlineVersion && (
            <span className="px-2.5 py-1 rounded-full bg-secondary/60 font-mono text-[11px] font-semibold" style={{ color: 'hsl(215 30% 35%)' }}>
              v{headlineVersion.version}
            </span>
          )}
          <span className="flex items-center gap-1 text-muted-foreground" title={downloadLabel} aria-label={downloadLabel}>
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
            </svg>
            {formatCompactCount(skill.downloadCount)}
          </span>
          <span
            className={`flex items-center gap-1 ${showStarredHighlight ? 'font-semibold text-amber-600 dark:text-amber-400' : 'text-muted-foreground'}`}
            title={starLabel}
            aria-label={starLabel}
          >
            <Bookmark className={`w-3.5 h-3.5 ${showStarredHighlight ? 'fill-current' : ''}`} />
            {skill.starCount}
          </span>
          {skill.ratingAvg !== undefined && skill.ratingCount > 0 && (
            <span className="flex items-center gap-1 text-amber-600 dark:text-amber-400" title={ratingLabel} aria-label={ratingLabel}>
              <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 20 20">
                <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
              </svg>
              {skill.ratingAvg.toFixed(1)}
            </span>
          )}
        </div>
        {(skill.ownerDisplayName || skill.updatedAt) && (
          <div className="mt-2 pt-2 border-t border-border/50 flex items-center gap-3 text-xs text-muted-foreground">
            {skill.ownerDisplayName && (
              <span className="flex items-center gap-1">
                <User className="w-3 h-3" />
                {skill.ownerDisplayName}
              </span>
            )}
            {skill.updatedAt && (
              <span className="flex items-center gap-1">
                <Clock className="w-3 h-3" />
                {formatRelativeTime(skill.updatedAt, i18n.language)}
              </span>
            )}
          </div>
        )}
      </div>
    </Card>
  )
}
