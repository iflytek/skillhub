const BASE_PATH_PATTERN = /^\/[A-Za-z0-9._~/-]+\/$/

// First path segments reserved by the SkillHub server's own Nginx locations
// (see web/nginx.conf.template). A base path starting with any of these would
// generate a `location ^~ /<seg>/` that shadows the server route and break the
// app. Kept in sync with the runtime, release-config, and Helm checks.
const RESERVED_FIRST_SEGMENTS = new Set([
  'api',
  'oauth2',
  'login',
  'assets',
  'registry',
  'nginx-health',
  '.well-known',
  'runtime-config.js',
])

/**
 * Validates the Vite base path before it is embedded into generated asset URLs.
 * Only same-origin, normalized URL paths are allowed.
 */
export function validateBasePath(value: string): string {
  if (value === '/') {
    return value
  }

  const segments = value.split('/').filter(Boolean)
  const hasDotSegment = segments.some((segment) => segment === '.' || segment === '..')
  if (
    !BASE_PATH_PATTERN.test(value)
    || value.includes('//')
    || hasDotSegment
  ) {
    throw new Error(
      `VITE_BASE_PATH must be '/' or a normalized root-relative path ending with '/': ${value}`,
    )
  }

  if (RESERVED_FIRST_SEGMENTS.has(segments[0])) {
    throw new Error(
      `VITE_BASE_PATH must not start with a segment reserved by the SkillHub server (${segments[0]}); it would shadow the server's own Nginx location: ${value}`,
    )
  }

  return value
}
