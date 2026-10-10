import { redirect } from '@tanstack/react-router'

export type RouteLocationLike = {
  pathname: string
  searchStr?: string
  hash?: string
}

export function buildReturnTo(location: RouteLocationLike) {
  return `${location.pathname}${location.searchStr ?? ''}${location.hash ?? ''}`
}

export function isSafeAuthReturnTo(value: unknown): value is string {
  return typeof value === 'string'
    && value.startsWith('/')
    && !value.startsWith('//')
    && !value.includes('\\')
    && !Array.from(value).some((character) => {
      const code = character.charCodeAt(0)
      return code < 32 || code === 127
    })
}

export function resolveAuthReturnTo(value: unknown) {
  return isSafeAuthReturnTo(value) ? value : '/'
}

function resolveAuthenticatedReturnTo(value: unknown) {
  const target = resolveAuthReturnTo(value)
  try {
    const pathname = decodeURIComponent(new URL(target, 'http://localhost').pathname)
    if (/^\/(?:login|register)\/?$/i.test(pathname) || pathname.startsWith('//') || pathname.includes('\\')) {
      return '/'
    }
  } catch {
    return '/'
  }
  return target
}

export function createRedirectAuthenticated(getCurrentUser: () => Promise<unknown>) {
  return async function redirectAuthenticated({ search }: { search: { returnTo?: string } }) {
    let user: unknown
    try {
      user = await getCurrentUser()
    } catch {
      // A failed status check must not make the login entry unavailable.
      return
    }
    if (user) {
      throw redirect({ to: resolveAuthenticatedReturnTo(search.returnTo), replace: true })
    }
  }
}

export function createRequireAuth(getCurrentUser: () => Promise<unknown>) {
  return async function requireAuth({ location }: { location: RouteLocationLike }) {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({
        to: '/login',
        search: { returnTo: buildReturnTo(location) },
      })
    }
    return { user }
  }
}
