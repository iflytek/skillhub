import { ArrowLeft } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '@/shared/ui/button'

interface SuiteWorkspaceHeaderProps {
  title: string
  description?: string
  eyebrow?: string
  backLabel?: string
  onBack?: () => void
  actions?: ReactNode
}

/** Compact, consistent page framing for the dashboard Suite workflow. */
export function SuiteWorkspaceHeader({
  title,
  description,
  eyebrow,
  backLabel,
  onBack,
  actions,
}: SuiteWorkspaceHeaderProps) {
  return (
    <header className="space-y-2">
      {backLabel && onBack ? (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="-ml-2 h-6 px-2 text-[11px] text-muted-foreground hover:text-foreground"
          onClick={onBack}
        >
          <ArrowLeft className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />
          {backLabel}
        </Button>
      ) : null}
      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
        <div className="min-w-0">
          {eyebrow ? <p className="mb-1 text-xs text-muted-foreground">{eyebrow}</p> : null}
          <h1 className="break-words text-lg font-bold tracking-tight sm:text-xl">{title}</h1>
          {description ? <p className="mt-1 max-w-3xl text-xs leading-5 text-muted-foreground sm:text-sm">{description}</p> : null}
        </div>
        {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
      </div>
    </header>
  )
}
