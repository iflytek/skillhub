import { useTranslation } from 'react-i18next'
import type { ValidationRunStatus } from '@/api/types'

const STATUS_CLASS: Record<ValidationRunStatus, string> = {
  QUEUED: 'bg-muted text-muted-foreground',
  PREPARING: 'bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300',
  RUNNING: 'bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300 animate-pulse',
  SUCCEEDED: 'bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-300',
  FAILED: 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300',
  CANCELLED: 'bg-muted text-muted-foreground',
  TIMED_OUT: 'bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300',
}

/** Colored status pill for a validation run. */
export function RunStatusBadge({ status }: { status: ValidationRunStatus }) {
  const { t } = useTranslation()
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASS[status] ?? STATUS_CLASS.QUEUED}`}
      data-testid="run-status"
    >
      {t(`authoring.runStatus.${status}`)}
    </span>
  )
}
