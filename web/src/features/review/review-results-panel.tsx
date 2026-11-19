import type { ReactNode } from 'react'

/** Selection actions, rows and pagination form one review surface. */
export function ReviewResultsPanel({ header, children, footer, busy, selected }: {
  header: ReactNode
  children: ReactNode
  footer?: ReactNode
  busy?: boolean
  selected?: boolean
}) {
  return <section id="review-results" tabIndex={-1} data-review-results-panel aria-busy={busy} className="rounded-xl border bg-card">
    <div data-review-results-toolbar className={`sticky top-14 z-10 flex min-h-16 flex-wrap items-center justify-between gap-3 rounded-t-xl border-b ${selected ? 'bg-accent' : 'bg-card'} px-4 py-3 [&_button]:min-h-11 sm:[&_button]:min-h-8`}>{header}</div>
    {children}
    {footer && <div className="flex flex-wrap items-center justify-between gap-3 border-t px-4 py-3 text-xs text-muted-foreground">{footer}</div>}
  </section>
}
