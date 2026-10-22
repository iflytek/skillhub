import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronDown, ChevronUp, Shield } from 'lucide-react'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { useSecurityAudits } from './use-security-audit'
import { getSecurityAuditDisplayState } from './display-state'
import { VerdictBadge } from './verdict-badge'
import { FindingItem } from './finding-item'
import type { FindingSeverity, SecurityAuditRecord } from './types'
import { cn } from '@/shared/lib/utils'

const SEVERITY_ORDER: Record<FindingSeverity, number> = {
  CRITICAL: 0,
  HIGH: 1,
  MEDIUM: 2,
  LOW: 3,
  INFO: 4,
}

function sortFindings(findings: SecurityAuditRecord['findings']) {
  return [...findings].sort(
    (a, b) => (SEVERITY_ORDER[a.severity] ?? 99) - (SEVERITY_ORDER[b.severity] ?? 99)
  )
}

interface SecurityAuditSectionProps {
  skillId: number
  versionId: number
  versionStatus?: string
  showEmptyState?: boolean
  /** When true, omits the outer Card wrapper (e.g. when rendered inside a Dialog). */
  bare?: boolean
  compact?: boolean
}

export function SecurityAuditSection({ skillId, versionId, versionStatus, showEmptyState = false, bare, compact = false }: SecurityAuditSectionProps) {
  const { t } = useTranslation()
  const { data: audits, isLoading, isError } = useSecurityAudits(skillId, versionId)

  // Review pages show an explicit state even when no scan result is available.
  if (isLoading) {
    if (!showEmptyState) return null
    return <Card className={cn('space-y-3 p-5 md:p-6', compact && 'p-4 md:p-4')}>
      <h2 className={cn('text-lg font-semibold', compact && 'text-base')}>{t('securityAudit.title')}</h2>
      <div className="h-12 animate-shimmer rounded-lg" />
    </Card>
  }

  if (isError || !audits || audits.length === 0) {
    if (!showEmptyState) return null
    return <Card className={cn('space-y-3 p-5 md:p-6', compact && 'p-4 md:p-4')}>
      <h2 className={cn('text-lg font-semibold', compact && 'text-base')}>{t('securityAudit.title')}</h2>
      <p className="text-sm text-muted-foreground">
        {t(isError ? 'review.scanLoadFailed'
          : versionStatus === 'SCANNING' ? 'review.scanPending'
            : versionStatus === 'SCAN_FAILED' ? 'review.scanFailed' : 'review.scanUnavailable')}
      </p>
    </Card>
  }

  const content = (
    <>
      <div className="flex items-center gap-2">
        <Shield className={cn('w-5 h-5 text-muted-foreground', compact && 'h-4 w-4')} />
        <h2 className={cn('text-lg font-semibold', compact && 'text-base')}>{t('securityAudit.title')}</h2>
      </div>

      <div className="space-y-4">
        {audits.map((audit) => (
          <ScannerCard key={audit.id} audit={audit} versionStatus={versionStatus} compact={compact} />
        ))}
      </div>
    </>
  )

  if (bare) {
    return <div className="space-y-6">{content}</div>
  }

  return <Card className={cn('space-y-5 p-5 md:p-6', compact && 'space-y-3 p-4 md:p-4')}>{content}</Card>
}

function ScannerCard({ audit, versionStatus, compact }: { audit: SecurityAuditRecord; versionStatus?: string; compact: boolean }) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState(false)
  const sortedFindings = sortFindings(audit.findings)

  return (
    <div className={cn('space-y-3 rounded-md border border-border/60 bg-muted/20 p-4', compact && 'space-y-2 p-3')}>
      <div className="flex items-center justify-between flex-wrap gap-2">
        <div className="flex items-center gap-2">
          <span className={cn('text-sm font-semibold font-mono', compact && 'text-xs')}>{audit.scannerType}</span>
          <VerdictBadge displayState={getSecurityAuditDisplayState(audit, versionStatus)} compact={compact} />
        </div>
        <div className={cn('flex items-center gap-4 text-sm text-muted-foreground', compact && 'gap-3 text-xs')}>
          <span>
            {t('securityAudit.findingsCount', { count: audit.findingsCount })}
          </span>
          {audit.scanDurationSeconds != null && (
            <span>{t('securityAudit.scanDuration', { seconds: audit.scanDurationSeconds })}</span>
          )}
        </div>
      </div>

      {audit.failureReason && (
        <p className="text-sm text-destructive">
          {t('securityAudit.failureReason', { reason: audit.failureReason })}
        </p>
      )}

      {sortedFindings.length > 0 && (
        <>
          <Button data-analytics-id="security.findings.toggle"
            variant="ghost"
            size="sm"
            className={cn('w-full justify-between text-muted-foreground', compact && 'h-7 px-2 text-xs')}
            aria-expanded={expanded}
            onClick={() => setExpanded(!expanded)}
          >
            <span>{t('securityAudit.findings')}</span>
            {expanded ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
          </Button>

          {expanded && (
            <div className="space-y-3">
              {sortedFindings.map((finding, idx) => (
                <FindingItem key={`${finding.ruleId}-${idx}`} finding={finding} compact={compact} />
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}
