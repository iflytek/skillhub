import { cn } from '@/shared/lib/utils'

interface NamespaceBadgeProps {
  type: 'GLOBAL' | 'TEAM'
  name: string
  className?: string
}

export function NamespaceBadge({ type, name, className }: NamespaceBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-3 py-1 text-xs font-medium border transition-colors',
        type === 'GLOBAL'
          ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20 hover:bg-emerald-500/15'
          : 'bg-accent/10 text-accent border-accent/20 hover:bg-accent/15',
        className
      )}
    >
      {name}
    </span>
  )
}
