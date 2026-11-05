import { ChevronDown } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { ReviewSkillDetail } from '@/api/types'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { cn } from '@/shared/lib/utils'

interface ReviewPresentationComparisonProps {
  detail: ReviewSkillDetail
}

const COMPARISON_GRID = 'grid min-w-0 grid-cols-[repeat(auto-fit,minmax(min(100%,28rem),1fr))] gap-3'
const COMPARISON_MARKDOWN = 'text-sm leading-6 [&_h1]:mb-4 [&_h1]:pb-2 [&_h1]:text-xl [&_h1]:leading-7 [&_h2]:mb-3 [&_h2]:mt-6 [&_h2]:pb-2 [&_h2]:text-lg [&_h2]:leading-7 [&_h3]:mb-2 [&_h3]:mt-5 [&_h3]:text-base [&_h3]:leading-6 [&_h4]:text-sm [&_p]:my-3 [&_p]:text-sm [&_p]:leading-6 [&_li]:text-sm [&_li]:leading-6 [&_pre]:text-xs [&_table]:text-sm [&>:first-child]:mt-0 [&>:last-child]:mb-0'
const CHANGED_STATUS = 'border-amber-500/30 bg-amber-500/10 text-amber-900 dark:text-amber-200'
const FILE_STATUS: Record<string, string> = {
  ADDED: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-800 dark:text-emerald-200',
  MODIFIED: CHANGED_STATUS,
  REMOVED: 'border-red-500/30 bg-red-500/10 text-red-800 dark:text-red-200',
}

/** Compares only snapshots authorized and selected by the review endpoint. */
export function ReviewPresentationComparison({ detail }: ReviewPresentationComparisonProps) {
  const { t } = useTranslation()
  const comparison = detail.comparison
  if (!comparison) return <p className="text-sm text-muted-foreground">{t('reviewWorkbench.noBaseline')}</p>

  const fields = [
    { key: 'displayName', title: t('publish.displayName'), markdown: false },
    { key: 'summary', title: t('reviewWorkbench.summary'), markdown: false },
    { key: 'overview', title: t('reviewWorkbench.overview'), markdown: true },
  ] as const

  return (
    <section className="space-y-5" aria-label={t('reviewWorkbench.presentationChanges')}>
      <div className="grid grid-cols-2 gap-4 rounded-lg bg-secondary/70 p-4 text-sm">
        <div><p className="text-xs text-muted-foreground">{t('reviewWorkbench.baseline')}</p><p className="mt-1 break-all font-medium">{comparison.baseVersion}</p></div>
        <div><p className="text-xs text-muted-foreground">{t('reviewWorkbench.pending')}</p><p className="mt-1 break-all font-medium">{detail.activeVersion}</p></div>
      </div>
      {fields.map(({ key, title, markdown }) => {
        const before = comparison.baseline[key]
        const after = comparison.current[key]
        const unavailable = markdown && ((!before && comparison.baseline.overviewSource !== 'NONE') || (!after && comparison.current.overviewSource !== 'NONE'))
        const changed = before !== after
        return (
          <details key={key} open={changed || unavailable} className="group rounded-lg border border-border/60">
            <summary className="flex min-h-11 cursor-pointer list-none flex-wrap items-center gap-3 rounded-lg px-4 py-3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring [&::-webkit-details-marker]:hidden">
              <ChevronDown aria-hidden="true" className="h-4 w-4 shrink-0 -rotate-90 text-muted-foreground group-open:rotate-0" />
              <h3 className="min-w-0 flex-1 text-sm font-semibold">{title}</h3>
              <span className={cn('rounded-full border px-2.5 py-1 text-xs font-medium', changed || unavailable ? CHANGED_STATUS : 'border-border/60 bg-secondary text-muted-foreground')}>
                {t(unavailable ? 'reviewWorkbench.unavailableComparison' : changed ? 'reviewWorkbench.modified' : 'reviewWorkbench.unchanged')}
              </span>
            </summary>
            <div className={cn(COMPARISON_GRID, 'border-t border-border/60 p-3 sm:p-4')}>
              {([{ label: 'before', value: before, snapshot: comparison.baseline }, { label: 'after', value: after, snapshot: comparison.current }] as const).map(({ label, value, snapshot }) => (
                <div key={label} className={cn('min-w-0 space-y-3 rounded-lg border border-border/60 bg-card p-4 sm:p-5', label === 'after' && changed && 'border-amber-500/40 bg-amber-500/5 dark:border-amber-400/30 dark:bg-amber-400/5')}>
                  <p className={cn('text-xs font-medium', label === 'after' && changed ? 'text-amber-900 dark:text-amber-200' : 'text-muted-foreground')}>{t(`reviewWorkbench.${label}`)}</p>
                  {value ? markdown ? <MarkdownRenderer content={value} className={COMPARISON_MARKDOWN} /> : <p className="whitespace-pre-wrap break-words text-sm leading-6">{value}</p>
                    : <p className="text-sm text-muted-foreground">{t(markdown && snapshot.overviewSource !== 'NONE' ? 'reviewWorkbench.overviewUnavailable' : 'reviewWorkbench.notProvided')}</p>}
                </div>
              ))}
            </div>
          </details>
        )
      })}
      <section className="space-y-3">
        <h3 className="text-base font-semibold">{t('reviewWorkbench.fileChanges')}</h3>
        <div className="divide-y divide-border/60 rounded-lg border border-border/60">
          {comparison.files.map((file) => (
            <div key={file.path} className="flex min-w-0 flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm">
              <span className="min-w-0 break-all font-mono text-xs">{file.path}</span>
              <span className={cn('shrink-0 rounded-full border px-2.5 py-1 text-xs font-medium', FILE_STATUS[file.changeType] ?? 'border-border/60 bg-secondary text-muted-foreground')}>{t(`reviewWorkbench.file${file.changeType}`)}</span>
            </div>
          ))}
          {comparison.files.length === 0 && <p className="p-4 text-sm text-muted-foreground">{t('skillDetail.noFiles')}</p>}
        </div>
      </section>
    </section>
  )
}
