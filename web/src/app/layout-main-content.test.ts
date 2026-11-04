import { describe, expect, it } from 'vitest'
import {
  AUTH_MAIN_CLASS_NAME,
  CENTERED_DASHBOARD_CONTENT_CLASS_NAME,
  CENTERED_MAIN_CLASS_NAME,
  CENTERED_SEARCH_CONTENT_CLASS_NAME,
  DEFAULT_MAIN_CLASS_NAME,
  MY_SKILLS_MAIN_CLASS_NAME,
  MY_SKILLS_CONTENT_CLASS_NAME,
  MY_SKILLS_SIDEBAR_ROW_CLASS_NAME,
  MY_SKILLS_SIDEBAR_CONTENT_CLASS_NAME,
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

  it.each(['/dashboard/tokens', '/dashboard/stars', '/settings/profile', '/dashboard/skills/unrelated'])('keeps %s in the existing dashboard frame', (pathname) => {
    const layout = getAppMainContentLayout(pathname)

    expect(layout).toEqual({
      mainClassName: CENTERED_MAIN_CLASS_NAME,
      contentClassName: CENTERED_DASHBOARD_CONTENT_CLASS_NAME,
    })
    expect(layout.contentClassName).toContain('max-w-[1100px]')
  })

  it('isolates the prototype-width frame and sidebar gutters to My Skills', () => {
    expect(getAppMainContentLayout('/dashboard/skills')).toEqual({
      mainClassName: MY_SKILLS_MAIN_CLASS_NAME,
      contentClassName: MY_SKILLS_CONTENT_CLASS_NAME,
      sidebarRowClassName: MY_SKILLS_SIDEBAR_ROW_CLASS_NAME,
      sidebarContentClassName: MY_SKILLS_SIDEBAR_CONTENT_CLASS_NAME,
    })
    expect(MY_SKILLS_CONTENT_CLASS_NAME).toContain('max-w-[1440px]')
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
