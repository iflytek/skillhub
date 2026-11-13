import * as React from 'react'
import { cn } from '@/shared/lib/utils'

export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {}

const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, ...props }, ref) => {
    return (
      <textarea
        className={cn(
          'flex min-h-[100px] w-full rounded-[var(--control-radius)] border border-border bg-background px-4 py-3 text-[length:var(--control-font-size)] leading-[var(--control-line-height)] text-foreground transition-[border-color,background-color] duration-150 placeholder:text-[hsl(var(--text-placeholder))] focus:[outline:none] focus:border-ring aria-[invalid=true]:border-destructive aria-[invalid=true]:focus:border-destructive disabled:cursor-not-allowed disabled:opacity-50 resize-y',
          className
        )}
        ref={ref}
        {...props}
      />
    )
  }
)
Textarea.displayName = 'Textarea'

export { Textarea }
