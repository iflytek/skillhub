import { Link, useNavigate, useParams } from '@tanstack/react-router'
import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Ban, RefreshCw } from 'lucide-react'
import { authoringApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { toast } from '@/shared/lib/toast'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { EventConsole } from '@/features/authoring/event-console'
import { FindingCard } from '@/features/authoring/finding-card'
import { RunStatusBadge } from '@/features/authoring/run-status-badge'
import { useValidationStream } from '@/features/authoring/use-validation-stream'

/**
 * Validation run detail: live event console (SSE with polling fallback), the
 * findings report with previewable fixes, and run lifecycle actions.
 */
export function RunDetailPage() {
  const { draftId, runId } = useParams({ from: '/dashboard/authoring/$draftId/runs/$runId' })
  const id = Number(runId)
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const run = useQuery({
    queryKey: ['authoring', 'run', id],
    queryFn: () => authoringApi.getRun(id),
    enabled: Number.isFinite(id),
    // Terminal runs stop changing, but a live run's status must be refreshed so
    // the page eventually notices the terminal transition even without events.
    refetchInterval: (query) => (query.state.data?.terminal ? false : 2_000),
  })

  // Findings keep arriving while the run is active; poll until it settles.
  const runTerminal = run.data?.terminal ?? false
  const findings = useQuery({
    queryKey: ['authoring', 'run', id, 'findings'],
    queryFn: () => authoringApi.listFindings(id),
    enabled: Number.isFinite(id),
    refetchInterval: runTerminal ? false : 2_000,
  })

  // The interval above stops the moment the run turns terminal, and that final
  // tick can still predate the finish — fetch the settled report exactly once.
  // A settled run also changes the draft's validated state, so drop the cached
  // draft data: navigating back to the workbench must not serve a pre-run
  // snapshot while the global 30s staleTime would keep the submit gate closed.
  useEffect(() => {
    if (runTerminal) {
      void queryClient.invalidateQueries({ queryKey: ['authoring', 'run', id, 'findings'] })
      if (Number.isFinite(Number(draftId))) {
        void queryClient.invalidateQueries({ queryKey: ['authoring', 'draft', Number(draftId)] })
      }
      void queryClient.invalidateQueries({ queryKey: ['authoring', 'drafts'] })
    }
  }, [runTerminal, id, draftId, queryClient])

  const stream = useValidationStream(run.data)

  const applyFix = useMutation({
    mutationFn: (findingId: number) => authoringApi.applyFix(id, findingId),
    onSuccess: (finding) => {
      toast.success(t('authoring.run.fixApplied', { revision: finding.appliedRevision ?? '' }))
      queryClient.invalidateQueries({ queryKey: ['authoring', 'run', id, 'findings'] })
      queryClient.invalidateQueries({ queryKey: ['authoring', 'draft', Number(draftId)] })
      queryClient.invalidateQueries({ queryKey: ['authoring', 'draft', Number(draftId), 'files'] })
      queryClient.invalidateQueries({ queryKey: ['authoring', 'drafts'] })
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : String(error))
    },
  })

  const dismissFinding = useMutation({
    mutationFn: (findingId: number) => authoringApi.dismissFinding(id, findingId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['authoring', 'run', id, 'findings'] })
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : String(error))
    },
  })

  const cancelRun = useMutation({
    mutationFn: () => authoringApi.cancelRun(id),
    onSuccess: () => {
      toast.success(t('authoring.run.cancelRequested'))
      queryClient.invalidateQueries({ queryKey: ['authoring', 'run', id] })
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : String(error))
    },
  })

  if (run.isLoading) {
    return <p className="text-sm text-muted-foreground">{t('authoring.ui.loading')}</p>
  }
  if (run.isError || !run.data) {
    return <p className="text-sm text-red-600">{t('authoring.run.loadFailed')}</p>
  }

  const current = run.data
  const active = current.active

  return (
    <div className="space-y-6 animate-fade-up">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="space-y-1">
          <Link
            to="/dashboard/authoring/$draftId"
            params={{ draftId: String(draftId) }}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden />
            {t('authoring.run.back')}
          </Link>
          <h1 className="flex items-center gap-3 text-2xl font-semibold">
            {t('authoring.run.title', { id: current.id })}
            <RunStatusBadge status={current.status} />
          </h1>
          <p className="text-xs text-muted-foreground">
            {t('authoring.run.revision')} {current.draftRevision} ·{' '}
            {current.createdAt ? formatLocalDateTime(current.createdAt, i18n.language) : ''}
            {current.finishedAt && ` · ${formatLocalDateTime(current.finishedAt, i18n.language)}`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {active && (
            <Button variant="outline" onClick={() => cancelRun.mutate()} disabled={cancelRun.isPending || current.cancelRequested}>
              <Ban className="mr-1 h-4 w-4" aria-hidden />
              {current.cancelRequested ? t('authoring.run.cancelling') : t('authoring.run.cancel')}
            </Button>
          )}
          {current.terminal && current.errorCount > 0 && (
            <Button
              onClick={() =>
                navigate({
                  to: '/dashboard/authoring/$draftId',
                  params: { draftId: String(draftId) },
                })
              }
            >
              <RefreshCw className="mr-1 h-4 w-4" aria-hidden />
              {t('authoring.run.fixAndRevalidate')}
            </Button>
          )}
        </div>
      </div>

      <div className="grid gap-3 text-sm sm:grid-cols-3">
        <Card>
          <CardContent className="pt-6">
            <div className="text-2xl font-semibold text-red-600" data-testid="error-count">{current.errorCount}</div>
            <p className="text-xs text-muted-foreground">{t('authoring.run.errorCount')}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="text-2xl font-semibold text-amber-600" data-testid="warning-count">{current.warningCount}</div>
            <p className="text-xs text-muted-foreground">{t('authoring.run.warningCount')}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="text-2xl font-semibold" data-testid="task-count">
              {String(current.summary?.behaviorTasksRun ?? '—')}
            </div>
            <p className="text-xs text-muted-foreground">{t('authoring.run.tasksRun')}</p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t('authoring.run.eventConsoleTitle')}</CardTitle>
        </CardHeader>
        <CardContent>
          <EventConsole events={stream.events} live={stream.live} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t('authoring.run.findingsTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {findings.isLoading && (
            <p className="text-sm text-muted-foreground">{t('authoring.ui.loading')}</p>
          )}
          {findings.data && findings.data.length === 0 && !active && (
            <p className="py-4 text-center text-sm text-muted-foreground">{t('authoring.run.noFindings')}</p>
          )}
          {findings.data?.map((finding) => (
            <FindingCard
              key={finding.id}
              finding={finding}
              applying={applyFix.isPending}
              onApply={(target) => applyFix.mutate(target.id)}
              onDismiss={(target) => dismissFinding.mutate(target.id)}
            />
          ))}
        </CardContent>
      </Card>
    </div>
  )
}
