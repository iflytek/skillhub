import { describe, expect, it } from 'vitest'
import {
  AUTH_MAIN_CLASS_NAME,
  PUBLISH_CONTENT_CLASS_NAME,
  CENTERED_MAIN_CLASS_NAME,
  CENTERED_SEARCH_CONTENT_CLASS_NAME,
  DEFAULT_MAIN_CLASS_NAME,
  CONSOLE_MAIN_CLASS_NAME,
  CONSOLE_CONTENT_CLASS_NAME,
  SETTINGS_CONTENT_CLASS_NAME,
  CONSOLE_SIDEBAR_ROW_CLASS_NAME,
  CONSOLE_SIDEBAR_CONTENT_CLASS_NAME,
  SPLIT_AUTH_MAIN_CLASS_NAME,
  getAppMainContentLayout,
  resolveAppMainContentPathname,
} from './layout-main-content'

describe('getAppMainContentLayout', () => {
  it('keeps the landing page full width without the app-shell padding wrapper', () => {
    expect(getAppMainContentLayout('/')).toEqual({
      mainClassName: 'flex-1 relative z-10',
      contentClassName: '',
    })
  })

  it('centers the search page content with roomier responsive gutters', () => {
    const layout = getAppMainContentLayout('/search')

    expect(layout).toEqual({
      mainClassName: CENTERED_MAIN_CLASS_NAME,
      contentClassName: CENTERED_SEARCH_CONTENT_CLASS_NAME,
    })
    expect(layout.contentClassName).toContain('max-w-[1200px]')
  })

  it.each(['/dashboard', '/dashboard/skills', '/dashboard/tokens', '/dashboard/stars', '/dashboard/namespaces', '/dashboard/suites', '/dashboard/reviews', '/dashboard/governance'])('shares the console frame and sidebar gutters on %s', (pathname) => {
    expect(getAppMainContentLayout(pathname)).toEqual({
      mainClassName: CONSOLE_MAIN_CLASS_NAME,
      contentClassName: CONSOLE_CONTENT_CLASS_NAME,
      sidebarRowClassName: CONSOLE_SIDEBAR_ROW_CLASS_NAME,
      sidebarContentClassName: CONSOLE_SIDEBAR_CONTENT_CLASS_NAME,
    })
  })

  it.each(['/settings/profile', '/settings/security', '/settings/notifications', '/settings/accounts'])('centers %s inside a compact sidebar-and-form frame', (pathname) => {
    expect(getAppMainContentLayout(pathname)).toEqual({
      mainClassName: CONSOLE_MAIN_CLASS_NAME,
      contentClassName: SETTINGS_CONTENT_CLASS_NAME,
      sidebarRowClassName: CONSOLE_SIDEBAR_ROW_CLASS_NAME,
      sidebarContentClassName: CONSOLE_SIDEBAR_CONTENT_CLASS_NAME,
      pageClassName: 'w-full max-w-3xl',
    })
  })

  it('fits the settings sidebar, desktop gutters and readable form without trailing content space', () => {
    expect(SETTINGS_CONTENT_CLASS_NAME).toContain('mx-auto')
    expect(SETTINGS_CONTENT_CLASS_NAME).toContain('max-w-[1072px]')
    expect(CONSOLE_CONTENT_CLASS_NAME).toContain('max-w-[1440px]')
  })

  it('keeps publishing a standalone flow without console navigation', () => {
    expect(getAppMainContentLayout('/dashboard/publish')).toEqual({
      mainClassName: CENTERED_MAIN_CLASS_NAME,
      contentClassName: PUBLISH_CONTENT_CLASS_NAME,
    })
  })

  it.each(['/dashboard/reviews/42', '/dashboard/namespaces/team/reviews/42'])('retains a wider console frame for %s', (pathname) => {
    expect(getAppMainContentLayout(pathname).contentClassName).toContain('max-w-[1500px]')
    expect(getAppMainContentLayout(pathname).sidebarRowClassName).toBe(CONSOLE_SIDEBAR_ROW_CLASS_NAME)
  })

  it('uses the full-bleed split layout for login and registration', () => {
    for (const pathname of ['/login', '/register']) {
      expect(getAppMainContentLayout(pathname)).toEqual({
        mainClassName: SPLIT_AUTH_MAIN_CLASS_NAME,
        contentClassName: '',
      })
    }
  })

  it('keeps the compact entry layout for password reset', () => {
    for (const pathname of ['/reset-password']) {
      expect(getAppMainContentLayout(pathname)).toEqual({
        mainClassName: AUTH_MAIN_CLASS_NAME,
        contentClassName: '',
      })
    }
  })

  it('leaves other non-landing routes on the default full-width app content layout', () => {
    expect(getAppMainContentLayout('/space/acme/demo')).toEqual({
      mainClassName: DEFAULT_MAIN_CLASS_NAME,
      contentClassName: '',
    })
  })
})

describe('resolveAppMainContentPathname', () => {
  it('sticks with the last resolved route while the next page is still pending', () => {
    expect(resolveAppMainContentPathname('/dashboard', '/search')).toBe('/search')
  })

  it('falls back to the live location when no resolved route is available yet', () => {
    expect(resolveAppMainContentPathname('/search')).toBe('/search')
  })
})
