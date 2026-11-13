import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { cn } from '@/shared/lib/utils'

export interface SwitchProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  checked: boolean
  onCheckedChange: (checked: boolean) => void
}

export const Switch = forwardRef<HTMLButtonElement, SwitchProps>(
  ({ checked, onCheckedChange, onClick, className, ...props }, ref) => (
    <button
      {...props}
      ref={ref}
      type="button"
      role="switch"
      aria-checked={checked}
      data-state={checked ? 'checked' : 'unchecked'}
      className={cn(
        'inline-flex h-5 w-9 shrink-0 items-center rounded-full border p-px transition-colors duration-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none',
        checked ? 'border-primary bg-primary' : 'border-muted-foreground/60 bg-muted-foreground/60',
        className,
      )}
      onClick={event => {
        onClick?.(event)
        if (!event.defaultPrevented) onCheckedChange(!checked)
      }}
    >
      <span aria-hidden className={cn('pointer-events-none block h-4 w-4 rounded-full transition-transform duration-200 motion-reduce:transition-none', checked ? 'translate-x-4 bg-primary-foreground' : 'translate-x-0 bg-background')} />
    </button>
  ),
)
Switch.displayName = 'Switch'
