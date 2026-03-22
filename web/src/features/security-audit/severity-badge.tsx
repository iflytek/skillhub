import { useTranslation } from 'react-i18next'
import type { FindingSeverity } from './types'

interface SeverityBadgeProps {
  severity: FindingSeverity
}

export function SeverityBadge({ severity }: SeverityBadgeProps) {
  const { t } = useTranslation()

  const styles = {
    CRITICAL: 'bg-red-500/10 text-red-700 dark:text-red-400',
    HIGH: 'bg-orange-500/10 text-orange-700 dark:text-orange-400',
    MEDIUM: 'bg-amber-500/10 text-amber-700 dark:text-amber-400',
    LOW: 'bg-blue-500/10 text-blue-700 dark:text-blue-400',
    INFO: 'bg-gray-500/10 text-gray-700 dark:text-gray-400',
  }

  return (
    <span
      className={`rounded-full px-2 py-0.5 text-xs font-medium ${styles[severity]}`}
    >
      {t(`securityAudit.severity.${severity}`)}
    </span>
  )
}
