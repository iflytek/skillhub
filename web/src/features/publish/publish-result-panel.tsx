import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { CheckCircle2, AlertCircle, Loader2 } from 'lucide-react'
import type { PublishResult } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { cn } from '@/shared/lib/utils'
import { publishResultPhase, type PublishSubmissionState } from './publish-result'

export interface PublishResultPanelProps {
  state: PublishSubmissionState
  onReturnToForm: () => void
  onReplacePackage: () => void
  onViewMySkills: () => void
  onViewVersion: (result: PublishResult) => void
}

/** Present exactly one submission outcome. Callbacks navigate/read; this panel never writes. */
export function PublishResultPanel({ state, onReturnToForm, onReplacePackage, onViewMySkills, onViewVersion }: PublishResultPanelProps) {
  const { t } = useTranslation()
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { heading.current?.focus() }, [state.kind])
  const submitting = state.kind === 'submitting'
  const phase = state.kind === 'accepted' ? publishResultPhase(state.result.status) : undefined
  const failedPhase = phase === 'SCAN_FAILED' || phase === 'REJECTED' || phase === 'YANKED'
  const error = state.kind === 'error' || state.kind === 'version-conflict' || failedPhase
  const key = phase ? `accepted.${phase}` : state.kind === 'error' ? `error.${state.reason}` : state.kind
  const parameters = state.kind === 'accepted'
    ? { skill: `@${state.result.namespace}/${state.result.slug}`, version: state.result.version }
    : state.kind === 'submitting' ? { skill: state.label, version: state.version ?? '' } : state.kind === 'version-conflict' ? { version: state.version ?? '' } : {}

  return (
    <section aria-busy={submitting} aria-labelledby="publish-result-heading" role={error ? 'alert' : 'status'} className={cn('rounded-xl border p-5 sm:p-7', error ? 'border-destructive/25 bg-destructive/5' : state.kind === 'accepted' && phase === 'PUBLISHED' ? 'border-emerald-600/20 bg-emerald-600/5' : 'border-border bg-muted/30')}>
      <div className="flex items-start gap-3">
        {submitting ? <Loader2 aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0 animate-spin motion-reduce:animate-none" /> : error || state.kind === 'unknown' ? <AlertCircle aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0" /> : <CheckCircle2 aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0" />}
        <div className="min-w-0 flex-1">
          <h2 ref={heading} tabIndex={-1} id="publish-result-heading" className="break-words text-lg font-semibold outline-none focus-visible:ring-2 focus-visible:ring-ring">{t(`publish.result.${key}.title`, parameters)}</h2>
          <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-muted-foreground">{t(`publish.result.${key}.description`, parameters)}</p>
          {state.kind === 'accepted' && <p className="mt-2 text-xs text-muted-foreground">{t('publish.result.receiptHint')}</p>}
          {state.kind === 'accepted' && <p className="mt-2 break-all text-xs text-muted-foreground">@{state.result.namespace}/{state.result.slug} · {state.result.version}</p>}
          {state.kind === 'accepted' && state.hadPublishedVersion && phase !== 'PUBLISHED' && <p className="mt-2 text-sm text-muted-foreground">{t('publish.result.previousPublished')}</p>}
          <div className="mt-5 flex flex-wrap gap-3">
            {submitting ? <Button type="button" disabled>{t('publish.result.submitting.button')}</Button> : state.kind === 'accepted' ? <>
              <Button type="button" onClick={() => onViewVersion(state.result)}>{t('publish.result.viewVersion')}</Button>
              <Button type="button" variant="outline" onClick={onViewMySkills}>{t('publish.result.mySkills')}</Button>
            </> : state.kind === 'version-conflict' ? <Button type="button" onClick={onReplacePackage}>{t('publish.result.replacePackage')}</Button> : <>
              {state.kind === 'unknown' && <Button type="button" onClick={onViewMySkills}>{t('publish.result.checkMySkills')}</Button>}
              <Button type="button" variant={state.kind === 'unknown' ? 'outline' : 'default'} onClick={onReturnToForm}>{t('publish.result.returnToForm')}</Button>
            </>}
          </div>
        </div>
      </div>
    </section>
  )
}
