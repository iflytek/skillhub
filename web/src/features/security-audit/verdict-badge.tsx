import { useTranslation } from 'react-i18next'
import type { SecurityAuditDisplayState, SecurityVerdict } from './types'

interface VerdictBadgeProps {
  verdict?: SecurityVerdict
  displayState?: SecurityAuditDisplayState
}

export function VerdictBadge({ verdict, displayState }: VerdictBadgeProps) {
  const { t } = useTranslation()
  const state = displayState ?? verdict

  if (!state) {
    return null
  }

  const styles = {
    SCANNING: 'bg-sky-500/10 text-sky-700 dark:text-sky-400',
    SAFE: 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400',
    SUSPICIOUS: 'bg-amber-500/10 text-amber-700 dark:text-amber-400',
    DANGEROUS: 'bg-orange-500/10 text-orange-700 dark:text-orange-400',
    BLOCKED: 'bg-red-500/10 text-red-700 dark:text-red-400',
  }

  const label = state === 'SCANNING'
    ? t('securityAudit.statusScanning')
    : t(`securityAudit.verdict.${state}`)

  return (
    <span
      className={`rounded-full px-2.5 py-0.5 text-sm font-medium ${styles[state]}`}
    >
      {label}
    </span>
  )
}
