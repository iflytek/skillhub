import { useState } from 'react'
import { ChevronDown, ChevronUp, ExternalLink, ShieldCheck } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { ComplianceSnapshot } from '@/api/types'
import { cn } from '@/shared/lib/utils'

interface ComplianceSnapshotPanelProps {
  snapshot?: ComplianceSnapshot | null
  className?: string
  defaultExpanded?: boolean
}

function shortDigest(digest?: string) {
  if (!digest) {
    return '—'
  }
  if (digest.length <= 20) {
    return digest
  }
  return `${digest.slice(0, 17)}…`
}

export function ComplianceSnapshotPanel({ snapshot, className, defaultExpanded = false }: ComplianceSnapshotPanelProps) {
  const { t } = useTranslation()
  const items = snapshot?.items?.filter((item) => item.standard || item.controlId) ?? []
  const [isExpanded, setIsExpanded] = useState(defaultExpanded)

  if (items.length === 0) {
    return null
  }

  return (
    <div className={cn('rounded-2xl border border-emerald-500/20 bg-emerald-500/5 p-4', className)} data-compliance-snapshot-panel>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2 text-sm font-semibold text-foreground">
          <ShieldCheck className="h-4 w-4 text-emerald-500" />
          {t('compliance.title')}
          <span className="rounded-full bg-emerald-500/10 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:text-emerald-300">
            {t('compliance.mappingCount', { count: items.length })}
          </span>
          {snapshot?.schemaVersion ? (
            <span className="rounded-full bg-secondary px-2 py-0.5 font-mono text-xs text-secondary-foreground">
              {snapshot.schemaVersion}
            </span>
          ) : null}
        </div>
        <div className="flex items-center gap-2">
          <div className="font-mono text-xs text-muted-foreground" title={snapshot?.digest}>
            {shortDigest(snapshot?.digest)}
          </div>
          <button
            type="button"
            className="inline-flex items-center gap-1 rounded-full border border-border/70 bg-background/80 px-2.5 py-1 text-xs font-medium text-foreground transition-colors hover:bg-background"
            aria-expanded={isExpanded}
            data-compliance-snapshot-toggle
            onClick={() => setIsExpanded((value) => !value)}
          >
            {isExpanded ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
            {isExpanded ? t('common.collapse') : t('common.expand')}
          </button>
        </div>
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        {items.slice(0, isExpanded ? items.length : 2).map((item, index) => (
          <span
            key={`${item.standard ?? 'standard'}-${item.version ?? 'version'}-${item.controlId ?? index}`}
            className="inline-flex items-center gap-1 rounded-full border border-emerald-500/20 bg-emerald-500/10 px-2.5 py-1 text-xs font-medium text-emerald-800 dark:text-emerald-200"
          >
            <ShieldCheck className="h-3 w-3" />
            {[item.standard, item.controlId].filter(Boolean).join(' · ')}
          </span>
        ))}
        {!isExpanded && items.length > 2 ? (
          <span className="inline-flex items-center rounded-full border border-dashed border-border/70 px-2.5 py-1 text-xs text-muted-foreground">
            +{items.length - 2}
          </span>
        ) : null}
      </div>

      {isExpanded ? (
        <div className="mt-3 grid gap-2" data-compliance-snapshot-detail>
          {items.map((item, index) => (
            <div key={`${item.standard ?? 'standard'}-${item.version ?? 'version'}-${item.controlId ?? index}`} className="rounded-xl border border-border/60 bg-background/70 p-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="rounded-full bg-secondary px-2 py-0.5 font-mono text-xs text-secondary-foreground">
                  {item.standard ?? t('compliance.unknownStandard')}
                </span>
                {item.version ? (
                  <span className="font-mono text-xs text-muted-foreground">{item.version}</span>
                ) : null}
                <span className="font-mono text-sm font-semibold text-foreground">{item.controlId ?? '—'}</span>
              </div>
              {item.title ? (
                <div className="mt-1 text-sm text-muted-foreground">{item.title}</div>
              ) : null}
              {item.evidence && item.evidence.length > 0 ? (
                <div className="mt-2 flex flex-wrap gap-2">
                  {item.evidence.map((evidence, evidenceIndex) => (
                    <span
                      key={`${evidence.type ?? 'evidence'}-${evidence.path ?? evidence.url ?? evidenceIndex}`}
                      className="inline-flex items-center gap-1 rounded-full border border-border/60 px-2 py-0.5 text-xs text-muted-foreground"
                      title={evidence.sha256}
                    >
                      {evidence.url ? <ExternalLink className="h-3 w-3" /> : null}
                      {evidence.path ?? evidence.url ?? evidence.type ?? t('compliance.evidence')}
                    </span>
                  ))}
                </div>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}
    </div>
  )
}
