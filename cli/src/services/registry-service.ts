import { DEFAULT_REGISTRY } from '../shared/constants'

export function resolveRegistry(
  args: { registry?: string | undefined },
  env: NodeJS.ProcessEnv,
  config: { registry?: string | undefined }
): string {
  return normalizeRegistry(args.registry || env.SKILLHUB_REGISTRY || config.registry || DEFAULT_REGISTRY)
}

export type TokenSource = 'argument' | 'environment' | 'stored'

export interface ResolvedToken {
  token: string
  source: TokenSource
}

export function resolveTokenWithSource(
  args: { token?: string | undefined },
  env: NodeJS.ProcessEnv,
  storedToken?: string | undefined
): ResolvedToken | undefined {
  if (args.token) return { token: args.token, source: 'argument' }
  if (env.SKILLHUB_TOKEN) return { token: env.SKILLHUB_TOKEN, source: 'environment' }
  if (storedToken) return { token: storedToken, source: 'stored' }
  return undefined
}

export function resolveToken(
  args: { token?: string | undefined },
  env: NodeJS.ProcessEnv,
  storedToken?: string | undefined
): string | undefined {
  return resolveTokenWithSource(args, env, storedToken)?.token
}

function normalizeRegistry(registry: string): string {
  return registry.replace(/\/+$/, '')
}
