import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Check, CheckCheck, AlertCircle, Loader2, RefreshCw, X, XCircle } from 'lucide-react'
import { reviewApi } from '@/api/client'
import type { BatchReviewRequest, BatchReviewResponse, ReviewTask } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Textarea } from '@/shared/ui/textarea'

export const BATCH_REVIEW_LIMIT = 50
export function isBatchReviewTask(task: ReviewTask) {
  return task.status === 'PENDING' && task.subjectType === 'SKILL_VERSION'
    && !task.requestedVisibility && !task.avatarCandidateId && !task.avatarRemoval
}

export function useBatchReview(filterKey: string) {
  const { t } = useTranslation()
  const client = useQueryClient()
  const selectionRestore = useRef({ scope: filterKey, ids: (client.getQueryData<number[]>(['reviews', 'selection', filterKey]) ?? []).slice(0, BATCH_REVIEW_LIMIT), applied: false })
  const restoredSelection = useQuery({
    queryKey: ['review-selection-restore', filterKey, selectionRestore.current.ids],
    queryFn: async () => Promise.all(selectionRestore.current.ids.map(id => reviewApi.get(id).catch(error => { if (typeof error === 'object' && error !== null && 'status' in error && (error.status === 403 || error.status === 404)) return null; throw error }))),
    enabled: selectionRestore.current.scope === filterKey && selectionRestore.current.ids.length > 0 && !selectionRestore.current.applied,
    retry: false, gcTime: 0,
  })
  const [selected, setSelected] = useState<ReviewTask[]>([])
  useEffect(() => {
    if (restoredSelection.data && selectionRestore.current.scope === filterKey && !selectionRestore.current.applied) {
      selectionRestore.current.applied = true
      setSelected(restoredSelection.data.filter((task): task is ReviewTask => !!task && isBatchReviewTask(task)))
    }
  }, [filterKey, restoredSelection.data])
  const [action, setAction] = useState<BatchReviewRequest['action']>('APPROVE')
  const [open, setOpen] = useState(false)
  const [comment, setComment] = useState('')
  const [invalid, setInvalid] = useState(false)
  const [result, setResult] = useState<BatchReviewResponse | null>(null)
  const [uncertain, setUncertain] = useState(false)
  const [clearOpen, setClearOpen] = useState(false)
  const pendingFilter = useRef<(() => void) | null>(null)
  const reasonInput = useRef<HTMLTextAreaElement>(null)
  const submitting = useRef(false)
  const snapshot = useRef<ReviewTask[]>([])
  const returnFocus = useRef<HTMLElement | null>(null)
  const previousFilter = useRef(filterKey)
  const activeScope = useRef(filterKey)
  activeScope.current = filterKey
  useEffect(() => {
    if (previousFilter.current !== filterKey) {
      client.removeQueries({ queryKey: ['reviews', 'selection', previousFilter.current], exact: true })
      previousFilter.current = filterKey
      setSelected([])
      setOpen(false)
      setResult(null)
      snapshot.current = []
      return
    }
    if (selectionRestore.current.scope === filterKey && selectionRestore.current.ids.length && !selectionRestore.current.applied) return
    client.setQueryData(['reviews', 'selection', filterKey], selected.map(task => task.id))
  }, [client, filterKey, selected])

  const refresh = async () => {
    await Promise.all(['reviews', 'governance', 'skills', 'skill-avatar'].map(key =>
      client.invalidateQueries({ queryKey: [key] })))
  }
  const preview = useMutation({
    mutationFn: (request: BatchReviewRequest) => reviewApi.batchPreview(request), retry: false,
  })
  const decide = useMutation({
    mutationFn: (request: BatchReviewRequest) => reviewApi.batchDecide(request), retry: false,
    onMutate: () => filterKey,
    onSuccess: (response, _request, scope) => {
      if (scope !== activeScope.current) return
      const excluded = preview.data?.items.filter(item => item.outcome !== 'READY') ?? []
      setResult({ items: [...response.items, ...excluded] })
      const completed = new Set(response.items.filter(item => ['APPROVED', 'REJECTED'].includes(item.outcome)).map(item => item.id))
      setSelected(items => items.filter(item => !completed.has(item.id)))
      setUncertain(response.items.some(item => item.outcome === 'UNKNOWN'))
      void refresh()
    },
    onError: (_error, _request, scope) => { if (scope === activeScope.current) { setUncertain(true); void refresh() } },
    onSettled: () => { submitting.current = false },
  })
  const busy = preview.isPending || decide.isPending || restoredSelection.isFetching || (restoredSelection.isError && !selectionRestore.current.applied)
  const items = snapshot.current
  const readyIds = preview.data?.items.filter(item => item.outcome === 'READY').map(item => item.id) ?? []
  const ready = new Set(readyIds)
  const start = (next: BatchReviewRequest['action']) => {
    if (!selected.length || busy) return
    returnFocus.current = document.activeElement as HTMLElement
    snapshot.current = [...selected]
    setAction(next); setComment(''); setInvalid(false); setResult(null); setUncertain(false)
    decide.reset(); preview.reset(); setOpen(true)
    preview.mutate({ ids: selected.map(item => item.id), action: next })
  }
  const reconcile = useMutation({
    mutationFn: async () => {
      const states = await Promise.all(snapshot.current.map(async task => {
        try { return { task, current: await reviewApi.get(task.id) } }
        catch { return { task, current: null } }
      }))
      return states
    }, retry: false,
    onMutate: () => filterKey,
    onSuccess: (states, _variables, scope) => {
      if (scope !== activeScope.current) return
      // Never infer success from a lost response; show the latest actual status instead.
      setResult({ items: states.map(({ task, current }) => ({ id: task.id,
        outcome: current ? current.status : 'UNKNOWN', reason: 'RECONCILED',
        message: current ? t('reviewBatch.currentStatus', { status: t(`reviewCenter.status.${current.status}`) }) : t('reviewBatch.unknown'),
      })) })
      setSelected(states.filter(({ current }) => current && isBatchReviewTask(current)).map(({ current }) => current!))
      setUncertain(states.some(({ current }) => !current))
      void refresh()
    },
  })
  const toggle = (task: ReviewTask) => setSelected(old => old.some(item => item.id === task.id)
    ? old.filter(item => item.id !== task.id) : old.length < BATCH_REVIEW_LIMIT ? [...old, task] : old)
  const checkbox = (task: ReviewTask) => <input type="checkbox"
    className="h-4 w-4 rounded accent-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring"
    aria-label={t('reviewBatch.selectTask', { name: task.displayName || task.skillSlug || task.id })}
    checked={selected.some(item => item.id === task.id)}
    disabled={busy || !isBatchReviewTask(task) || selected.length >= BATCH_REVIEW_LIMIT && !selected.some(item => item.id === task.id)}
    onChange={() => toggle(task)} />
  const selectPage = (tasks: ReviewTask[]) => {
    const candidates = tasks.filter(isBatchReviewTask)
    const all = candidates.length > 0 && candidates.every(task => selected.some(item => item.id === task.id))
    setSelected(old => all ? old.filter(item => !candidates.some(task => task.id === item.id))
      : [...old, ...candidates.filter(task => !old.some(item => item.id === task.id))].slice(0, BATCH_REVIEW_LIMIT))
  }
  const guardFilter = (callback: () => void) => {
    if (busy) return
    if (!selected.length) callback()
    else { pendingFilter.current = callback; setClearOpen(true) }
  }
  const submit = () => {
    if (submitting.current || !readyIds.length || decide.isPending || result || uncertain) return
    if (action === 'REJECT' && !comment.trim()) { setInvalid(true); reasonInput.current?.focus(); return }
    submitting.current = true
    decide.mutate({ ids: readyIds, action, comment: comment.trim() || undefined })
  }

  const toolbar = restoredSelection.isError && !selectionRestore.current.applied ? <div role="alert" className="flex w-full flex-wrap items-center gap-3"><p className="text-sm">{t('reviewGroups.restoreFailed')}</p><Button variant="outline" onClick={() => void restoredSelection.refetch()}>{t('reviewCenter.retry')}</Button><Button variant="outline" onClick={() => { selectionRestore.current.applied = true; setSelected([]) }}>{t('reviewGroups.reselect')}</Button></div> : selected.length > 0 ? <div className="flex w-full flex-wrap items-center justify-between gap-3 [&_button]:min-h-11 sm:[&_button]:min-h-8" role="region" aria-label={t('reviewBatch.selection')}>
    <p className="text-sm font-medium" aria-live="polite">{t('reviewBatch.selected', { count: selected.length })}<span className="ml-2 text-xs font-normal text-muted-foreground">{t('reviewBatch.limit', { count: BATCH_REVIEW_LIMIT })}</span></p>
    <div className="flex flex-wrap gap-2">
      <Button variant="ghost" size="sm" disabled={busy} onClick={() => setSelected([])}><X className="mr-1.5 h-4 w-4" />{t('reviewBatch.clear')}</Button>
      <Button variant="outline" size="sm" disabled={busy} onClick={() => start('REJECT')}><XCircle className="mr-1.5 h-4 w-4" />{t('reviewBatch.reject')}</Button>
      <Button size="sm" disabled={busy} onClick={() => start('APPROVE')}><CheckCheck className="mr-1.5 h-4 w-4" />{t('reviewBatch.approve')}</Button>
    </div>
  </div> : null
  const dialogs = <>
    <Dialog open={clearOpen} onOpenChange={setClearOpen}><DialogContent className="[&>button]:h-11 [&>button]:w-11 sm:[&>button]:h-7 sm:[&>button]:w-7"><DialogHeader><DialogTitle>{t('reviewBatch.changeFilters')}</DialogTitle><DialogDescription>{t('reviewBatch.clearWarning')}</DialogDescription></DialogHeader><DialogFooter><Button variant="outline" className="h-11 sm:h-8" onClick={() => setClearOpen(false)}>{t('reviewBatch.cancel')}</Button><Button className="h-11 sm:h-8" onClick={() => { client.setQueryData(['reviews', 'selection', filterKey], []); setSelected([]); setClearOpen(false); pendingFilter.current?.(); pendingFilter.current = null }}>{t('reviewBatch.continue')}</Button></DialogFooter></DialogContent></Dialog>
    <Dialog open={open} onOpenChange={next => { if (!decide.isPending && !reconcile.isPending) setOpen(next) }}>
      <DialogContent className="flex max-h-[min(48rem,calc(100dvh-2rem))] w-[min(calc(100vw-2rem),48rem)] flex-col gap-0 overflow-hidden p-0" hideClose
        onCloseAutoFocus={event => {
          event.preventDefault()
          if (returnFocus.current?.isConnected) returnFocus.current.focus()
          if (document.activeElement !== returnFocus.current) document.getElementById('review-results')?.focus()
        }}>
        {!decide.isPending && !reconcile.isPending && <Button variant="ghost" size="icon" className="absolute right-3 top-3 h-11 w-11 md:h-7 md:w-7" aria-label={t('reviewBatch.close')} onClick={() => setOpen(false)}><X className="h-4 w-4" /></Button>}
        <DialogHeader className="shrink-0 border-b px-6 py-5 pr-16 text-left md:pr-12">
          <DialogTitle className="flex items-center gap-2">{result ? <CheckCheck className="h-5 w-5" /> : action === 'APPROVE' ? <Check className="h-5 w-5" /> : <XCircle className="h-5 w-5" />}{t(result ? 'reviewBatch.resultTitle' : action === 'APPROVE' ? 'reviewBatch.approveTitle' : 'reviewBatch.rejectTitle')}</DialogTitle>
          <DialogDescription>{t('reviewBatch.summary', { selected: items.length, ready: readyIds.length })}</DialogDescription>
        </DialogHeader>
        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-6 py-4">
          {preview.isPending && <p role="status" className="flex items-center gap-2 text-sm"><Loader2 className="h-4 w-4 animate-spin" />{t('reviewBatch.checking')}</p>}
          {preview.isError && <p role="alert" className="text-sm text-destructive">{t('reviewBatch.previewFailed')}</p>}
          {(uncertain || decide.isError) && <p role="alert" className="mb-4 rounded-lg bg-warning/10 p-3 text-sm">{t('reviewBatch.unknown')}</p>}
          <ul className="divide-y" aria-label={t('reviewBatch.tasks')}>
            {(result?.items ?? preview.data?.items ?? []).map(item => {
              const task = items.find(task => task.id === item.id)
              const success = ['APPROVED', 'REJECTED'].includes(item.outcome)
              return <li key={item.id} className="flex min-w-0 items-start gap-3 py-3 text-sm">
                {success || item.outcome === 'READY' ? <Check className="mt-0.5 h-4 w-4 shrink-0 text-success" /> : <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-warning" />}
                <div className="min-w-0 flex-1"><p className="break-words font-medium [overflow-wrap:anywhere]">{task?.displayName || task?.skillSlug || `#${item.id}`}</p><p className="mt-1 break-all text-xs text-muted-foreground">@{task?.namespace} · {task?.version}</p><p className="mt-1 break-words text-xs">{item.message}</p></div>
              </li>
            })}
          </ul>
          {!result && !uncertain && preview.isSuccess && <div className="mt-4 space-y-2">
            {!ready.size && <p role="status" className="text-sm text-muted-foreground">{t('reviewBatch.noneReady')}</p>}
            <label htmlFor="batch-review-comment" className="block text-sm font-medium">{t(action === 'REJECT' ? 'reviewBatch.reason' : 'reviewBatch.comment')}</label>
            <Textarea ref={reasonInput} id="batch-review-comment" maxLength={2000} className="min-h-20 resize-y" value={comment} disabled={decide.isPending}
              aria-invalid={invalid} aria-describedby={invalid ? 'batch-reason-error' : undefined}
              onChange={event => { setComment(event.target.value); setInvalid(false) }} />
            {invalid && <p id="batch-reason-error" role="alert" className="text-xs text-destructive">{t('reviewBatch.reasonRequired')}</p>}
            <p className="text-xs text-muted-foreground">{t('reviewBatch.independent')}</p>
          </div>}
        </div>
        <DialogFooter className="shrink-0 flex-row justify-end border-t bg-card px-6 py-4 [&_button]:min-h-11 md:[&_button]:min-h-0">
          {result || uncertain ? <><Button variant="outline" disabled={reconcile.isPending} onClick={() => reconcile.mutate()}><RefreshCw className="mr-1.5 h-4 w-4" />{t('reviewBatch.refresh')}</Button><Button disabled={reconcile.isPending} onClick={() => setOpen(false)}>{t('reviewBatch.done')}</Button></> : <><Button variant="outline" disabled={decide.isPending} onClick={() => setOpen(false)}>{t('reviewBatch.cancel')}</Button><Button variant={action === 'REJECT' ? 'destructive' : 'default'} disabled={!preview.isSuccess || !ready.size || decide.isPending} onClick={submit}>{decide.isPending && <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />}{t(decide.isPending ? 'reviewBatch.processing' : action === 'APPROVE' ? 'reviewBatch.confirmApprove' : 'reviewBatch.confirmReject', { count: ready.size })}</Button></>}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </>
  return { selected, checkbox, selectPage, toolbar, dialogs, guardFilter, busy }
}
