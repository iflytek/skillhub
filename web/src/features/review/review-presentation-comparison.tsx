import { useTranslation } from 'react-i18next'
import type { ReviewSkillDetail } from '@/api/types'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { Card } from '@/shared/ui/card'

interface ReviewPresentationComparisonProps {
  detail: ReviewSkillDetail
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
    <section className="space-y-6" aria-label={t('reviewWorkbench.presentationChanges')}>
      <div className="grid grid-cols-2 gap-4 rounded-md bg-secondary/70 p-4 text-sm">
        <div><p className="text-xs text-muted-foreground">{t('reviewWorkbench.baseline')}</p><p className="mt-1 break-all font-medium">{comparison.baseVersion}</p></div>
        <div><p className="text-xs text-muted-foreground">{t('reviewWorkbench.pending')}</p><p className="mt-1 break-all font-medium">{detail.activeVersion}</p></div>
      </div>
      {fields.map(({ key, title, markdown }) => {
        const before = comparison.baseline[key]
        const after = comparison.current[key]
        const unavailable = markdown && ((!before && comparison.baseline.overviewSource !== 'NONE') || (!after && comparison.current.overviewSource !== 'NONE'))
        const changed = before !== after
        return (
          <section key={key} className="space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-base font-semibold">{title}</h3>
              <span className="rounded-full bg-secondary px-2.5 py-1 text-xs text-muted-foreground">{t(unavailable ? 'reviewWorkbench.unavailableComparison' : changed ? 'reviewWorkbench.modified' : 'reviewWorkbench.unchanged')}</span>
            </div>
            <Card className="grid min-w-0 divide-y divide-border/60 overflow-hidden md:grid-cols-2 md:divide-x md:divide-y-0">
              {([{ label: 'before', value: before, snapshot: comparison.baseline }, { label: 'after', value: after, snapshot: comparison.current }] as const).map(({ label, value, snapshot }) => (
                <div key={label} className="min-w-0 space-y-3 p-5">
                  <p className="text-xs font-medium text-muted-foreground">{t(`reviewWorkbench.${label}`)}</p>
                  {value ? markdown ? <MarkdownRenderer content={value} /> : <p className="whitespace-pre-wrap break-words text-sm leading-6">{value}</p>
                    : <p className="text-sm text-muted-foreground">{t(markdown && snapshot.overviewSource !== 'NONE' ? 'reviewWorkbench.overviewUnavailable' : 'reviewWorkbench.notProvided')}</p>}
                </div>
              ))}
            </Card>
          </section>
        )
      })}
      <section className="space-y-3">
        <h3 className="text-base font-semibold">{t('reviewWorkbench.fileChanges')}</h3>
        <div className="divide-y divide-border/60 rounded-md border border-border/60">
          {comparison.files.map((file) => (
            <div key={file.path} className="flex min-w-0 flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm">
              <span className="min-w-0 break-all font-mono text-xs">{file.path}</span>
              <span className="shrink-0 rounded-full bg-secondary px-2.5 py-1 text-xs">{t(`reviewWorkbench.file${file.changeType}`)}</span>
            </div>
          ))}
          {comparison.files.length === 0 && <p className="p-4 text-sm text-muted-foreground">{t('skillDetail.noFiles')}</p>}
        </div>
      </section>
    </section>
  )
}
