import { useEffect, useState } from 'react'
import { Link, useParams } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Play } from 'lucide-react'
import type { ValidationRunInfo } from '@/api/types'
import { authoringApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Card, CardContent } from '@/shared/ui/card'
import { toast } from '@/shared/lib/toast'
import { DraftFileEditor } from '@/features/authoring/draft-file-editor'
import { QueryError } from '@/features/authoring/query-error'
import { ValidationResults } from '@/features/authoring/validation-results'

export function DraftDetailPage() {
  const { draftId } = useParams({ from: '/dashboard/authoring/$draftId' })
  const id = Number(draftId)
  // Resource changes reset local editor/run selection along with query identity.
  return <DraftWorkbench key={draftId} id={id} />
}

function DraftWorkbench({ id }: { id: number }) {
  const { t } = useTranslation()
  const client = useQueryClient()
  const [dirty, setDirty] = useState(false)
  const [selectedRunId, setSelectedRunId] = useState<number | null>(null)
  const validId = Number.isSafeInteger(id) && id > 0
  const draft = useQuery({
    queryKey: ['authoring', 'draft', id], queryFn: () => authoringApi.getDraft(id),
    enabled: validId, meta: { skipGlobalErrorHandler: true }, retry: false,
  })
  const files = useQuery({
    queryKey: ['authoring', 'draft', id, 'files'], queryFn: () => authoringApi.listFiles(id),
    enabled: validId && draft.isSuccess, meta: { skipGlobalErrorHandler: true }, retry: false,
  })
  const runs = useQuery({
    queryKey: ['authoring', 'draft', id, 'runs'], queryFn: () => authoringApi.listRuns(id),
    enabled: validId && draft.isSuccess, meta: { skipGlobalErrorHandler: true }, retry: false,
    refetchInterval: query => query.state.data?.some(run => run.active) ? 1500 : false,
  })
  const activeRun = runs.data?.some(run => run.active) ?? false
  const refreshDraft = () => {
    void client.invalidateQueries({ queryKey: ['authoring', 'draft', id] })
    void client.invalidateQueries({ queryKey: ['authoring', 'drafts'] })
  }
  const startRun = useMutation({
    mutationFn: () => authoringApi.startRun(id),
    onSuccess: run => {
      setSelectedRunId(run.id ?? null)
      client.setQueryData<ValidationRunInfo[]>(['authoring', 'draft', id, 'runs'], old => [run, ...(old ?? []).filter(item => item.id !== run.id)])
      refreshDraft()
    },
    onError: error => toast.error(error instanceof Error ? error.message : t('apiError.unknown')),
  })
  // Keep an active run visible even when the page is refreshed or opened mid-run.
  const selected = selectedRunId ?? runs.data?.find(run => run.active)?.id ?? runs.data?.[0]?.id
  useEffect(() => {
    if (activeRun) void client.invalidateQueries({ queryKey: ['authoring', 'draft', id] })
  }, [activeRun, client, id])

  if (!validId) return <p role="alert">{t('authoring.detail.loadFailed')}</p>
  if (draft.isPending) return <p role="status">{t('authoring.ui.loading')}</p>
  if (draft.isError) return <QueryError error={draft.error} retry={() => void draft.refetch()} />
  const current = draft.data
  if (current.revision == null) return <p role="alert">{t('authoring.detail.loadFailed')}</p>
  return (
    <div className="min-w-0 space-y-6">
      <Link to="/dashboard/authoring" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" aria-hidden />{t('authoring.detail.back')}
      </Link>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 space-y-2">
          <h1 className="break-all text-2xl font-semibold">{current.name}</h1>
          <p className="text-sm text-muted-foreground">{t('authoring.run.revision')} {current.revision} · {t(current.validated ? 'authoring.list.validated' : 'authoring.list.notValidated')}</p>
        </div>
        <Button onClick={() => startRun.mutate()} disabled={startRun.isPending || activeRun || dirty || files.isPending || files.isError} data-testid="start-run">
          <Play className="mr-1 h-4 w-4" aria-hidden />{t(startRun.isPending ? 'authoring.detail.starting' : 'authoring.detail.validate')}
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">{t('authoring.detail.structureOnly')}</p>
      {dirty && <p role="status" className="text-sm text-muted-foreground">{t('authoring.detail.saveFirst')}</p>}
      <Card><CardContent className="pt-6">
        {files.isPending && <p role="status">{t('authoring.ui.loading')}</p>}
        {files.isError && <QueryError error={files.error} retry={() => void files.refetch()} />}
        {files.isSuccess && <DraftFileEditor draftId={id} files={files.data} revision={current.revision} onSaved={refreshDraft} onDirtyChange={setDirty} />}
      </CardContent></Card>
      <section className="space-y-3" aria-label={t('authoring.detail.tabRuns')}>
        <h2 className="text-lg font-semibold">{t('authoring.detail.tabRuns')}</h2>
        {runs.isPending && <p role="status">{t('authoring.ui.loading')}</p>}
        {runs.isError && <QueryError error={runs.error} retry={() => void runs.refetch()} />}
        {runs.isSuccess && runs.data.length === 0 && <p>{t('authoring.detail.noRuns')}</p>}
        <div className="flex flex-wrap gap-2">
          {runs.data?.map(run => run.id != null && <Button key={run.id} variant={selected === run.id ? 'default' : 'outline'} size="sm" onClick={() => setSelectedRunId(run.id ?? null)} aria-pressed={selected === run.id}>
            #{run.id} · {t(`authoring.run.status.${run.status}`, { defaultValue: run.status })}
          </Button>)}
        </div>
        {selected != null && <ValidationResults key={selected} runId={selected} draftId={id} />}
      </section>
    </div>
  )
}
