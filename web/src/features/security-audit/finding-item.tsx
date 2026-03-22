import { useTranslation } from 'react-i18next'
import type { SecurityFinding } from './types'
import { SeverityBadge } from './severity-badge'

interface FindingItemProps {
  finding: SecurityFinding
}

export function FindingItem({ finding }: FindingItemProps) {
  const { t } = useTranslation()

  const location = [finding.filePath, finding.lineNumber].filter(Boolean).join(':')

  return (
    <div className="space-y-2 rounded-xl border border-border/60 bg-card/70 p-4">
      <div className="flex items-center gap-2 flex-wrap">
        <SeverityBadge severity={finding.severity} />
        <code className="text-xs font-mono text-muted-foreground">{finding.ruleId}</code>
        {location && (
          <span className="text-xs text-muted-foreground">{location}</span>
        )}
      </div>

      <p className="text-sm text-foreground">{finding.title}</p>

      {finding.message && (
        <p className="text-sm text-muted-foreground">{finding.message}</p>
      )}

      {finding.codeSnippet && (
        <pre className="overflow-x-auto rounded-lg bg-secondary/50 p-3 text-xs font-mono text-muted-foreground">
          {finding.codeSnippet}
        </pre>
      )}

      {finding.remediation && (
        <div className="bg-secondary/50 rounded-xl p-3 text-sm text-muted-foreground">
          <span className="font-medium text-foreground">{t('securityAudit.remediation')}: </span>
          {finding.remediation}
        </div>
      )}
    </div>
  )
}
