import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CheckCircle2, ChevronDown, ChevronRight, XCircle } from 'lucide-react'
import type { ValidationFindingInfo } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { previewPatch } from './preview-patch'

const SEVERITY_CLASS = {
  ERROR: 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300',
  WARNING: 'bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300',
} as const

const LINE_CLASS = {
  context: 'text-zinc-500',
  removed: 'bg-red-500/10 text-red-600 dark:text-red-400',
  added: 'bg-green-500/10 text-green-600 dark:text-green-400',
} as const

const LINE_PREFIX = {
  context: '  ',
  removed: '- ',
  added: '+ ',
} as const

interface FindingCardProps {
  finding: ValidationFindingInfo
  onApply: (finding: ValidationFindingInfo) => void
  onDismiss: (finding: ValidationFindingInfo) => void
  applying?: boolean
}

/**
 * One validation finding: where it happened, what is wrong, and — when the
 * validator produced one — a previewable, explicitly confirmed fix. Applying a
 * suggestion always creates a new draft revision; that is enforced server-side.
 */
export function FindingCard({ finding, onApply, onDismiss, applying }: FindingCardProps) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState(false)
  const [confirming, setConfirming] = useState(false)

  const patches = finding.suggestion?.patches ?? []
  const hasSuggestion = patches.length > 0 && finding.status === 'OPEN'

  return (
    <div
      className="rounded-lg border p-4"
      data-testid={`finding-${finding.id}`}
      data-severity={finding.severity}
    >
      <div className="flex flex-wrap items-center gap-2">
        {finding.severity === 'ERROR'
          ? <XCircle className="h-4 w-4 text-red-600" aria-hidden />
          : <CheckCircle2 className="h-4 w-4 text-amber-500" aria-hidden />}
        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${SEVERITY_CLASS[finding.severity]}`}>
          {finding.severity}
        </span>
        <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium">
          {t(`authoring.layer.${finding.layer}`)}
        </span>
        <code className="text-xs text-muted-foreground">{finding.ruleCode}</code>
        {finding.status !== 'OPEN' && (
          <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium" data-testid="finding-status">
            {t(`authoring.findingStatus.${finding.status}`)}
            {finding.appliedRevision != null && ` · ${t('authoring.run.revision')} ${finding.appliedRevision}`}
          </span>
        )}
      </div>

      <p className="mt-2 text-sm">{finding.message}</p>
      {(finding.filePath || finding.location) && (
        <p className="mt-1 text-xs text-muted-foreground" data-testid="finding-location">
          {[finding.filePath, finding.location].filter(Boolean).join(':')}
        </p>
      )}

      {hasSuggestion && (
        <div className="mt-3">
          <button
            type="button"
            className="flex items-center gap-1 text-sm font-medium text-blue-600 dark:text-blue-400"
            onClick={() => setExpanded((value) => !value)}
            aria-expanded={expanded}
            data-testid="suggestion-toggle"
          >
            {expanded ? <ChevronDown className="h-4 w-4" aria-hidden /> : <ChevronRight className="h-4 w-4" aria-hidden />}
            {t('authoring.run.suggestionTitle')}
          </button>
          {finding.suggestion?.description && (
            <p className="mt-1 text-sm text-muted-foreground">{finding.suggestion.description}</p>
          )}

          {expanded && (
            <div className="mt-2 space-y-3">
              {patches.map((patch, index) => (
                <div key={`${patch.filePath}-${index}`}>
                  <p className="mb-1 font-mono text-xs text-muted-foreground">{patch.filePath}</p>
                  <pre className="max-h-64 overflow-auto rounded-md border bg-black/95 p-3 font-mono text-xs leading-5 text-zinc-100">
                    {previewPatch(patch).map((line, lineIndex) => (
                      <div key={lineIndex} className={LINE_CLASS[line.kind]}>
                        {LINE_PREFIX[line.kind]}
                        {line.text}
                      </div>
                    ))}
                  </pre>
                </div>
              ))}
            </div>
          )}

          <div className="mt-3 flex flex-wrap items-center gap-2">
            {confirming ? (
              <>
                <Button size="sm" onClick={() => onApply(finding)} disabled={applying} data-testid="apply-confirm">
                  {applying ? t('authoring.run.applying') : t('authoring.run.applyConfirm')}
                </Button>
                <Button size="sm" variant="outline" onClick={() => setConfirming(false)}>
                  {t('authoring.ui.cancel')}
                </Button>
              </>
            ) : (
              <>
                <Button size="sm" onClick={() => setConfirming(true)} data-testid="apply-start">
                  {t('authoring.run.applyFix')}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => onDismiss(finding)}
                  disabled={applying}
                  data-testid="dismiss-fix"
                >
                  {t('authoring.run.dismissFix')}
                </Button>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
