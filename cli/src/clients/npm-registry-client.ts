import { EXIT, CLI_PACKAGE_NAME } from '../shared/constants'
import { CliError } from '../shared/errors'

const DEFAULT_NPM_REGISTRY = 'https://registry.npmjs.org'

function readEnv(env: NodeJS.ProcessEnv, name: string): string | undefined {
  const exactValue = env[name]?.trim()
  if (exactValue) {
    return exactValue
  }

  const lowerName = name.toLowerCase()
  for (const [key, value] of Object.entries(env)) {
    const normalizedValue = value?.trim()
    if (key.toLowerCase() === lowerName && normalizedValue) {
      return normalizedValue
    }
  }
  return undefined
}

function resolveRegistry(env: NodeJS.ProcessEnv): string {
  return readEnv(env, 'SKILLHUB_NPM_REGISTRY')
    ?? readEnv(env, 'npm_config_registry')
    ?? readEnv(env, 'NPM_CONFIG_REGISTRY')
    ?? DEFAULT_NPM_REGISTRY
}

function buildLatestUrl(registry: string, packageName: string): string {
  try {
    const base = registry.endsWith('/') ? registry : `${registry}/`
    return new URL(`${encodeURIComponent(packageName)}/latest`, base).toString()
  } catch {
    throw new CliError('invalid npm registry URL', EXIT.usage, {
      registry,
      next: 'check npm registry configuration and retry'
    })
  }
}

export class NpmRegistryClient {
  constructor(
    private readonly fetchImpl: typeof fetch = fetch,
    private readonly timeoutMs = 10_000,
    private readonly env: NodeJS.ProcessEnv = process.env
  ) {}

  async latestVersion(packageName = CLI_PACKAGE_NAME): Promise<string> {
    const registry = resolveRegistry(this.env)
    const url = buildLatestUrl(registry, packageName)
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), this.timeoutMs)
    try {
      let response: Response
      try {
        response = await this.fetchImpl(url, {
          signal: controller.signal
        })
      } catch (error) {
        const isTimeout = error instanceof Error && error.name === 'AbortError'
        throw new CliError('npm registry unreachable', EXIT.network, {
          registry,
          cause: error instanceof Error ? error.message : String(error),
          next: isTimeout
            ? 'check npm registry connectivity or proxy settings and retry'
            : 'check npm registry/proxy configuration and retry'
        })
      }
      if (!response.ok) {
        throw new CliError(`npm registry returned ${response.status}`, EXIT.network, { registry })
      }
      let body: unknown
      try {
        body = await response.json()
      } catch (error) {
        throw new CliError('npm registry response invalid', EXIT.network, {
          registry,
          cause: error instanceof Error ? error.message : String(error)
        })
      }
      if (typeof body !== 'object' || body === null || !('version' in body) || typeof body.version !== 'string') {
        throw new CliError('npm registry response missing version', EXIT.network, { registry })
      }
      return body.version
    } finally {
      clearTimeout(timer)
    }
  }
}
