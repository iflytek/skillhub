import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/shared/lib/utils'

const buttonVariants = cva(
  // 基础：紧致圆角、精准属性过渡、按下回弹、聚焦双层光晕
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-[transform,background-color,box-shadow,border-color] duration-150 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/40 focus-visible:ring-offset-1 focus-visible:ring-offset-background disabled:pointer-events-none disabled:opacity-50 active:scale-[0.97] select-none',
  {
    variants: {
      variant: {
        // default：三层阴影（inset 顶高光 + 底部投影 + 1px 描边），模拟立体片状按钮
        default:
          'bg-primary text-primary-foreground shadow-[inset_0_1px_0_0_hsl(0_0%_100%/0.10),0_1px_2px_0_rgb(0_0_0/0.10),0_0_0_1px_rgb(0_0_0/0.04)] hover:bg-primary/92 hover:shadow-[inset_0_1px_0_0_hsl(0_0%_100%/0.14),0_2px_4px_-1px_rgb(0_0_0/0.14),0_0_0_1px_rgb(0_0_0/0.06)] active:shadow-[inset_0_1px_2px_0_rgb(0_0_0/0.14)]',
        destructive:
          'bg-destructive text-destructive-foreground shadow-[inset_0_1px_0_0_hsl(0_0%_100%/0.12),0_1px_2px_0_rgb(0_0_0/0.10)] hover:bg-destructive/92 hover:shadow-[inset_0_1px_0_0_hsl(0_0%_100%/0.16),0_2px_4px_-1px_rgb(0_0_0/0.14)]',
        outline:
          'border border-border/60 bg-background/70 text-foreground shadow-[0_1px_1px_0_rgb(0_0_0/0.04)] hover:bg-accent/80 hover:border-border hover:text-accent-foreground hover:shadow-[0_1px_2px_0_rgb(0_0_0/0.06)]',
        secondary:
          'bg-secondary text-secondary-foreground shadow-[inset_0_1px_0_0_hsl(0_0%_100%/0.04),0_1px_1px_0_rgb(0_0_0/0.04)] hover:bg-secondary/80 hover:shadow-[inset_0_1px_0_0_hsl(0_0%_100%/0.06),0_1px_2px_0_rgb(0_0_0/0.06)]',
        ghost:
          'hover:bg-accent/70 hover:text-accent-foreground',
        link:
          'text-primary underline-offset-4 hover:underline active:scale-100',
      },
      size: {
        default: 'h-9 px-4 py-2',
        sm: 'h-8 rounded-sm px-3 text-xs',
        lg: 'h-10 rounded-lg px-8',
        icon: 'h-9 w-9',
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
