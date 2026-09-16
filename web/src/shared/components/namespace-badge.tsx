import { cn } from '@/shared/lib/utils'

interface NamespaceBadgeProps {
  type: 'GLOBAL' | 'TEAM'
  name: string
  className?: string
  title?: string
}

export function NamespaceBadge({ type, name, className, title }: NamespaceBadgeProps) {
  return (
    <span
      title={title}
      className={cn(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium border transition-colors',
        type === 'GLOBAL'
          ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/20'
          : 'bg-blue-500/10 text-blue-700 dark:text-blue-400 border-blue-500/20',
        className
      )}
    >
      {name}
    </span>
  )
}
