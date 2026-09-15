import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { AlertTriangle, Check, Circle, Clock3, RefreshCw, Square } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { cn } from '@/shared/lib/utils'
import {
  useCancelSuiteBundleOperation,
  useRetrySuiteBundleOperation,
  useSuiteBundleOperation,
} from '@/shared/hooks/use-suite-queries'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'

const TERMINAL_STATUSES = new Set(['CANCELLED', 'REPREVIEW_REQUIRED', 'SUITE_DRAFT_CREATED'])

function splitCoordinate(coordinate?: string): { namespace: string; slug: string } | null {
  const match = coordinate?.match(/^@([^/]+)\/(.+)$/)
  return match ? { namespace: match[1], slug: match[2] } : null
}

export function SuiteBundleOperationDetail({ operationId }: { operationId: string }) {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const operationQuery = useSuiteBundleOperation(operationId)
  const cancelMutation = useCancelSuiteBundleOperation()
  const retryMutation = useRetrySuiteBundleOperation()
  const [cancelOpen, setCancelOpen] = useState(false)
  const operation = operationQuery.data
  const status = operation?.status
  const draftCoordinate = splitCoordinate(operation?.targetCoordinate)
  const restartOperation = () => {
    if (operation?.mode === 'UPDATE' && draftCoordinate) {
      void navigate({
        to: `/dashboard/suites/${draftCoordinate.namespace}/${encodeURIComponent(draftCoordinate.slug)}/new-version`,
        search: { sourceVersion: operation.baseVersion ?? undefined },
      })
      return
    }
    void navigate({ to: '/dashboard/suites/new' })
  }
  const canCancel = Boolean(status && !TERMINAL_STATUSES.has(status))
  const memberStageComplete = status === 'SUITE_DRAFT_CREATED'
  const memberStageActive = status === 'RUNNING' || status === 'WAITING_FOR_MEMBERS'
    || status === 'BLOCKED_RETRYABLE'

  return (
    <div className="space-y-6" aria-live="polite">
      <Card className="space-y-6 p-5 md:p-6">
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
          <div className="min-w-0">
            <p className="break-all font-mono text-base font-semibold">
              {operation?.targetCoordinate ?? t('suite.bundle.loadingOperation')}
              {operation?.targetVersion ? `@${operation.targetVersion}` : ''}
            </p>
            <p className="mt-2 text-sm text-muted-foreground">
              {status ? t(`suite.bundle.status.${status}`) : t('suite.bundle.loadingOperation')}
            </p>
            {status ? (
              <p className="mt-2 text-sm font-medium text-foreground">
                {t(`suite.bundle.nextStep.${status}`)}
              </p>
            ) : null}
          </div>
          {status ? (
            <span className={cn(
              'w-fit rounded-full border px-3 py-1 text-xs font-medium',
              status === 'BLOCKED_RETRYABLE' || status === 'REPREVIEW_REQUIRED'
                ? 'border-amber-500/30 bg-amber-500/10 text-amber-800 dark:text-amber-300'
                : status === 'CANCELLED'
                  ? 'border-border bg-muted text-muted-foreground'
                  : status === 'SUITE_DRAFT_CREATED'
                    ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-800 dark:text-emerald-300'
                    : 'border-primary/20 bg-primary/10 text-primary',
            )}>
              {t(`suite.bundle.statusLabel.${status}`)}
            </span>
          ) : null}
        </div>

        <ol className="grid gap-3 md:grid-cols-3" aria-label={t('suite.bundle.lifecycleTitle')}>
          <LifecycleStep
            icon={<Check className="h-4 w-4" />}
            title={t('suite.bundle.lifecycle.preview')}
            description={t('suite.bundle.lifecycle.previewDescription')}
            state="complete"
          />
          <LifecycleStep
            icon={memberStageComplete ? <Check className="h-4 w-4" /> : <Clock3 className="h-4 w-4" />}
            title={t('suite.bundle.lifecycle.members')}
            description={t('suite.bundle.lifecycle.membersDescription')}
            state={memberStageComplete ? 'complete' : memberStageActive ? 'active' : 'inactive'}
          />
          <LifecycleStep
            icon={memberStageComplete ? <Check className="h-4 w-4" /> : <Circle className="h-4 w-4" />}
            title={t('suite.bundle.lifecycle.draft')}
            description={t('suite.bundle.lifecycle.draftDescription')}
            state={memberStageComplete ? 'complete' : 'inactive'}
          />
        </ol>

        <div className="grid gap-3 border-t pt-4 text-sm sm:grid-cols-2">
          <div>
            <p className="text-xs text-muted-foreground">{t('suite.bundle.operationId')}</p>
            <p className="mt-1 break-all font-mono text-xs">{operationId}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">{t('suite.bundle.lastUpdated')}</p>
            <p className="mt-1 text-xs">
              {operation?.updatedAt ? formatLocalDateTime(operation.updatedAt, i18n.language) : '—'}
            </p>
          </div>
        </div>
      </Card>

      {operationQuery.error ? (
        <Card className="flex flex-wrap items-center justify-between gap-3 border-destructive/30 p-4 text-sm text-destructive" role="alert">
          <span>{t('suite.bundle.operationLoadFailed')}</span>
          <Button variant="outline" size="sm" onClick={() => operationQuery.refetch()}>
            <RefreshCw className="mr-2 h-4 w-4" />{t('suite.bundle.reloadOperation')}
          </Button>
        </Card>
      ) : null}

      {status === 'CANCELLED' ? (
        <div className="flex gap-3 rounded-xl border border-muted-foreground/20 bg-muted/40 p-4 text-sm">
          <Square className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <div>
            <p className="font-medium">{t('suite.bundle.cancelledTitle')}</p>
            <p className="mt-1 text-muted-foreground">{t('suite.bundle.cancelledDescription')}</p>
          </div>
        </div>
      ) : null}

      {operation?.failureCode ? (
        <div className="flex gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{operation.failureCode}</span>
        </div>
      ) : null}

      <Card className="overflow-hidden">
        <div className="border-b px-5 py-4 md:px-6">
          <h2 className="font-semibold">{t('suite.bundle.memberTitle')}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{t('suite.bundle.memberDescription')}</p>
        </div>
        <div className="divide-y">
          {(operation?.members ?? []).map((member) => {
            const memberCoordinate = splitCoordinate(member.coordinate)
            const canViewVersion = !member.redacted && memberCoordinate && member.version && member.skillVersionId
            return (
              <div key={member.position} className="space-y-3 px-5 py-4 md:px-6">
                <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-start">
                  <div className="min-w-0">
                    <p className="break-all font-mono text-sm font-medium">
                      {member.redacted ? t('suite.bundle.redactedMember') : member.coordinate}
                    </p>
                    {!member.redacted ? (
                      <p className="mt-1 text-xs text-muted-foreground">
                        {member.sourceType ? t(`suite.bundle.source.${member.sourceType}`) : null}
                        {member.relationship ? ` · ${t(`suite.bundle.relationship.${member.relationship}`)}` : null}
                        {member.publishAction ? ` · ${t(`suite.bundle.action.${member.publishAction}`)}` : null}
                      </p>
                    ) : null}
                  </div>
                  <span className="w-fit rounded-full bg-secondary px-2.5 py-1 text-xs text-muted-foreground">
                    {t(`suite.bundle.memberStatus.${member.status}`)}
                  </span>
                </div>
                {!member.redacted ? (
                  <>
                    {member.packagePath ? (
                      <p className="break-all text-xs text-muted-foreground">
                        {t('suite.bundle.memberDirectory', { path: member.packagePath })}
                      </p>
                    ) : null}
                    <p className="text-xs text-muted-foreground">
                      {member.visibility ?? '—'} · v{member.version ?? '—'}
                    </p>
                    {(member.errors ?? []).map((error) => <p key={error} className="text-xs text-destructive">{error}</p>)}
                    {(member.warnings ?? []).map((warning) => <p key={warning} className="text-xs text-amber-700 dark:text-amber-300">{warning}</p>)}
                    {canViewVersion ? (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => navigate({
                          to: `/space/${memberCoordinate.namespace}/${encodeURIComponent(memberCoordinate.slug)}`,
                          search: { version: member.version },
                        })}
                      >
                        {t('suite.bundle.viewMemberReview')}
                      </Button>
                    ) : null}
                  </>
                ) : null}
              </div>
            )
          })}
          {!operationQuery.isLoading && (operation?.members?.length ?? 0) === 0 ? (
            <p className="px-5 py-8 text-center text-sm text-muted-foreground">{t('suite.bundle.noMembers')}</p>
          ) : null}
        </div>
      </Card>

      <div className="flex flex-wrap justify-end gap-3">
        {status === 'BLOCKED_RETRYABLE' ? (
          <Button
            variant="outline"
            disabled={retryMutation.isPending}
            onClick={() => retryMutation.mutate(operationId, {
              onError: (error) => toast.error(t('suite.bundle.retryFailed'), error.message),
            })}
          >
            <RefreshCw className="mr-2 h-4 w-4" />{t('suite.bundle.retry')}
          </Button>
        ) : null}
        {canCancel ? (
          <Button variant="outline" disabled={cancelMutation.isPending} onClick={() => setCancelOpen(true)}>
            {t('suite.bundle.cancelOperation')}
          </Button>
        ) : null}
        {status === 'REPREVIEW_REQUIRED' || status === 'CANCELLED' ? (
          <Button onClick={restartOperation}>
            {t('suite.bundle.startAgain')}
          </Button>
        ) : null}
        {status === 'SUITE_DRAFT_CREATED' && draftCoordinate && operation?.targetVersion ? (
          <Button onClick={() => navigate({
            to: `/suite/${draftCoordinate.namespace}/${encodeURIComponent(draftCoordinate.slug)}`,
            search: { version: operation.targetVersion },
          })}>
            {t('suite.bundle.openDraft')}
          </Button>
        ) : null}
      </div>

      <ConfirmDialog
        open={cancelOpen}
        onOpenChange={setCancelOpen}
        title={t('suite.bundle.cancelConfirmTitle')}
        description={t('suite.bundle.cancelConfirmDescription')}
        confirmText={t('suite.bundle.cancelConfirmAction')}
        variant="destructive"
        onConfirm={() => cancelMutation.mutate(operationId, {
          onSuccess: () => setCancelOpen(false),
          onError: (error) => toast.error(t('suite.bundle.cancelFailed'), error.message),
        })}
      />
    </div>
  )
}

function LifecycleStep({ icon, title, description, state }: {
  icon: React.ReactNode
  title: string
  description: string
  state: 'complete' | 'active' | 'inactive'
}) {
  return (
    <li className={cn(
      'flex gap-3 rounded-lg border p-3',
      state === 'complete' && 'border-emerald-500/25 bg-emerald-500/5',
      state === 'active' && 'border-primary/25 bg-primary/5',
      state === 'inactive' && 'bg-muted/20 text-muted-foreground',
    )}>
      <span className={cn(
        'flex h-7 w-7 shrink-0 items-center justify-center rounded-full border',
        state === 'complete' && 'border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300',
        state === 'active' && 'border-primary/30 bg-primary/10 text-primary',
      )}>
        {icon}
      </span>
      <div>
        <p className="text-sm font-medium text-foreground">{title}</p>
        <p className="mt-1 text-xs leading-5 text-muted-foreground">{description}</p>
      </div>
    </li>
  )
}
