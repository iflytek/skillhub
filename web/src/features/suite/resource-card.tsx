import { Boxes, Download, Wrench } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { ResourceSummary } from '@/api/types'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { Card } from '@/shared/ui/card'
import { formatCompactCount } from '@/shared/lib/number-format'

export function ResourceCard({ resource, onClick, analyticsId }: { resource: ResourceSummary; onClick: () => void; analyticsId?: string }) {
  const { t } = useTranslation()
  const isSuite = resource.resourceType === 'SUITE'
  return (
    <Card
      data-analytics-id={analyticsId}
      className="group h-full min-w-0 cursor-pointer p-5 transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70"
      role="link"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onClick()
        }
      }}
    >
      <div className="flex h-full flex-col gap-4">
        <div className="min-w-0">
          <div className="min-w-0">
            <span className="mb-2 inline-flex items-center gap-1 rounded-full bg-secondary px-2 py-1 text-xs font-medium">
              {isSuite ? <Boxes className="h-3.5 w-3.5" /> : <Wrench className="h-3.5 w-3.5" />}
              {t(isSuite ? 'suite.resourceTypeSuite' : 'suite.resourceTypeSkill')}
            </span>
            <h3 className="line-clamp-2 text-lg font-semibold [overflow-wrap:anywhere] group-hover:text-primary" title={resource.displayName}>{resource.displayName}</h3>
          </div>
          <div className="mt-2 grid justify-items-start">
            <NamespaceBadge
              className="block max-w-full truncate"
              type={resource.namespace === 'global' ? 'GLOBAL' : 'TEAM'}
              name={`@${resource.namespace}`}
              title={`@${resource.namespace}`}
            />
          </div>
        </div>
        <p className="line-clamp-2 min-h-10 text-sm [overflow-wrap:anywhere] text-muted-foreground">
          {resource.summary || t('suite.noSummary')}
        </p>
        {isSuite && resource.labels?.length ? (
          <div className="flex flex-wrap gap-1.5" aria-label={t('suite.assignedLabels')}>
            {resource.labels.map(label => (
              <span
                key={label.slug}
                className="max-w-full truncate rounded-full border border-border bg-secondary px-2 py-0.5 text-[11px] font-medium text-secondary-foreground"
                title={label.displayName}
              >
                {label.displayName}
              </span>
            ))}
          </div>
        ) : null}
        <div className="mt-auto flex flex-wrap items-center gap-x-3 gap-y-2 text-xs text-muted-foreground">
          <span
            className="max-w-[50%] truncate rounded-full bg-secondary/70 px-2.5 py-1 font-mono"
            title={`v${resource.version}`}
          >
            v{resource.version}
          </span>
          <span className="inline-flex items-center gap-1"><Download className="h-3.5 w-3.5" />{formatCompactCount(resource.installCount)}</span>
          {!resource.available ? <span className="text-destructive">{t('suite.unavailable')}</span> : null}
        </div>
      </div>
    </Card>
  )
}
