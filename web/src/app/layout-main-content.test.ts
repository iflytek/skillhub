import { describe, expect, it } from 'vitest'
import { APP_FRAME_CLASS_NAME } from '@/shared/lib/layout-frame'
import {
  DEFAULT_MAIN_CLASS_NAME,
  DEFAULT_CONTENT_CLASS_NAME,
  CONSOLE_MAIN_CLASS_NAME,
  CONSOLE_CONTENT_CLASS_NAME,
  CONSOLE_SIDEBAR_ROW_CLASS_NAME,
  CONSOLE_SIDEBAR_CONTENT_CLASS_NAME,
  SPLIT_AUTH_MAIN_CLASS_NAME,
  getAppMainContentLayout,
  resolveAppMainContentPathname,
} from './layout-main-content'

describe('getAppMainContentLayout', () => {
  it('lets landing sections keep full-bleed backgrounds and frame their content internally', () => {
    expect(getAppMainContentLayout('/')).toEqual({
      mainClassName: 'flex-1 relative z-10',
      contentClassName: '',
    })
  })

  it.each([
    '/search', '/github', '/suites', '/suites/team/example', '/space/acme/demo',
    '/space/acme/demo/compare', '/namespaces/acme', '/notifications', '/privacy',
    '/reset-password', '/device', '/dashboard/publish',
  ])('gives %s the shared public frame without console navigation', (pathname) => {
    expect(getAppMainContentLayout(pathname)).toEqual({
      mainClassName: DEFAULT_MAIN_CLASS_NAME,
      contentClassName: DEFAULT_CONTENT_CLASS_NAME,
    })
    expect(getAppMainContentLayout(pathname).contentClassName).toContain(APP_FRAME_CLASS_NAME)
  })

  it.each([
    '/dashboard', '/dashboard/skills', '/dashboard/tokens', '/dashboard/stars',
    '/dashboard/namespaces', '/dashboard/suites', '/dashboard/reviews', '/dashboard/governance',
    '/settings/profile', '/settings/security', '/settings/notifications', '/settings/accounts',
    '/dashboard/reviews/42', '/dashboard/namespaces/team/reviews/42',
  ])('shares the same console frame and sidebar gutters on %s', (pathname) => {
    expect(getAppMainContentLayout(pathname)).toEqual({
      mainClassName: CONSOLE_MAIN_CLASS_NAME,
      contentClassName: CONSOLE_CONTENT_CLASS_NAME,
      sidebarRowClassName: CONSOLE_SIDEBAR_ROW_CLASS_NAME,
      sidebarContentClassName: CONSOLE_SIDEBAR_CONTENT_CLASS_NAME,
    })
    expect(getAppMainContentLayout(pathname).contentClassName).toContain(APP_FRAME_CLASS_NAME)
  })

  it('uses one responsive gutter instead of adding a second set around console content', () => {
    expect(APP_FRAME_CLASS_NAME).toContain('max-w-[1440px]')
    expect(APP_FRAME_CLASS_NAME).toContain('px-4 sm:px-6 lg:px-10')
    expect(CONSOLE_SIDEBAR_CONTENT_CLASS_NAME).not.toMatch(/\b(?:lg:)?px-/)
    expect(CONSOLE_SIDEBAR_CONTENT_CLASS_NAME).toContain('lg:pl-10')
  })

  it.each(['/login', '/register'])('preserves the separate split-auth composition on %s', (pathname) => {
    expect(getAppMainContentLayout(pathname)).toEqual({
      mainClassName: SPLIT_AUTH_MAIN_CLASS_NAME,
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
