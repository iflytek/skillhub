import { useTranslation } from 'react-i18next'
import { Card } from '@/shared/ui/card'
import { Label } from '@/shared/ui/label'
import { useSecurityAudit } from './use-security-audit'
import type { SecurityFinding } from '@/api/types'

interface SecurityAuditPanelProps {
  skillId: number
  versionId: number
  hasSecurityAudit?: boolean
}

export function SecurityAuditPanel({ skillId, versionId, hasSecurityAudit }: SecurityAuditPanelProps) {
  const { t, i18n } = useTranslation()
  const shouldFetch = hasSecurityAudit !== false
  const { data: audit, isLoading, error } = useSecurityAudit(skillId, versionId, shouldFetch)

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString(i18n.language)
  }

  const formatDuration = (seconds?: number) => {
    if (!seconds) return '—'
    return `${seconds.toFixed(1)}s`
  }

  const getVerdictLabel = (verdict: string) => {
    switch (verdict) {
      case 'SAFE':
        return t('review.verdictSafe')
      case 'SUSPICIOUS':
        return t('review.verdictSuspicious')
      case 'DANGEROUS':
        return t('review.verdictDangerous')
      case 'BLOCKED':
        return t('review.verdictBlocked')
      default:
        return verdict
    }
  }

  const getVerdictClassName = (verdict: string) => {
    switch (verdict) {
      case 'SAFE':
        return 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20'
      case 'SUSPICIOUS':
        return 'bg-amber-500/10 text-amber-500 border-amber-500/20'
      case 'DANGEROUS':
        return 'bg-red-500/10 text-red-500 border-red-500/20'
      case 'BLOCKED':
        return 'bg-slate-500/10 text-slate-500 border-slate-500/20'
      default:
        return 'bg-secondary/60 text-muted-foreground border-border/40'
    }
  }

  const getSeverityClassName = (severity: string) => {
    const upper = severity.toUpperCase()
    if (upper === 'CRITICAL' || upper === 'HIGH') {
      return 'text-red-500'
    }
    if (upper === 'MEDIUM') {
      return 'text-amber-500'
    }
    return 'text-muted-foreground'
  }

  if (!shouldFetch) {
    return (
      <Card className="p-8">
        <h2 className="text-xl font-bold font-heading mb-4">{t('review.securityScan')}</h2>
        <p className="text-muted-foreground">{t('review.securityScanNotAvailable')}</p>
      </Card>
    )
  }

  if (isLoading) {
    return (
      <Card className="p-8">
        <h2 className="text-xl font-bold font-heading mb-4">{t('review.securityScan')}</h2>
        <div className="space-y-3">
          <div className="h-6 w-32 animate-shimmer rounded" />
          <div className="h-4 w-48 animate-shimmer rounded" />
        </div>
      </Card>
    )
  }

  if (error) {
    // 404 means no audit available
    if ('status' in error && error.status === 404) {
      return (
        <Card className="p-8">
          <h2 className="text-xl font-bold font-heading mb-4">{t('review.securityScan')}</h2>
          <p className="text-muted-foreground">{t('review.securityScanNotAvailable')}</p>
        </Card>
      )
    }
    return (
      <Card className="p-8">
        <h2 className="text-xl font-bold font-heading mb-4">{t('review.securityScan')}</h2>
        <p className="text-red-500">{t('review.securityScanError')}</p>
      </Card>
    )
  }

  if (!audit) {
    return null
  }

  return (
    <Card className="p-8 space-y-6">
      <h2 className="text-xl font-bold font-heading">{t('review.securityScan')}</h2>

      <div className="space-y-4">
        <div className="flex items-center gap-3">
          <span
            className={`px-3 py-1.5 rounded-full text-sm font-semibold border ${getVerdictClassName(audit.verdict)}`}
          >
            {getVerdictLabel(audit.verdict)}
          </span>
        </div>

        <div className="grid grid-cols-3 gap-4">
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">
              {t('review.findingsCount')}
            </Label>
            <p className="font-semibold">{audit.findingsCount}</p>
          </div>
          {audit.maxSeverity && (
            <div className="space-y-1">
              <Label className="text-xs text-muted-foreground uppercase tracking-wider">
                {t('review.maxSeverity')}
              </Label>
              <p className={`font-semibold ${getSeverityClassName(audit.maxSeverity)}`}>
                {audit.maxSeverity}
              </p>
            </div>
          )}
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">
              {t('review.scanDuration')}
            </Label>
            <p className="font-semibold text-muted-foreground">
              {formatDuration(audit.scanDurationSeconds)}
            </p>
          </div>
        </div>

        {audit.scannedAt && (
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">
              {t('review.scannedAt')}
            </Label>
            <p className="font-semibold text-muted-foreground">{formatDate(audit.scannedAt)}</p>
          </div>
        )}
      </div>

      {audit.findings && audit.findings.length > 0 && (
        <div className="space-y-3">
          {audit.findings.map((finding, index) => (
            <FindingCard key={`finding-${index}`} finding={finding} />
          ))}
        </div>
      )}
    </Card>
  )
}

interface FindingCardProps {
  finding: SecurityFinding
}

function FindingCard({ finding }: FindingCardProps) {

  const getSeverityClassName = (severity: string) => {
    const upper = severity.toUpperCase()
    if (upper === 'CRITICAL' || upper === 'HIGH') {
      return 'bg-red-500/10 text-red-500 border-red-500/20'
    }
    if (upper === 'MEDIUM') {
      return 'bg-amber-500/10 text-amber-500 border-amber-500/20'
    }
    return 'bg-secondary/60 text-muted-foreground border-border/40'
  }

  return (
    <div className="p-4 bg-secondary/30 rounded-xl border border-border/40 space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 space-y-1">
          <div className="flex items-center gap-2">
            <span
              className={`px-2 py-0.5 rounded text-xs font-semibold border ${getSeverityClassName(finding.severity)}`}
            >
              {finding.severity}
            </span>
            <span className="text-sm font-semibold">{finding.title}</span>
          </div>
          {finding.category && (
            <p className="text-xs text-muted-foreground">{finding.category}</p>
          )}
        </div>
      </div>

      <p className="text-sm leading-relaxed">{finding.message}</p>

      {finding.filePath && (
        <div className="flex items-center gap-2 text-xs text-muted-foreground font-mono">
          <span>{finding.filePath}</span>
          {finding.lineNumber && <span>:{finding.lineNumber}</span>}
        </div>
      )}

      {finding.codeSnippet && (
        <pre className="p-3 bg-black/20 rounded-lg text-xs font-mono overflow-x-auto">
          <code>{finding.codeSnippet}</code>
        </pre>
      )}
    </div>
  )
}
