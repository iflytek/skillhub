import { cn } from '@/shared/lib/utils'
import { APP_FRAME_CLASS_NAME } from '@/shared/lib/layout-frame'

export const APP_HEADER_BASE_CLASS_NAME =
  'sticky top-0 z-50 border-b border-border/30 bg-background/70 backdrop-blur-xl transition-[background-color,border-color,box-shadow] duration-150 supports-[backdrop-filter]:bg-background/60'

export const APP_HEADER_CONTENT_CLASS_NAME = `${APP_FRAME_CLASS_NAME} flex min-h-[56px] items-center justify-between py-2.5`

export const APP_HEADER_ELEVATED_CLASS_NAME =
  'border-b border-border/30 shadow-[0_1px_2px_0_rgb(0_0_0/0.04)]'

export function getAppHeaderClassName(isElevated: boolean): string {
  return cn(APP_HEADER_BASE_CLASS_NAME, isElevated && APP_HEADER_ELEVATED_CLASS_NAME)
}
