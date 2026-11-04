import { APP_FRAME_CLASS_NAME } from '@/shared/lib/layout-frame'

export const LANDING_MAIN_CLASS_NAME = 'flex-1 relative z-10'
export const SPLIT_AUTH_MAIN_CLASS_NAME = 'flex-1 relative z-10'
export const DEFAULT_MAIN_CLASS_NAME = 'flex-1 relative z-10'
export const DEFAULT_CONTENT_CLASS_NAME = `${APP_FRAME_CLASS_NAME} py-8 lg:py-10`
export const CONSOLE_MAIN_CLASS_NAME = 'flex-1 relative z-10 bg-background'
export const CONSOLE_CONTENT_CLASS_NAME = `${APP_FRAME_CLASS_NAME} min-h-[calc(100vh-11rem)]`
export const CONSOLE_SIDEBAR_ROW_CLASS_NAME = 'flex flex-col lg:flex-row gap-0 [&>aside]:py-4 lg:[&>aside]:pr-5 lg:[&>aside]:pt-8 lg:[&>aside]:pb-6 lg:[&>aside]:border-r lg:[&>aside]:border-border/40'
export const CONSOLE_SIDEBAR_CONTENT_CLASS_NAME = 'flex-1 min-w-0 pb-7 pt-[18px] lg:pl-10 lg:pt-9'

export const DASHBOARD_PATH_PREFIXES = ['/dashboard', '/settings/'] as const
export const AUTH_ENTRY_PATHS = ['/login', '/register', '/reset-password'] as const
export const SPLIT_AUTH_PATHS = ['/login', '/register'] as const

interface AppMainContentLayout {
  mainClassName: string
  contentClassName: string
  sidebarRowClassName?: string
  sidebarContentClassName?: string
}

export function resolveAppMainContentPathname(
  pathname: string,
  resolvedPathname?: string,
): string {
  return resolvedPathname ?? pathname
}

export function getAppMainContentLayout(pathname: string): AppMainContentLayout {
  // Landing sections own full-bleed backgrounds and use the shared frame internally.
  if (pathname === '/') {
    return { mainClassName: LANDING_MAIN_CLASS_NAME, contentClassName: '' }
  }

  // Preserve the separate split-auth composition and its compact form.
  if (SPLIT_AUTH_PATHS.includes(pathname as typeof SPLIT_AUTH_PATHS[number])) {
    return { mainClassName: SPLIT_AUTH_MAIN_CLASS_NAME, contentClassName: '' }
  }

  if (pathname !== '/dashboard/publish' && (
    pathname === '/dashboard' || pathname.startsWith('/dashboard/') || pathname.startsWith('/settings/')
  )) {
    return {
      mainClassName: CONSOLE_MAIN_CLASS_NAME,
      contentClassName: CONSOLE_CONTENT_CLASS_NAME,
      sidebarRowClassName: CONSOLE_SIDEBAR_ROW_CLASS_NAME,
      sidebarContentClassName: CONSOLE_SIDEBAR_CONTENT_CLASS_NAME,
    }
  }

  return {
    mainClassName: DEFAULT_MAIN_CLASS_NAME,
    contentClassName: DEFAULT_CONTENT_CLASS_NAME,
  }
}
