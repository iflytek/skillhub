import * as SelectPrimitive from '@radix-ui/react-select'
import { cn } from '@/shared/lib/utils'

export const SELECT_TRIGGER_CLASS_NAME = cn(
  'flex h-11 w-full items-center justify-between rounded-lg border border-border bg-white px-4 py-2 text-sm text-foreground',
  'ring-offset-background transition-all duration-200',
  'placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-primary/50',
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
  return value && value.length > 0 ? value : undefined
}

export const Select = SelectPrimitive.Root
