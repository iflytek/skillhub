export const DASHBOARD_PAGE_TITLE_CLASS_NAME = 'break-words text-[27px] font-medium leading-9 tracking-[-0.8px]'
export const DASHBOARD_PAGE_DESCRIPTION_CLASS_NAME = 'mt-1 text-[13px] leading-5'

interface DashboardPageHeaderProps {
  title: string
  subtitle?: string
  actions?: React.ReactNode
}

/**
 * Standard header used by dashboard sub-pages so navigation and page framing stay consistent.
 *
 * The "back to dashboard" link is intentionally omitted — the sidebar already provides
 * complete navigation and makes a dedicated back link redundant.
 */
export function DashboardPageHeader({ title, subtitle, actions }: DashboardPageHeaderProps) {
  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className={DASHBOARD_PAGE_TITLE_CLASS_NAME} style={{ color: 'hsl(var(--foreground))' }}>{title}</h1>
          {subtitle ? <p className={DASHBOARD_PAGE_DESCRIPTION_CLASS_NAME} style={{ color: 'hsl(var(--text-secondary))' }}>{subtitle}</p> : null}
        </div>
        {actions}
      </div>
    </div>
  )
}
