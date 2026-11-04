import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { authoringApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Card, CardContent } from '@/shared/ui/card'
import { toast } from '@/shared/lib/toast'
import { QueryError } from './query-error'

export function ValidationResults({ runId, draftId }: { runId: number, draftId: number }) {
  const { t } = useTranslation()
  const client = useQueryClient()
  const runKey = ['authoring', 'run', runId]
  const run = useQuery({
    queryKey: runKey, queryFn: () => authoringApi.getRun(runId),
    refetchInterval: query => query.state.data?.terminal ? false : 1500,
    meta: { skipGlobalErrorHandler: true }, retry: false,
  })
  const findings = useQuery({
    queryKey: [...runKey, 'findings', run.data?.status], queryFn: () => authoringApi.listFindings(runId),
    enabled: run.isSuccess && run.data.draftId === draftId,
    refetchInterval: run.data?.terminal ? false : 1500,
    meta: { skipGlobalErrorHandler: true }, retry: false,
  })
  useEffect(() => {
    if (run.data?.terminal && run.data.draftId === draftId) {
      void client.invalidateQueries({ queryKey: ['authoring', 'draft', draftId] })
      void client.invalidateQueries({ queryKey: ['authoring', 'drafts'] })
    }
  }, [client, draftId, run.data?.terminal, run.data?.draftId])
  const cancel = useMutation({
    mutationFn: () => authoringApi.cancelRun(runId),
    onSuccess: data => client.setQueryData(runKey, data),
    onError: error => toast.error(error instanceof Error ? error.message : t('apiError.unknown')),
  })
  if (run.isPending) return <p role="status">{t('authoring.ui.loading')}</p>
  if (run.isError) return <QueryError error={run.error} retry={() => void run.refetch()} />
  if (run.data.draftId !== draftId) return <p role="alert">{t('authoring.detail.loadFailed')}</p>
  const data = run.data
  return (
    <Card><CardContent className="space-y-4 pt-6" data-testid="validation-results">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="space-y-1" role="status">
          <p className="font-medium">{t(`authoring.run.status.${data.status}`, { defaultValue: data.status })} · {t('authoring.run.revision')} {data.draftRevision}</p>
          <p className="text-sm text-muted-foreground">{t('authoring.run.errors', { count: data.errorCount ?? 0 })} · {t('authoring.run.warnings', { count: data.warningCount ?? 0 })}</p>
        </div>
        {data.active && <Button variant="outline" disabled={cancel.isPending || data.cancelRequested} onClick={() => cancel.mutate()}>{t('authoring.run.cancel')}</Button>}
      </div>
      {findings.isPending && <p>{t('authoring.ui.loading')}</p>}
      {findings.isError && <QueryError error={findings.error} retry={() => void findings.refetch()} />}
      {findings.isSuccess && findings.data.length === 0 && <p className="text-sm text-muted-foreground">{t(data.terminal ? 'authoring.run.noFindings' : 'authoring.run.waiting')}</p>}
      <ul className="space-y-3">
        {findings.data?.map((finding, index) => <li key={finding.id ?? index} className="space-y-1 rounded-md border p-3">
          <p className="break-words text-sm font-medium">{finding.severity === 'ERROR' ? t('authoring.run.error') : t('authoring.run.warning')} · {finding.ruleCode}</p>
          <p className="break-words text-sm">{finding.message}</p>
          {finding.filePath && <p className="break-all font-mono text-xs text-muted-foreground">{finding.filePath}{finding.location ? ` · ${finding.location}` : ''}</p>}
          {finding.suggestion?.description && <p className="break-words text-sm text-muted-foreground">{finding.suggestion.description}</p>}
        </li>)}
      </ul>
    </CardContent></Card>
  )
}
