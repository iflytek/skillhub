import { DEFAULT_REGISTRY } from '../shared/constants'

export function resolveRegistry(
  args: { registry?: string | undefined },
  env: NodeJS.ProcessEnv,
  config: { registry?: string | undefined }
): string {
  return normalizeRegistry(args.registry || env.SKILLHUB_REGISTRY || config.registry || DEFAULT_REGISTRY)
}

export function resolveToken(
  args: { token?: string | undefined },
  env: NodeJS.ProcessEnv,
  storedToken?: string | undefined
): string | undefined {
  return args.token || env.SKILLHUB_TOKEN || storedToken
}

function normalizeRegistry(registry: string): string {
  return registry.replace(/\/+$/, '')
}
