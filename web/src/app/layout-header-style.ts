import { cn } from '@/shared/lib/utils'

export const APP_HEADER_BASE_CLASS_NAME =
  'sticky top-0 z-50 flex items-center justify-between border-b border-border/30 bg-background/70 px-4 py-2.5 backdrop-blur-xl transition-[background-color,border-color,box-shadow] duration-150 supports-[backdrop-filter]:bg-background/60 sm:px-6 md:px-12 min-h-[52px]'

export const APP_HEADER_ELEVATED_CLASS_NAME =
  'border-b border-border/30 shadow-[0_1px_2px_0_rgb(0_0_0/0.04)]'

export function getAppHeaderClassName(isElevated: boolean): string {
  return cn(APP_HEADER_BASE_CLASS_NAME, isElevated && APP_HEADER_ELEVATED_CLASS_NAME)
}
