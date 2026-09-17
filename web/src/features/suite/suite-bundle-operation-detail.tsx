/* Hallmark · component: publishing task detail · genre: modern-minimal · tone: utilitarian · pre-emit critique: P5 H5 E5 S5 R5 V4 */
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
import { suiteBundleProblemKind } from './suite-bundle-problem'

const TERMINAL_STATUSES = new Set(['CANCELLED', 'REPREVIEW_REQUIRED', 'SUITE_DRAFT_CREATED'])
const INTERNAL_FAILURE_CODE_PATTERN = /^[A-Z][A-Z0-9_]+$/

function isDemoOnlyMessage(message: string): boolean {
  return message.includes('演示数据') || message.includes('不具备真实执行计划') || message.includes('请勿点击重试')
}

function userVisibleMessages(messages: string[] | undefined): string[] {
  return (messages ?? []).filter((message) => (
    !INTERNAL_FAILURE_CODE_PATTERN.test(message.trim()) && !isDemoOnlyMessage(message)
  ))
}

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
  const draftMissing = status === 'SUITE_DRAFT_CREATED'
    && (!operation?.resultSuiteId || !operation.resultSuiteVersionId)
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
  const problemKind = draftMissing ? 'draftMissing' : suiteBundleProblemKind(status, operation?.failureCode)
  const retryLabel = retryMutation.isPending
    ? t('suite.bundle.retrying')
    : problemKind === 'memberScanFailed'
      ? t('suite.bundle.retryScan')
      : t('suite.bundle.retryMemberPublish')
  const affectedMembers = operation?.members?.filter((member) => {
    const missingBoundVersion = member.status === 'COMPLETED' && (!member.skillId || !member.skillVersionId)
    return missingBoundVersion
      || member.status === 'BLOCKED_RETRYABLE'
      || member.status === 'REPREVIEW_REQUIRED'
      || (operation.failureCode === 'MEMBER_EXECUTION_FAILED'
        && member.status !== 'COMPLETED'
        && member.status !== 'CANCELLED')
  }) ?? []
  const affectedMemberNames = affectedMembers.slice(0, 3).map((member) => (
    member.redacted ? t('suite.bundle.redactedMember') : member.coordinate
  ))
  const affectedMemberPositions = new Set(affectedMembers.map((member) => member.position))
  const demoOnlyOperation = (operation?.members ?? []).some((member) => (
    [...(member.errors ?? []), ...(member.warnings ?? [])].some(isDemoOnlyMessage)
  ))

  return (
    <div className="space-y-4" aria-live="polite">
      <Card className="space-y-4 p-4">
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
          <div className="min-w-0">
            <p className="break-all font-mono text-base font-semibold">
              {operation?.targetCoordinate ?? t('suite.bundle.loadingOperation')}
              {operation?.targetVersion ? `@${operation.targetVersion}` : ''}
            </p>
            {!status ? <p className="mt-1.5 text-sm text-muted-foreground">{t('suite.bundle.loadingOperation')}</p> : null}
          </div>
          {status ? (
            <div className="flex flex-wrap items-center justify-end gap-2">
              <span className={cn(
              'w-fit rounded-md border px-2 py-1 text-xs font-medium',
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
              {status === 'BLOCKED_RETRYABLE' && !demoOnlyOperation ? (
                <Button
                  size="sm"
                  disabled={retryMutation.isPending}
                  onClick={() => retryMutation.mutate(operationId, {
                    onError: (error) => toast.error(t('suite.bundle.retryFailed'), error.message),
                  })}
                >
                  <RefreshCw className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />{retryLabel}
                </Button>
              ) : null}
              {canCancel ? (
                <Button size="sm" variant="outline" disabled={cancelMutation.isPending} onClick={() => setCancelOpen(true)}>
                  {t('suite.bundle.cancelOperation')}
                </Button>
              ) : null}
              {status === 'REPREVIEW_REQUIRED' || status === 'CANCELLED' ? (
                <Button size="sm" onClick={restartOperation}>{t('suite.bundle.startAgain')}</Button>
              ) : null}
              {status === 'SUITE_DRAFT_CREATED' && !draftMissing && draftCoordinate && operation?.targetVersion ? (
                <Button size="sm" onClick={() => navigate({
                  to: `/dashboard/suites/${draftCoordinate.namespace}/${encodeURIComponent(draftCoordinate.slug)}`,
                  search: { version: operation.targetVersion },
                })}>{t('suite.bundle.openDraft')}</Button>
              ) : null}
              {draftMissing ? (
                <Button size="sm" onClick={restartOperation}>{t('suite.bundle.startAgain')}</Button>
              ) : null}
            </div>
          ) : null}
        </div>

        <ol className="grid gap-2 md:grid-cols-3" aria-label={t('suite.bundle.lifecycleTitle')}>
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

        <div className="grid gap-3 border-t pt-3 text-sm sm:grid-cols-2">
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

      {problemKind ? (
        <div className="flex gap-3 rounded-lg border border-amber-500/30 bg-amber-500/5 p-3.5 text-sm" role="status">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-700 dark:text-amber-300" aria-hidden="true" />
          <div className="min-w-0">
            <p className="font-medium text-foreground">{t(`suite.bundle.problem.${problemKind}.title`)}</p>
            <p className="mt-1 text-xs leading-5 text-muted-foreground">
              {t(`suite.bundle.problem.${problemKind}.description`)}
            </p>
            {affectedMemberNames.length > 0 ? (
              <p className="mt-2 flex flex-wrap items-center gap-1.5 text-xs text-foreground">
                <span className="text-muted-foreground">{t('suite.bundle.problem.affectedMembersLabel')}</span>
                {affectedMemberNames.map((memberName) => (
                  <span key={memberName} className="rounded-md bg-amber-500/10 px-1.5 py-0.5 font-mono text-amber-900 dark:text-amber-200">
                    {memberName}
                  </span>
                ))}
                {affectedMembers.length > affectedMemberNames.length
                  ? <span className="text-muted-foreground">{t('suite.bundle.problem.affectedMembersMore', {
                      count: affectedMembers.length - affectedMemberNames.length,
                    })}</span>
                  : null}
              </p>
            ) : null}
          </div>
        </div>
      ) : null}

      <Card className="overflow-hidden">
        <div className="border-b px-4 py-3">
          <h2 className="text-sm font-semibold">{t('suite.bundle.memberTitle')}</h2>
          <p className="mt-1 text-xs text-muted-foreground">{t('suite.bundle.memberDescription')}</p>
        </div>
        <div className="divide-y">
          {(operation?.members?.length ?? 0) > 0 ? (
            <div className="hidden grid-cols-[minmax(220px,1.4fr)_minmax(160px,1fr)_90px_130px] gap-3 bg-secondary/30 px-4 py-2 text-xs text-muted-foreground md:grid">
              <span>{t('suite.management.member')}</span>
              <span>{t('suite.bundle.memberAction')}</span>
              <span>{t('suite.version')}</span>
              <span>{t('suite.status')}</span>
            </div>
          ) : null}
          {(operation?.members ?? []).map((member) => {
            const memberCoordinate = splitCoordinate(member.coordinate)
            const canViewSkill = !member.redacted && memberCoordinate && member.skillId
            const missingBoundVersion = member.status === 'COMPLETED' && (!member.skillId || !member.skillVersionId)
            const needsAttention = affectedMemberPositions.has(member.position) || missingBoundVersion
            const errors = userVisibleMessages(member.errors)
            const warnings = userVisibleMessages(member.warnings)
            return (
              <div key={member.position} className={cn(
                'grid gap-2 px-4 py-3 text-sm md:grid-cols-[minmax(220px,1.4fr)_minmax(160px,1fr)_90px_130px] md:gap-3',
                needsAttention && 'border-l-2 border-amber-500 bg-amber-500/[0.06]',
              )}>
                <div className="min-w-0">
                  <p className="break-all font-mono text-xs font-medium">
                    {member.redacted ? t('suite.bundle.redactedMember') : member.coordinate}
                  </p>
                  {!member.redacted && member.packagePath ? (
                    <p className="mt-1 break-all text-xs text-muted-foreground">
                      {t('suite.bundle.memberDirectory', { path: member.packagePath })}
                    </p>
                  ) : null}
                </div>
                {!member.redacted ? (
                  <p className="text-xs leading-5 text-muted-foreground">
                    {member.sourceType ? t(`suite.bundle.source.${member.sourceType}`) : null}
                    {member.relationship ? ` · ${t(`suite.bundle.relationship.${member.relationship}`)}` : null}
                    {member.publishAction ? ` · ${t(`suite.bundle.action.${member.publishAction}`)}` : null}
                  </p>
                ) : <span>—</span>}
                <p className="font-mono text-xs text-muted-foreground">
                  {member.visibility ?? '—'}<br />v{member.version ?? '—'}
                </p>
                <div className="space-y-2">
                  <span className={cn(
                    'inline-flex rounded-md px-2 py-1 text-xs',
                    needsAttention
                      ? 'bg-amber-500/15 font-medium text-amber-900 dark:text-amber-200'
                      : 'bg-secondary text-muted-foreground',
                  )}>
                    {needsAttention ? t('suite.bundle.memberStatus.ATTENTION') : t(`suite.bundle.memberStatus.${member.status}`)}
                  </span>
                  {canViewSkill ? (
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-7 px-2 text-xs"
                      onClick={() => navigate({
                        to: `/space/${memberCoordinate.namespace}/${encodeURIComponent(memberCoordinate.slug)}`,
                        search: { returnTo: `/dashboard/suites/publishing/${operationId}`, version: member.version ?? undefined },
                      })}
                    >
                      {t('suite.bundle.viewMemberSkill')}
                    </Button>
                  ) : !member.redacted ? (
                    <p className="text-xs text-muted-foreground">{t('suite.bundle.memberSkillNotCreated')}</p>
                  ) : null}
                </div>
                {!member.redacted && (errors.length > 0 || warnings.length > 0) ? (
                  <div className="space-y-1 md:col-span-4">
                    {errors.map((error) => <p key={error} className="text-xs text-destructive">{error}</p>)}
                    {warnings.map((warning) => <p key={warning} className="text-xs text-amber-700 dark:text-amber-300">{warning}</p>)}
                  </div>
                ) : null}
              </div>
            )
          })}
          {!operationQuery.isLoading && (operation?.members?.length ?? 0) === 0 ? (
            <p className="px-5 py-8 text-center text-sm text-muted-foreground">{t('suite.bundle.noMembers')}</p>
          ) : null}
        </div>
      </Card>

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
      'flex gap-2.5 rounded-lg border p-2.5',
      state === 'complete' && 'border-emerald-500/25 bg-emerald-500/5',
      state === 'active' && 'border-primary/25 bg-primary/5',
      state === 'inactive' && 'bg-muted/20 text-muted-foreground',
    )}>
      <span className={cn(
        'flex h-6 w-6 shrink-0 items-center justify-center rounded-full border [&>svg]:h-3.5 [&>svg]:w-3.5',
        state === 'complete' && 'border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300',
        state === 'active' && 'border-primary/30 bg-primary/10 text-primary',
      )}>
        {icon}
      </span>
      <div>
        <p className="text-xs font-medium text-foreground">{title}</p>
        <p className="mt-0.5 text-xs leading-4 text-muted-foreground">{description}</p>
      </div>
    </li>
  )
}
