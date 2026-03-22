import { useTranslation } from 'react-i18next'
import type { SecurityVerdict } from './types'

interface VerdictBadgeProps {
  verdict: SecurityVerdict
}

export function VerdictBadge({ verdict }: VerdictBadgeProps) {
  const { t } = useTranslation()

  const styles = {
    SAFE: 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400',
    SUSPICIOUS: 'bg-amber-500/10 text-amber-700 dark:text-amber-400',
    DANGEROUS: 'bg-orange-500/10 text-orange-700 dark:text-orange-400',
    BLOCKED: 'bg-red-500/10 text-red-700 dark:text-red-400',
  }

  return (
    <span
      className={`rounded-full px-2.5 py-0.5 text-sm font-medium ${styles[verdict]}`}
    >
      {t(`securityAudit.verdict.${verdict}`)}
    </span>
  )
}
