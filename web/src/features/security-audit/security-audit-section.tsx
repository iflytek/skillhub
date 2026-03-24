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
  /** When true, omits the outer Card wrapper (e.g. when rendered inside a Dialog). */
  bare?: boolean
}

export function SecurityAuditSection({ skillId, versionId, bare }: SecurityAuditSectionProps) {
  const { t } = useTranslation()
  const { data: audits, isLoading } = useSecurityAudits(skillId, versionId)

  // Return nothing while loading or when there are no audits.
  // This section is supplementary — showing a shimmer that then disappears
  // for the majority of skills (no audit) would cause a jarring flicker.
  if (isLoading || !audits || audits.length === 0) {
    return null
  }

  const content = (
    <>
      <div className="flex items-center gap-2">
        <Shield className="w-5 h-5 text-muted-foreground" />
        <h2 className="text-xl font-bold font-heading">{t('securityAudit.title')}</h2>
      </div>

      <div className="space-y-4">
        {audits.map((audit) => (
          <ScannerCard key={audit.id} audit={audit} />
        ))}
      </div>
    </>
  )

  if (bare) {
    return <div className="space-y-6">{content}</div>
  }

  return <Card className="p-8 space-y-6">{content}</Card>
}

function ScannerCard({ audit }: { audit: SecurityAuditRecord }) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState(false)
  const sortedFindings = sortFindings(audit.findings)

  return (
    <div className="rounded-xl border border-border/60 bg-secondary/20 p-4 space-y-3">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <div className="flex items-center gap-3">
          <span className="text-sm font-semibold font-mono">{audit.scannerType}</span>
          <VerdictBadge displayState={getSecurityAuditDisplayState(audit)} />
        </div>
        <div className="flex items-center gap-4 text-sm text-muted-foreground">
          <span>
            {t('securityAudit.findingsCount', { count: audit.findingsCount })}
          </span>
          {audit.scanDurationSeconds != null && (
            <span>{t('securityAudit.scanDuration', { seconds: audit.scanDurationSeconds })}</span>
          )}
        </div>
      </div>

      {sortedFindings.length > 0 && (
        <>
          <Button
            variant="ghost"
            size="sm"
            className="w-full justify-between text-muted-foreground"
            onClick={() => setExpanded(!expanded)}
          >
            <span>{t('securityAudit.findings')}</span>
            {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
          </Button>

          {expanded && (
            <div className="space-y-3">
              {sortedFindings.map((finding, idx) => (
                <FindingItem key={`${finding.ruleId}-${idx}`} finding={finding} />
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}
