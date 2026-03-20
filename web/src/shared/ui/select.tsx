import * as React from 'react'
import { cn } from '@/shared/lib/utils'

export const SELECT_TRIGGER_CLASS_NAME = cn(
  'flex h-11 w-full rounded-lg border border-border/60 bg-secondary/50 px-4 py-2 text-sm text-foreground',
  'ring-offset-background transition-all duration-200',
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40 focus-visible:border-primary/50',
  'disabled:cursor-not-allowed disabled:opacity-50'
)

export const SELECT_CONTENT_CLASS_NAME = cn(
  'z-50 overflow-hidden rounded-lg border border-border bg-popover text-popover-foreground shadow-md'
)

export const SELECT_ITEM_CLASS_NAME = cn(
  'relative flex w-full cursor-default select-none items-center rounded-md py-2 pl-8 pr-3 text-sm outline-none',
  'focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50'
)

export function normalizeSelectValue(value?: string | null) {
  return typeof value === 'string' && value.length > 0 ? value : undefined
}

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {}

const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, children, ...props }, ref) => {
    return (
      <select
        className={cn(SELECT_TRIGGER_CLASS_NAME, className)}
        ref={ref}
        {...props}
      >
        {children}
      </select>
    )
  }
)

Select.displayName = 'Select'

export { Select }
