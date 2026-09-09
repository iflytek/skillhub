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
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight" style={{ color: 'hsl(var(--foreground))' }}>{title}</h1>
          {subtitle ? <p className="mt-1 text-sm" style={{ color: 'hsl(var(--text-secondary))' }}>{subtitle}</p> : null}
        </div>
        {actions}
      </div>
    </div>
  )
}
