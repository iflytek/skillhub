import type { MouseEvent } from 'react'
import type { SkillSummary } from '@/api/types'
import { useAuth } from '@/features/auth/use-auth'
import { useStar } from '@/features/social/use-star'
import { Card } from '@/shared/ui/card'
import { getHeadlineVersion } from '@/shared/lib/skill-lifecycle'
import { formatCompactCount } from '@/shared/lib/number-format'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
import { toast } from '@/shared/lib/toast'
import { buildInstallCommand, getBaseUrl } from './install-command'
import { Bookmark, Check, Copy } from 'lucide-react'

interface SkillCardProps {
  skill: SkillSummary
  onClick?: () => void
  highlightStarred?: boolean
}

export function SkillCard({ skill, onClick, highlightStarred = true }: SkillCardProps) {
  const { isAuthenticated } = useAuth()
  const { data: starStatus } = useStar(skill.id, highlightStarred && isAuthenticated)
  const showStarredHighlight = highlightStarred && isAuthenticated && starStatus?.starred
  const headlineVersion = getHeadlineVersion(skill)
  const isInteractive = typeof onClick === 'function'
  const [copied, copy] = useCopyToClipboard()

  const allLabels = skill.labels ?? []
  const maxVisible = 2
  const visibleLabels = allLabels.slice(0, maxVisible)
  const extraCount = allLabels.length - maxVisible
  const installCommand = buildInstallCommand(skill.namespace, skill.slug, getBaseUrl())

  const handleCopy = async (event: MouseEvent) => {
    event.stopPropagation()
    try {
      await copy(installCommand)
      toast.success('Install command copied', installCommand)
    } catch {
      toast.error('Failed to copy install command')
    }
  }

  return (
    <Card
      className="h-full p-5 cursor-pointer group relative overflow-hidden bg-white border shadow-sm card-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70 focus-visible:ring-offset-2"
      style={{ borderColor: 'hsl(var(--border-card))' }}
      onClick={onClick}
      onKeyDown={(event) => {
        if (!isInteractive) return
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onClick()
        }
      }}
      role={isInteractive ? 'link' : undefined}
      tabIndex={isInteractive ? 0 : undefined}
    >
      <div className="flex h-full flex-col">
        <div className="flex items-center justify-between gap-2 mb-3">
          <h3 className="font-semibold text-lg group-hover:text-primary transition-colors flex-shrink min-w-0" style={{ color: 'hsl(var(--foreground))' }}>
            {skill.displayName}
          </h3>
          {visibleLabels.length > 0 && (
            <div className="flex items-center gap-1 flex-shrink-0">
              {visibleLabels.map((label) => (
                <span
                  key={label.slug}
                  className="px-2 py-0.5 rounded-full text-xs font-medium whitespace-nowrap"
                  style={{ background: '#c6ffdd', color: '#0b8a3c' }}
                >
                  {label.displayName}
                </span>
              ))}
              {extraCount > 0 && (
                <span className="text-xs font-medium whitespace-nowrap" style={{ color: 'hsl(var(--text-secondary))' }}>
                  +{extraCount}
                </span>
              )}
            </div>
          )}
        </div>

        {skill.summary && (
          <p className="text-sm text-muted-foreground mb-4 line-clamp-2 leading-relaxed">
            {skill.summary}
          </p>
        )}

        <div className="mt-auto flex items-center text-xs text-muted-foreground">
          <div className="flex items-center gap-4 flex-1 min-w-0">
            {headlineVersion && (
              <span className="px-2.5 py-1 rounded-full bg-secondary/60 font-mono">
                v{headlineVersion.version}
              </span>
            )}
            <span className="flex items-center gap-1">
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
              </svg>
              {formatCompactCount(skill.downloadCount)}
            </span>
            <span
              className={`flex items-center gap-1 ${showStarredHighlight ? 'font-semibold text-primary' : ''}`}
            >
              <Bookmark className={`w-3.5 h-3.5 ${showStarredHighlight ? 'fill-current' : ''}`} />
              {skill.starCount}
            </span>
            {skill.ratingAvg !== undefined && skill.ratingCount > 0 && (
              <span className="flex items-center gap-1">
                <svg className="w-3.5 h-3.5 text-primary" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                </svg>
                {skill.ratingAvg.toFixed(1)}
              </span>
            )}
          </div>
          <button
            type="button"
            onClick={handleCopy}
            title={installCommand}
            className="no-hover-lift flex-shrink-0 p-1.5 rounded-lg transition-colors hover:bg-secondary/80"
            style={{ color: copied ? 'hsl(var(--primary))' : 'hsl(var(--muted-foreground))' }}
          >
            {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
          </button>
        </div>
      </div>
    </Card>
  )
}
