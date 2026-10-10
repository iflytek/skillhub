import { useState } from 'react'
import { Link, useNavigate, useParams } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Play, Send } from 'lucide-react'
import { authoringApi } from '@/api/client'
import type { RuntimeBindingInfo } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'
import { toast } from '@/shared/lib/toast'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { DraftFileEditor } from '@/features/authoring/draft-file-editor'
import { RuntimeBindingForm } from '@/features/authoring/runtime-binding-form'
import { RunStatusBadge } from '@/features/authoring/run-status-badge'

/**
 * Draft workbench: file editing, runtime binding, validation history, and the
 * submit gate that only opens after the current revision has been validated.
 */
export function DraftDetailPage() {
  const { draftId } = useParams({ from: '/dashboard/authoring/$draftId' })
  const id = Number(draftId)
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [submitOpen, setSubmitOpen] = useState(false)
  const [visibility, setVisibility] = useState<'PRIVATE' | 'PUBLIC'>('PRIVATE')

  const draft = useQuery({
    queryKey: ['authoring', 'draft', id],
    queryFn: () => authoringApi.getDraft(id),
    enabled: Number.isFinite(id),
  })

  const files = useQuery({
    queryKey: ['authoring', 'draft', id, 'files'],
    queryFn: () => authoringApi.listFiles(id),
    enabled: Number.isFinite(id),
  })

  const binding = useQuery({
    queryKey: ['authoring', 'draft', id, 'binding'],
    queryFn: () => authoringApi.getRuntimeBinding(id),
    enabled: Number.isFinite(id),
  })

  const runs = useQuery({
    queryKey: ['authoring', 'draft', id, 'runs'],
    queryFn: () => authoringApi.listRuns(id),
    enabled: Number.isFinite(id),
  })

  const refreshDraft = () => {
    queryClient.invalidateQueries({ queryKey: ['authoring', 'draft', id] })
    queryClient.invalidateQueries({ queryKey: ['authoring', 'draft', id, 'files'] })
    queryClient.invalidateQueries({ queryKey: ['authoring', 'draft', id, 'runs'] })
    queryClient.invalidateQueries({ queryKey: ['authoring', 'drafts'] })
  }

  const startRun = useMutation({
    mutationFn: () => authoringApi.startRun(id),
    onSuccess: (run) => {
      refreshDraft()
      navigate({
        to: '/dashboard/authoring/$draftId/runs/$runId',
        params: { draftId: String(id), runId: String(run.id) },
      })
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : String(error))
    },
  })

  const submit = useMutation({
    mutationFn: () => authoringApi.submitDraft(id, visibility),
    onSuccess: () => {
      setSubmitOpen(false)
      refreshDraft()
      toast.success(t('authoring.detail.submitted'))
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : String(error))
    },
  })

  if (draft.isLoading) {
    return <p className="text-sm text-muted-foreground">{t('authoring.ui.loading')}</p>
  }
  if (draft.isError || !draft.data) {
    return <p className="text-sm text-red-600">{t('authoring.detail.loadFailed')}</p>
  }

  const current = draft.data

  return (
    <div className="space-y-6 animate-fade-up">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="space-y-1">
          <Link
            to="/dashboard/authoring"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden />
            {t('authoring.detail.back')}
          </Link>
          <h1 className="text-2xl font-semibold">
            {current.name}
            <span className="ml-2 text-sm font-normal text-muted-foreground">
              {t('authoring.run.revision')} {current.revision}
            </span>
          </h1>
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            {current.validated ? (
              <span className="rounded-full bg-green-100 px-2 py-0.5 font-medium text-green-700 dark:bg-green-950 dark:text-green-300">
                {t('authoring.detail.validatedAt', { revision: current.validatedRevision ?? current.revision })}
              </span>
            ) : (
              <span className="rounded-full bg-muted px-2 py-0.5 font-medium">
                {t('authoring.list.notValidated')}
              </span>
            )}
            {current.submittedSkillId != null && (
              <span className="rounded-full bg-blue-100 px-2 py-0.5 font-medium text-blue-700 dark:bg-blue-950 dark:text-blue-300">
                {t('authoring.detail.submittedBadge')}
              </span>
            )}
            {current.updatedAt && <span>{formatLocalDateTime(current.updatedAt, i18n.language)}</span>}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button
            onClick={() => startRun.mutate()}
            disabled={startRun.isPending}
            data-testid="start-run"
          >
            <Play className="mr-1 h-4 w-4" aria-hidden />
            {startRun.isPending ? t('authoring.detail.starting') : t('authoring.detail.validate')}
          </Button>
          <Button
            variant="outline"
            disabled={!current.validated || current.submittedSkillId != null || submit.isPending}
            onClick={() => setSubmitOpen(true)}
            data-testid="submit-draft"
          >
            <Send className="mr-1 h-4 w-4" aria-hidden />
            {current.submittedSkillId != null
              ? t('authoring.detail.submittedBadge')
              : t('authoring.detail.submit')}
          </Button>
        </div>
      </div>

      <Tabs defaultValue="files">
        <TabsList>
          <TabsTrigger value="files">{t('authoring.detail.tabFiles')}</TabsTrigger>
          <TabsTrigger value="runtime">{t('authoring.detail.tabRuntime')}</TabsTrigger>
          <TabsTrigger value="runs">{t('authoring.detail.tabRuns')}</TabsTrigger>
        </TabsList>

        <TabsContent value="files">
          <Card>
            <CardContent className="pt-6">
              {files.data && (
                <DraftFileEditor
                  draftId={id}
                  files={files.data}
                  revision={current.revision}
                  onSaved={refreshDraft}
                  onDeleted={refreshDraft}
                />
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="runtime">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t('authoring.detail.runtimeTitle')}</CardTitle>
            </CardHeader>
            <CardContent>
              <RuntimeBindingForm
                draftId={id}
                binding={binding.data as RuntimeBindingInfo | undefined}
                onSaved={refreshDraft}
              />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="runs">
          <Card>
            <CardContent className="pt-6">
              {(!runs.data || runs.data.length === 0) && (
                <p className="py-6 text-center text-sm text-muted-foreground">
                  {t('authoring.detail.noRuns')}
                </p>
              )}
              {runs.data && runs.data.length > 0 && (
                <ul className="divide-y">
                  {runs.data.map((run) => (
                    <li key={run.id} className="flex flex-wrap items-center gap-3 py-3">
                      <RunStatusBadge status={run.status} />
                      <span className="text-sm text-muted-foreground">
                        #{run.id} · {t('authoring.run.revision')} {run.draftRevision}
                      </span>
                      <span className="text-sm">
                        {run.errorCount > 0 && (
                          <span className="mr-2 text-red-600">
                            {t('authoring.run.errors', { count: run.errorCount })}
                          </span>
                        )}
                        {run.warningCount > 0 && (
                          <span className="text-amber-600">
                            {t('authoring.run.warnings', { count: run.warningCount })}
                          </span>
                        )}
                        {run.errorCount === 0 && run.warningCount === 0 && (
                          <span className="text-green-600">{t('authoring.run.clean')}</span>
                        )}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {run.createdAt ? formatLocalDateTime(run.createdAt, i18n.language) : ''}
                      </span>
                      <Link
                        to="/dashboard/authoring/$draftId/runs/$runId"
                        params={{ draftId: String(id), runId: String(run.id) }}
                        className="ml-auto text-sm text-blue-600 hover:underline dark:text-blue-400"
                        data-testid={`open-run-${run.id}`}
                      >
                        {t('authoring.detail.openRun')}
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <Dialog open={submitOpen} onOpenChange={setSubmitOpen}>
        <DialogContent data-testid="submit-dialog">
          <DialogHeader>
            <DialogTitle>{t('authoring.detail.submitTitle')}</DialogTitle>
            <DialogDescription>{t('authoring.detail.submitDescription')}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-2">
            <Label htmlFor="submit-visibility">{t('authoring.detail.visibility')}</Label>
            <Select value={visibility} onValueChange={(value) => setVisibility(value as 'PRIVATE' | 'PUBLIC')}>
              <SelectTrigger id="submit-visibility" data-testid="submit-visibility">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="PRIVATE">{t('authoring.detail.private')}</SelectItem>
                <SelectItem value="PUBLIC">{t('authoring.detail.public')}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSubmitOpen(false)}>
              {t('authoring.ui.cancel')}
            </Button>
            <Button onClick={() => submit.mutate()} disabled={submit.isPending}>
              {submit.isPending ? t('authoring.ui.submitting') : t('authoring.detail.submit')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
