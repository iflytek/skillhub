import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/shared/lib/utils'

const buttonVariants = cva(
  // Size-specific controls and a single keyboard outline; preserve native button behavior.
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-[var(--control-radius)] text-[length:var(--control-font-size)] leading-[var(--control-line-height)] font-medium transition-[background-color,border-color] duration-150 ease-out focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:pointer-events-none disabled:opacity-50 active:scale-[0.97] motion-reduce:transform-none select-none',
  {
    variants: {
      variant: {
        // Keep variant names stable while adopting the neutral target palette.
        default:
          'bg-primary text-primary-foreground hover:bg-primary/90',
        destructive:
          'bg-destructive text-destructive-foreground hover:bg-destructive/90',
        outline:
          'border border-border bg-background text-foreground hover:bg-accent hover:text-accent-foreground',
        secondary:
          'bg-secondary text-secondary-foreground hover:bg-secondary/80',
        soft:
          'border border-primary/15 bg-primary/5 text-primary shadow-none hover:bg-primary/10',
        ghost:
          'hover:bg-accent/70 hover:text-accent-foreground',
        link:
          'text-primary underline-offset-4 hover:underline active:scale-100',
      },
      size: {
        default: 'h-[var(--control-height)] px-[var(--control-padding-x)] py-0',
        xs: 'h-[var(--control-xs-height)] px-2 py-0 text-xs leading-4',
        sm: 'h-[var(--control-sm-height)] px-2.5 py-0',
        lg: 'h-[var(--control-lg-height)] rounded-[var(--control-lg-radius)] px-3 py-0 text-sm leading-[22px]',
        icon: 'h-[var(--control-icon-size)] w-[var(--control-icon-size)]',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  }
)

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, style, ...props }, ref) => {
    return (
      <button
        className={cn(buttonVariants({ variant, size, className }))}
        style={style}
        ref={ref}
        {...props}
      />
    )
  }
)
Button.displayName = 'Button'

export { Button, buttonVariants }
