import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'

type VersionStatus =
  | 'DRAFT'
  | 'SCANNING'
  | 'SCAN_FAILED'
  | 'UPLOADED'
  | 'PENDING_REVIEW'
  | 'PUBLISHED'
  | 'REJECTED'
  | 'YANKED'

const statusStyles: Record<VersionStatus, string> = {
  PUBLISHED:
    'border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400',
  UPLOADED:
    'border-blue-500/30 bg-blue-500/10 text-blue-700 dark:text-blue-400',
  PENDING_REVIEW:
    'border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-400',
  REJECTED:
    'border-red-500/30 bg-red-500/10 text-red-700 dark:text-red-400',
  SCANNING:
    'border-purple-500/30 bg-purple-500/10 text-purple-700 dark:text-purple-400',
  SCAN_FAILED:
    'border-red-500/30 bg-red-500/10 text-red-700 dark:text-red-400',
  YANKED:
    'border-border/60 bg-secondary/40 text-muted-foreground',
  DRAFT:
    'border-border/60 bg-secondary/40 text-muted-foreground',
}

const i18nKeys: Record<VersionStatus, string> = {
  DRAFT: 'skillDetail.versionStatusDraft',
  SCANNING: 'skillDetail.versionStatusScanning',
  SCAN_FAILED: 'skillDetail.versionStatusScanFailed',
  UPLOADED: 'skillDetail.versionStatusUploaded',
  PENDING_REVIEW: 'skillDetail.versionStatusPendingReview',
  PUBLISHED: 'skillDetail.versionStatusPublished',
  REJECTED: 'skillDetail.versionStatusRejected',
  YANKED: 'skillDetail.versionStatusYanked',
}

/** Color-coded row styles (left-border + subtle background) for version cards. */
export const versionRowStyles: Record<VersionStatus, string> = {
  UPLOADED:
    'border-l-[3px] !border-l-blue-500 bg-blue-500/[0.03]',
  PENDING_REVIEW:
    'border-l-[3px] !border-l-amber-500 bg-amber-500/[0.03]',
  REJECTED:
    'border-l-[3px] !border-l-red-500 bg-red-500/[0.04]',
  SCANNING:
    'border-l-[3px] !border-l-purple-500 bg-purple-500/[0.03]',
  SCAN_FAILED:
    'border-l-[3px] !border-l-red-500 bg-red-500/[0.04]',
  PUBLISHED: '',
  YANKED: '',
  DRAFT: '',
}

export function getVersionRowStyle(status?: string): string {
  if (!status) return ''
  return versionRowStyles[status as VersionStatus] ?? ''
}

export function VersionStatusBadge({
  status,
  className,
}: {
  status?: string
  className?: string
}) {
  const { t } = useTranslation()
  if (!status) return null

  const style = statusStyles[status as VersionStatus] ?? statusStyles.DRAFT
  const label = i18nKeys[status as VersionStatus]
    ? t(i18nKeys[status as VersionStatus])
    : status

  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium',
        style,
        className,
      )}
    >
      {label}
    </span>
  )
}
