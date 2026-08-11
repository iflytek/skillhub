import { ChevronDown, ShieldAlert, ShieldCheck } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { ComplianceMapping, ComplianceSnapshot, SkillVersion } from '@/api/types'
import { cn } from '@/shared/lib/utils'

interface ReviewComplianceDiffPanelProps {
  baseVersion?: SkillVersion | null
  pendingVersion?: SkillVersion | null
  className?: string
}

type DiffKind = 'added' | 'removed' | 'modified'

interface DiffEntry {
  kind: DiffKind
  key: string
  base?: ComplianceMapping
  pending?: ComplianceMapping
}

function shortDigest(digest?: string) {
  if (!digest) {
    return '—'
  }
  if (digest.length <= 20) {
    return digest
  }
  return `${digest.slice(0, 17)}…`
}

function mappingKey(mapping: ComplianceMapping) {
  return [
    mapping.standard?.trim().toLowerCase() ?? '',
    mapping.version?.trim() ?? '',
    mapping.controlId?.trim() ?? '',
  ].join('\u0000')
}

function mappingSignature(mapping: ComplianceMapping) {
  return JSON.stringify({
    standard: mapping.standard?.trim().toLowerCase() ?? '',
    version: mapping.version?.trim() ?? '',
    controlId: mapping.controlId?.trim() ?? '',
    title: mapping.title?.trim() ?? '',
    evidence: (mapping.evidence ?? []).map((item) => ({
      type: item.type?.trim() ?? '',
      path: item.path?.trim() ?? '',
      url: item.url?.trim() ?? '',
      sha256: item.sha256?.trim() ?? '',
    })),
  })
}

function compareComplianceSnapshots(baseSnapshot?: ComplianceSnapshot | null, pendingSnapshot?: ComplianceSnapshot | null) {
  const baseItems = new Map((baseSnapshot?.items ?? []).map((item) => [mappingKey(item), item]))
  const pendingItems = new Map((pendingSnapshot?.items ?? []).map((item) => [mappingKey(item), item]))
  const keys = new Set<string>([...baseItems.keys(), ...pendingItems.keys()])
  const diffs: DiffEntry[] = []

  for (const key of keys) {
    const base = baseItems.get(key)
    const pending = pendingItems.get(key)
    if (base && pending) {
      if (mappingSignature(base) !== mappingSignature(pending)) {
        diffs.push({ kind: 'modified', key, base, pending })
      }
      continue
    }
    if (base) {
      diffs.push({ kind: 'removed', key, base })
      continue
    }
    if (pending) {
      diffs.push({ kind: 'added', key, pending })
    }
  }

  const sorted = diffs.sort((left, right) => {
    const rank: Record<DiffKind, number> = {
      removed: 0,
      modified: 1,
      added: 2,
    }
    return rank[left.kind] - rank[right.kind]
      || (left.base?.standard ?? left.pending?.standard ?? '').localeCompare(right.base?.standard ?? right.pending?.standard ?? '', 'zh-Hans-CN')
      || (left.base?.controlId ?? left.pending?.controlId ?? '').localeCompare(right.base?.controlId ?? right.pending?.controlId ?? '', 'zh-Hans-CN')
  })

  return {
    diffs: sorted,
    added: sorted.filter((item) => item.kind === 'added').length,
    removed: sorted.filter((item) => item.kind === 'removed').length,
    modified: sorted.filter((item) => item.kind === 'modified').length,
  }
}

function formatMappingLabel(mapping?: ComplianceMapping) {
  if (!mapping) {
    return '—'
  }
  const parts = [mapping.standard, mapping.controlId].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '—'
}

function renderEvidenceLabel(path?: string, url?: string, type?: string) {
  return path ?? url ?? type ?? '—'
}

function MappingDetails({
  mapping,
  emptyMessage,
}: {
  mapping?: ComplianceMapping
  emptyMessage: string
}) {
  const { t } = useTranslation()

  if (!mapping) {
    return (
      <div className="rounded-xl border border-dashed border-border/70 bg-muted/20 p-4 text-sm text-muted-foreground">
        {emptyMessage}
      </div>
    )
  }

  return (
    <div className="rounded-xl border border-border/70 bg-background/80 p-4">
      <div className="flex flex-wrap items-center gap-2">
        <span className="rounded-full bg-secondary px-2.5 py-0.5 font-mono text-xs text-secondary-foreground">
          {mapping.standard ?? t('compliance.unknownStandard')}
        </span>
        {mapping.version ? (
          <span className="font-mono text-xs text-muted-foreground">{mapping.version}</span>
        ) : null}
        <span className="font-mono text-sm font-semibold text-foreground">{mapping.controlId ?? '—'}</span>
      </div>

      {mapping.title ? (
        <p className="mt-2 break-words text-sm leading-6 text-foreground">{mapping.title}</p>
      ) : null}

      <div className="mt-3 space-y-2">
        {(mapping.evidence ?? []).length > 0 ? (
          (mapping.evidence ?? []).map((evidence, index) => (
            <div
              key={`${evidence.type ?? 'evidence'}-${evidence.path ?? evidence.url ?? index}`}
              className="flex min-w-0 flex-wrap items-center gap-2 rounded-lg border border-border/60 bg-muted/20 px-3 py-2 text-xs text-muted-foreground"
              title={evidence.sha256}
            >
              <span className="min-w-0 break-words rounded-full bg-background px-2 py-0.5 font-medium text-foreground">
                {renderEvidenceLabel(evidence.path, evidence.url, evidence.type)}
              </span>
              {evidence.sha256 ? (
                <span className="font-mono break-all leading-5">{evidence.sha256}</span>
              ) : null}
            </div>
          ))
        ) : (
          <div className="text-sm text-muted-foreground">{t('compliance.evidence')}</div>
        )}
      </div>
    </div>
  )
}

function DiffItem({ entry }: { entry: DiffEntry }) {
  const { t } = useTranslation()
  const labelKey = entry.kind === 'added'
    ? 'review.complianceDiffAdded'
    : entry.kind === 'removed'
      ? 'review.complianceDiffRemoved'
      : 'review.complianceDiffModified'
  const label = t(labelKey)
  const title = entry.pending?.title ?? entry.base?.title

  return (
    <details className="group rounded-2xl border border-border/70 bg-card/90 p-4 shadow-sm">
      <summary className="flex list-none cursor-pointer items-start gap-3 [&::-webkit-details-marker]:hidden">
        <span
          className={cn(
            'mt-0.5 inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full',
            entry.kind === 'added' && 'bg-emerald-500/10 text-emerald-600',
            entry.kind === 'removed' && 'bg-rose-500/10 text-rose-600',
            entry.kind === 'modified' && 'bg-amber-500/10 text-amber-600',
          )}
        >
          {entry.kind === 'removed' ? <ShieldAlert className="h-4 w-4" /> : <ShieldCheck className="h-4 w-4" />}
        </span>

        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <span
              className={cn(
                'rounded-full px-2.5 py-0.5 text-xs font-medium',
                entry.kind === 'added' && 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300',
                entry.kind === 'removed' && 'bg-rose-500/10 text-rose-700 dark:text-rose-300',
                entry.kind === 'modified' && 'bg-amber-500/10 text-amber-700 dark:text-amber-300',
              )}
            >
              {label}
            </span>
            <span className="font-mono text-sm font-semibold text-foreground">{formatMappingLabel(entry.base ?? entry.pending)}</span>
          </div>

          {title ? <p className="text-sm text-muted-foreground">{title}</p> : null}
        </div>

        <div className="ml-auto flex shrink-0 items-center gap-2 text-xs text-muted-foreground">
          <span>{t('review.complianceDiffViewDetails')}</span>
          <ChevronDown className="h-4 w-4 transition-transform duration-200 group-open:rotate-180" />
        </div>
      </summary>

      <div className="mt-4 grid gap-3 md:grid-cols-2">
        <div className="space-y-2">
          <div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            {t('review.complianceDiffBaseVersion')}
          </div>
          <MappingDetails
            mapping={entry.base}
            emptyMessage={t('review.complianceDiffBaseRemoved')}
          />
        </div>
        <div className="space-y-2">
          <div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            {t('review.complianceDiffPendingVersion')}
          </div>
          <MappingDetails
            mapping={entry.pending}
            emptyMessage={t('review.complianceDiffPendingAdded')}
          />
        </div>
      </div>
    </details>
  )
}

function pickBaseVersion(versions: SkillVersion[], activeVersion: string) {
  const publishedVersions = versions.filter((version) => version.status === 'PUBLISHED' && version.version !== activeVersion)
  if (publishedVersions.length > 0) {
    return publishedVersions.sort((left, right) => {
      const leftTime = new Date(left.publishedAt).getTime()
      const rightTime = new Date(right.publishedAt).getTime()
      if (Number.isFinite(leftTime) && Number.isFinite(rightTime) && leftTime !== rightTime) {
        return rightTime - leftTime
      }
      return right.id - left.id
    })[0]
  }
  return versions.find((version) => version.version !== activeVersion) ?? null
}

export function ReviewComplianceDiffPanel({ baseVersion, pendingVersion, className }: ReviewComplianceDiffPanelProps) {
  const { t } = useTranslation()
  if (!baseVersion || !pendingVersion) {
    return null
  }

  const diff = compareComplianceSnapshots(baseVersion.complianceSnapshot, pendingVersion.complianceSnapshot)
  if (diff.diffs.length === 0) {
    return null
  }

  return (
    <div className={cn('rounded-2xl border border-amber-500/20 bg-amber-500/5 p-4', className)}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <div className="flex flex-wrap items-center gap-2 text-sm font-semibold text-foreground">
            <ShieldAlert className="h-4 w-4 text-amber-500" />
            {t('review.complianceDiffTitle')}
          </div>
          <p className="break-words text-sm leading-6 text-muted-foreground">
            {t('review.complianceDiffDescription', {
              baseVersion: baseVersion.version,
              pendingVersion: pendingVersion.version,
            })}
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <span className="rounded-full bg-emerald-500/10 px-2.5 py-1 text-xs font-medium text-emerald-700 dark:text-emerald-300">
            {t('review.complianceDiffAddedLabel', { count: diff.added })}
          </span>
          <span className="rounded-full bg-rose-500/10 px-2.5 py-1 text-xs font-medium text-rose-700 dark:text-rose-300">
            {t('review.complianceDiffRemovedLabel', { count: diff.removed })}
          </span>
          <span className="rounded-full bg-amber-500/10 px-2.5 py-1 text-xs font-medium text-amber-700 dark:text-amber-300">
            {t('review.complianceDiffModifiedLabel', { count: diff.modified })}
          </span>
        </div>
      </div>

      <div className="mt-4 grid gap-3 md:grid-cols-2">
        <div className="rounded-xl border border-border/60 bg-background/70 p-3">
          <div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            {t('review.complianceDiffBaseDigest')}
          </div>
          <div className="mt-1 min-w-0 break-all font-mono text-xs leading-5 text-foreground" title={baseVersion.complianceSnapshot?.digest}>
            {shortDigest(baseVersion.complianceSnapshot?.digest)}
          </div>
        </div>
        <div className="rounded-xl border border-border/60 bg-background/70 p-3">
          <div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            {t('review.complianceDiffPendingDigest')}
          </div>
          <div className="mt-1 min-w-0 break-all font-mono text-xs leading-5 text-foreground" title={pendingVersion.complianceSnapshot?.digest}>
            {shortDigest(pendingVersion.complianceSnapshot?.digest)}
          </div>
        </div>
      </div>

      <div className="mt-4 grid gap-3">
        {diff.diffs.map((entry) => (
          <DiffItem key={`${entry.kind}-${entry.key}`} entry={entry} />
        ))}
      </div>
    </div>
  )
}

export { compareComplianceSnapshots, pickBaseVersion }
