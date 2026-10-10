/**
 * Deployment base path (Vite `base`, always ends with '/'). Router and asset
 * URLs honor it automatically; use `withBasePath` for the few full-page
 * navigations (`window.location.href`) that bypass the router.
 */
export const BASE_PATH = import.meta.env.BASE_URL

/**
 * Converts a browser-visible location to the internal path expected by
 * TanStack Router. Browser locations include the deployment base path, while
 * Router targets must not.
 */
export function toRouterPath(pathname: string, search = '', hash = '', basePath = BASE_PATH): string {
  const normalizedBasePath = basePath === '/'
    ? ''
    : basePath.endsWith('/')
      ? basePath.slice(0, -1)
      : basePath
  const routerPathname = normalizedBasePath
    && (pathname === normalizedBasePath || pathname.startsWith(`${normalizedBasePath}/`))
    ? pathname.slice(normalizedBasePath.length) || '/'
    : pathname

  return `${routerPathname}${search}${hash}`
}

/**
 * Prefixes a root-relative path with the base path. Absolute and
 * protocol-relative URLs are returned unchanged.
 */
export function withBasePath(path: string): string {
  if (!path.startsWith('/') || path.startsWith('//')) {
    return path
  }
  return `${BASE_PATH}${path.slice(1)}`
}
