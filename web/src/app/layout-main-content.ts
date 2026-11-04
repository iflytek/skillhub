export const LANDING_MAIN_CLASS_NAME = 'flex-1 relative z-10'
export const SPLIT_AUTH_MAIN_CLASS_NAME = 'flex-1 relative z-10'
export const AUTH_MAIN_CLASS_NAME = 'flex-1 relative z-10 px-4 pb-4 sm:px-6 md:px-8'
export const DEFAULT_MAIN_CLASS_NAME = 'flex-1 relative z-10 px-6 py-10 md:px-12'
export const CENTERED_MAIN_CLASS_NAME = 'flex-1 relative z-10 px-4 py-8 sm:px-6 md:px-12 md:py-10'
export const CENTERED_SEARCH_CONTENT_CLASS_NAME = 'mx-auto w-full max-w-[1200px]'
export const PUBLISH_CONTENT_CLASS_NAME = 'mx-auto min-h-[calc(100vh-11rem)] w-full max-w-[1100px]'
export const CONSOLE_MAIN_CLASS_NAME = 'flex-1 relative z-10 bg-background'
export const CONSOLE_CONTENT_CLASS_NAME = 'mx-auto min-h-[calc(100vh-11rem)] w-full max-w-[1440px]'
// 224px sidebar + 80px desktop gutters + 768px readable settings form.
export const SETTINGS_CONTENT_CLASS_NAME = 'mx-auto min-h-[calc(100vh-11rem)] w-full max-w-[1072px]'
export const CONSOLE_SIDEBAR_ROW_CLASS_NAME = 'flex flex-col lg:flex-row gap-0 [&>aside]:p-4 lg:[&>aside]:px-5 lg:[&>aside]:pt-8 lg:[&>aside]:pb-6 lg:[&>aside]:border-r lg:[&>aside]:border-border/40'
export const CONSOLE_SIDEBAR_CONTENT_CLASS_NAME = 'flex-1 min-w-0 px-4 pb-7 pt-[18px] lg:px-10 lg:pt-9'
export const REVIEW_DETAIL_CONTENT_CLASS_NAME = 'mx-auto min-h-[calc(100vh-11rem)] w-full max-w-[1500px]'

export const DASHBOARD_PATH_PREFIXES = ['/dashboard', '/settings/'] as const
export const AUTH_ENTRY_PATHS = ['/login', '/register', '/reset-password'] as const
export const SPLIT_AUTH_PATHS = ['/login', '/register'] as const

interface AppMainContentLayout {
  mainClassName: string
  contentClassName: string
  sidebarRowClassName?: string
  sidebarContentClassName?: string
  pageClassName?: string
}

export function resolveAppMainContentPathname(
  pathname: string,
  resolvedPathname?: string,
): string {
  return resolvedPathname ?? pathname
}

export function getAppMainContentLayout(pathname: string): AppMainContentLayout {
  if (pathname === '/') {
    return {
      mainClassName: LANDING_MAIN_CLASS_NAME,
      contentClassName: '',
    }
  }

  if (SPLIT_AUTH_PATHS.includes(pathname as typeof SPLIT_AUTH_PATHS[number])) {
    return {
      mainClassName: SPLIT_AUTH_MAIN_CLASS_NAME,
      contentClassName: '',
    }
  }

  if (AUTH_ENTRY_PATHS.includes(pathname as typeof AUTH_ENTRY_PATHS[number])) {
    return {
      mainClassName: AUTH_MAIN_CLASS_NAME,
      contentClassName: '',
    }
  }

  if (pathname === '/search') {
    return {
      mainClassName: CENTERED_MAIN_CLASS_NAME,
      contentClassName: CENTERED_SEARCH_CONTENT_CLASS_NAME,
    }
  }

  // Publishing is a standalone flow and intentionally has no console sidebar.
  if (pathname === '/dashboard/publish') {
    return {
      mainClassName: CENTERED_MAIN_CLASS_NAME,
      contentClassName: PUBLISH_CONTENT_CLASS_NAME,
    }
  }

  if (pathname === '/dashboard' || pathname.startsWith('/dashboard/') || pathname.startsWith('/settings/')) {
    const isSettings = pathname.startsWith('/settings/')
    const isReviewDetail = /^\/dashboard\/(?:reviews\/[^/]+|namespaces\/[^/]+\/reviews\/[^/]+)$/.test(pathname)
    return {
      mainClassName: CONSOLE_MAIN_CLASS_NAME,
      contentClassName: isSettings ? SETTINGS_CONTENT_CLASS_NAME : isReviewDetail ? REVIEW_DETAIL_CONTENT_CLASS_NAME : CONSOLE_CONTENT_CLASS_NAME,
      sidebarRowClassName: CONSOLE_SIDEBAR_ROW_CLASS_NAME,
      sidebarContentClassName: CONSOLE_SIDEBAR_CONTENT_CLASS_NAME,
      ...(isSettings ? { pageClassName: 'w-full max-w-3xl' } : {}),
    }
  }

  return {
    mainClassName: DEFAULT_MAIN_CLASS_NAME,
    contentClassName: '',
  }
}
