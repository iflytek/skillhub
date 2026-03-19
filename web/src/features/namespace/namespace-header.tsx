import { useTranslation } from 'react-i18next'
import type { Namespace } from '@/api/types'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { cn } from '@/shared/lib/utils'

interface NamespaceHeaderProps {
  namespace: Namespace
}

/**
 * Header block for namespace pages and namespace-oriented dashboard views.
 */
export function NamespaceHeader({ namespace }: NamespaceHeaderProps) {
  const { t } = useTranslation()
  const statusLabel = namespace.status === 'FROZEN'
    ? t('namespaceStatus.frozen')
    : namespace.status === 'ARCHIVED'
      ? t('namespaceStatus.archived')
      : t('namespaceStatus.active')
  const statusClassName = namespace.status === 'FROZEN'
    ? 'bg-amber-500/10 text-amber-500 border-amber-500/20'
    : namespace.status === 'ARCHIVED'
      ? 'bg-slate-500/10 text-slate-500 border-slate-500/20'
      : 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20'
  const hint = namespace.type === 'GLOBAL'
    ? t('namespaceStatus.immutableHint')
    : namespace.status === 'FROZEN'
      ? t('namespaceStatus.frozenHint')
      : namespace.status === 'ARCHIVED'
        ? t('namespaceStatus.archivedHint')
        : null

  return (
    <div className="flex items-start gap-4 p-6 border rounded-lg bg-card">
      {namespace.avatarUrl && (
        <img
          src={namespace.avatarUrl}
          alt={namespace.displayName}
          className="w-16 h-16 rounded-lg"
        />
      )}
      <div className="flex-1 space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-bold">{namespace.displayName}</h1>
          <NamespaceBadge type={namespace.type} name={namespace.type === 'GLOBAL' ? t('myNamespaces.typeGlobal') : t('myNamespaces.typeTeam')} />
          <span className={cn('inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium', statusClassName)}>
            {statusLabel}
          </span>
        </div>
        {namespace.description && (
          <p className="text-muted-foreground">{namespace.description}</p>
        )}
        <div className="text-sm text-muted-foreground">
          @{namespace.slug}
        </div>
        {hint ? (
          <div className="rounded-lg border border-border/50 bg-secondary/40 px-3 py-2 text-sm text-muted-foreground">
            {hint}
          </div>
        ) : null}
      </div>
    </div>
  )
}
