import { describe, expect, it } from 'vitest'
import {
  buildLoginRedirect,
  canAccessRoute,
  shouldNavigateBackOnForbidden,
  shouldRedirectToLogin,
} from './role-guard'

describe('canAccessRoute', () => {
  it('returns true when the user has one of the required roles', () => {
    expect(canAccessRoute(['USER', 'SKILL_ADMIN'], ['SKILL_ADMIN', 'SUPER_ADMIN'])).toBe(true)
  })

  it('returns false when the user does not have any required roles', () => {
    expect(canAccessRoute(['USER'], ['SKILL_ADMIN', 'SUPER_ADMIN'])).toBe(false)
  })
})

describe('shouldNavigateBackOnForbidden', () => {
  it('returns true when there is browser history to go back to', () => {
    expect(shouldNavigateBackOnForbidden(2)).toBe(true)
  })

  it('returns false when the current page is the first history entry', () => {
    expect(shouldNavigateBackOnForbidden(1)).toBe(false)
  })
})

describe('shouldRedirectToLogin', () => {
  it('returns true when auth has resolved without a signed-in user', () => {
    expect(shouldRedirectToLogin(false, null)).toBe(true)
  })

  it('returns false while auth is still loading', () => {
    expect(shouldRedirectToLogin(true, null)).toBe(false)
  })

  it('returns false when a signed-in user exists', () => {
    expect(shouldRedirectToLogin(false, { id: 'u-1' })).toBe(false)
  })
})

describe('buildLoginRedirect', () => {
  it('preserves the full current URL in returnTo', () => {
    expect(buildLoginRedirect('/dashboard/reviews/13', '?tab=pending', '#panel')).toEqual({
      to: '/login',
      search: {
        returnTo: '/dashboard/reviews/13?tab=pending#panel',
      },
    })
  })
})
