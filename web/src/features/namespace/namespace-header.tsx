import { useTranslation } from 'react-i18next'
import type { Namespace } from '@/api/types'
import { NamespaceBadge } from '@/shared/components/namespace-badge'

interface NamespaceHeaderProps {
  namespace: Namespace
}

export function NamespaceHeader({ namespace }: NamespaceHeaderProps) {
  const { t } = useTranslation()
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
        <div className="flex items-center gap-2">
          <h1 className="text-2xl font-bold">{namespace.displayName}</h1>
          <NamespaceBadge type={namespace.type} name={namespace.type === 'GLOBAL' ? t('myNamespaces.typeGlobal') : t('myNamespaces.typeTeam')} />
        </div>
        {namespace.description && (
          <p className="text-muted-foreground">{namespace.description}</p>
        )}
        <div className="text-sm text-muted-foreground">
          @{namespace.slug}
        </div>
      </div>
    </div>
  )
}
