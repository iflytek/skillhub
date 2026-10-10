import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Shield } from 'lucide-react'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/shared/ui/dialog'
import { toast } from '@/shared/lib/toast'
import { useRetrySecurityScan, useSecurityAudits } from './use-security-audit'
import { getSecurityAuditDisplayState } from './display-state'
import { VerdictBadge } from './verdict-badge'
import { SecurityAuditSection } from './security-audit-section'

interface SecurityAuditSummaryProps {
  skillId: number
  versionId: number
  versionStatus?: string
  canRetry?: boolean
}

export function SecurityAuditSummary({ skillId, versionId, versionStatus, canRetry = false }: SecurityAuditSummaryProps) {
  const { t } = useTranslation()
  const { data: audits } = useSecurityAudits(skillId, versionId)
  const [dialogOpen, setDialogOpen] = useState(false)
  const retryMutation = useRetrySecurityScan(skillId, versionId)

  if (!audits || audits.length === 0) {
    return null
  }

  const totalFindings = audits.reduce((sum, a) => sum + a.findingsCount, 0)

  return (
    <>
      <Card className="p-5 space-y-3">
        <div className="flex items-center gap-2">
          <Shield className="w-4 h-4 text-muted-foreground" />
          <span className="text-sm font-semibold font-heading text-foreground">
            {t('securityAudit.title')}
          </span>
        </div>
        <div className="space-y-2">
          {audits.map((audit) => (
            <div
              key={audit.id}
              className="flex items-center justify-between rounded-xl border border-border/60 bg-secondary/20 p-3"
            >
              <span className="text-xs font-mono text-muted-foreground">{audit.scannerType}</span>
              <VerdictBadge displayState={getSecurityAuditDisplayState(audit, versionStatus)} />
            </div>
          ))}
        </div>
        <p className="text-xs text-muted-foreground">
          {t('securityAudit.totalFindings', { count: totalFindings })}
        </p>
        {canRetry && versionStatus === 'SCAN_FAILED' && (
          <Button
            size="sm"
            className="w-full"
            disabled={retryMutation.isPending}
            onClick={() => retryMutation.mutate(undefined, {
              onSuccess: () => toast.success(t('securityAudit.retrySuccess')),
              onError: (error) => toast.error(
                t('securityAudit.retryError'),
                error instanceof Error ? error.message : undefined
              ),
            })}
          >
            {retryMutation.isPending ? t('securityAudit.retrying') : t('securityAudit.retry')}
          </Button>
        )}
        <Button variant="outline" size="sm" className="w-full" onClick={() => setDialogOpen(true)}>
          {t('securityAudit.viewDetails')}
        </Button>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="w-[min(calc(100vw-2rem),48rem)] max-h-[calc(100vh-2rem)] overflow-hidden flex flex-col">
          <DialogHeader className="shrink-0">
            <DialogTitle>{t('securityAudit.title')}</DialogTitle>
            <DialogDescription>{t('securityAudit.dialogDescription')}</DialogDescription>
          </DialogHeader>
          <div className="-mx-8 -mb-8 overflow-y-auto overscroll-contain px-8 pb-8">
            <SecurityAuditSection skillId={skillId} versionId={versionId} versionStatus={versionStatus} bare />
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}
