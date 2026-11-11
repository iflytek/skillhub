import * as React from 'react'
import { cn } from '@/shared/lib/utils'

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {}

export const INPUT_BASE_CLASS_NAME =
  'flex h-[var(--control-height)] w-full rounded-[var(--control-radius)] border border-border bg-background px-[var(--control-padding-x)] py-[var(--control-padding-y)] text-[length:var(--control-font-size)] leading-[var(--control-line-height)] text-foreground transition-[border-color,background-color] duration-150 ease-out file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-[hsl(var(--text-placeholder))] hover:border-border focus:[outline:none] focus:border-ring focus:bg-background aria-[invalid=true]:border-destructive aria-[invalid=true]:focus:border-destructive disabled:cursor-not-allowed disabled:opacity-50'

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, style, ...props }, ref) => {
    return (
      <input
        type={type}
        className={cn(
          INPUT_BASE_CLASS_NAME,
          className
        )}
        style={{ ...style }}
        ref={ref}
        {...props}
      />
    )
  }
)
Input.displayName = 'Input'

export { Input }
