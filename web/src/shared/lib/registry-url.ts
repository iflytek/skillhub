import { BASE_PATH } from './base-path'

function withoutTrailingSlash(value: string): string {
  return value === '/' ? '' : value.replace(/\/+$/, '')
}

/**
 * Resolves the public registry URL used in copied CLI and agent commands.
 * Runtime configuration wins outside local development; otherwise the current
 * browser origin must retain Vite's deployment base path.
 */
export function resolvePublicRegistryUrl(
  appBaseUrl: string | undefined,
  origin: string,
  basePath = BASE_PATH,
): string {
  const configuredUrl = appBaseUrl?.trim()
  if (configuredUrl && !configuredUrl.includes('localhost')) {
    return withoutTrailingSlash(configuredUrl)
  }

  return `${withoutTrailingSlash(origin)}${withoutTrailingSlash(basePath)}`
}
