import { describe, expect, it, vi } from 'vitest'
import { isRedirect } from '@tanstack/react-router'
import { buildReturnTo, createRedirectAuthenticated, createRequireAuth, resolveAuthReturnTo } from './auth-route'

describe('auth-route', () => {
  it('returns to the original local page or the home page, never an external URL', () => {
    expect(resolveAuthReturnTo('/skills?tab=mine#latest')).toBe('/skills?tab=mine#latest')
    expect(resolveAuthReturnTo(undefined)).toBe('/')
    expect(resolveAuthReturnTo('//example.com')).toBe('/')
    expect(resolveAuthReturnTo('/\\example.com')).toBe('/')
    expect(resolveAuthReturnTo('/\n/evil.example')).toBe('/')
    expect(resolveAuthReturnTo('/\t/evil.example')).toBe('/')
    expect(resolveAuthReturnTo('/\r/evil.example')).toBe('/')
    expect(resolveAuthReturnTo('/\u007fevil.example')).toBe('/')
    expect(resolveAuthReturnTo('https://example.com')).toBe('/')
  })

  it('buildReturnTo preserves pathname search and hash', () => {
    expect(buildReturnTo({
      pathname: '/space/global/caldav-calendar',
      searchStr: '?tab=files',
      hash: '#readme',
    })).toBe('/space/global/caldav-calendar?tab=files#readme')
  })

  it('createRequireAuth redirects unauthenticated users to login with returnTo', async () => {
    const requireAuth = createRequireAuth(async () => null)

    await expect(requireAuth({
      location: {
        pathname: '/space/global/caldav-calendar',
        searchStr: '?tab=files',
        hash: '#readme',
      },
    })).rejects.toSatisfy((error: unknown) => {
      expect(isRedirect(error)).toBe(true)
      if (!isRedirect(error)) {
        return false
      }
      expect(error.options.to).toBe('/login')
      expect(error.options.search).toEqual({
        returnTo: '/space/global/caldav-calendar?tab=files#readme',
      })
      return true
    })
  })

  it('createRequireAuth returns the current user when authenticated', async () => {
    const user = { userId: 'user-1' }
    const getCurrentUser = vi.fn(async () => user)
    const requireAuth = createRequireAuth(getCurrentUser)

    await expect(requireAuth({
      location: { pathname: '/dashboard' },
    })).resolves.toEqual({ user })
    expect(getCurrentUser).toHaveBeenCalledTimes(1)
  })

  it('keeps unauthenticated visitors on the login page', async () => {
    const redirectAuthenticated = createRedirectAuthenticated(async () => null)

    await expect(redirectAuthenticated({ search: { returnTo: '/dashboard/tokens' } })).resolves.toBeUndefined()
  })

  it('keeps the login page available when the session status check fails', async () => {
    const redirectAuthenticated = createRedirectAuthenticated(async () => {
      throw new Error('Session status unavailable')
    })

    await expect(redirectAuthenticated({ search: { returnTo: '/dashboard/tokens' } })).resolves.toBeUndefined()
  })

  it('redirects authenticated visitors to the requested local page', async () => {
    const redirectAuthenticated = createRedirectAuthenticated(async () => ({ userId: 'user-1' }))

    await expect(redirectAuthenticated({ search: { returnTo: '/dashboard/tokens?tab=active#latest' } })).rejects.toSatisfy((error: unknown) => {
      expect(isRedirect(error)).toBe(true)
      if (!isRedirect(error)) return false
      expect(error.options.to).toBe('/dashboard/tokens?tab=active#latest')
      expect(error.options.replace).toBe(true)
      return true
    })
  })

  it.each([undefined, '//example.com', '/login', '/login?returnTo=%2Flogin', '/register'])('redirects authenticated visitors to home for unusable destinations (%s)', async (returnTo) => {
    const redirectAuthenticated = createRedirectAuthenticated(async () => ({ userId: 'user-1' }))

    await expect(redirectAuthenticated({ search: { returnTo } })).rejects.toSatisfy((error: unknown) => {
      expect(isRedirect(error)).toBe(true)
      if (!isRedirect(error)) return false
      expect(error.options.to).toBe('/')
      return true
    })
  })
})
