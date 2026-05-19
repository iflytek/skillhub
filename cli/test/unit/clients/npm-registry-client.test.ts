import { describe, expect, test } from 'bun:test'
import { NpmRegistryClient } from '../../../src/clients/npm-registry-client'

describe('NpmRegistryClient', () => {
  test('uses npm_config_registry when checking the latest version', async () => {
    let requestedUrl = ''
    const successfulFetch = (async (input: RequestInfo | URL) => {
      requestedUrl = String(input)
      return Response.json({ version: '1.2.3' })
    }) as typeof fetch
    const client = new NpmRegistryClient(successfulFetch, 10_000, {
      npm_config_registry: 'https://registry.npmmirror.com'
    })

    await expect(client.latestVersion()).resolves.toBe('1.2.3')
    expect(requestedUrl).toBe('https://registry.npmmirror.com/%40astron-team%2Fskillhub/latest')
  })

  test('uses SkillHub registry override before npm registry env vars', async () => {
    let requestedUrl = ''
    const successfulFetch = (async (input: RequestInfo | URL) => {
      requestedUrl = String(input)
      return Response.json({ version: '1.2.3' })
    }) as typeof fetch
    const client = new NpmRegistryClient(successfulFetch, 10_000, {
      SKILLHUB_NPM_REGISTRY: 'https://skillhub-registry.example.test/npm/',
      npm_config_registry: 'https://lower-priority.example.test',
      NPM_CONFIG_REGISTRY: 'https://lowest-priority.example.test'
    })

    await expect(client.latestVersion()).resolves.toBe('1.2.3')
    expect(requestedUrl).toBe('https://skillhub-registry.example.test/npm/%40astron-team%2Fskillhub/latest')
  })

  test('resolves registry env names case-insensitively for Windows compatibility', async () => {
    let requestedUrl = ''
    const successfulFetch = (async (input: RequestInfo | URL) => {
      requestedUrl = String(input)
      return Response.json({ version: '1.2.3' })
    }) as typeof fetch
    const client = new NpmRegistryClient(successfulFetch, 10_000, {
      skillhub_npm_registry: 'https://windows-env.example.test'
    })

    await expect(client.latestVersion()).resolves.toBe('1.2.3')
    expect(requestedUrl).toBe('https://windows-env.example.test/%40astron-team%2Fskillhub/latest')
  })

  test('ignores empty registry env values before falling back', async () => {
    let requestedUrl = ''
    const successfulFetch = (async (input: RequestInfo | URL) => {
      requestedUrl = String(input)
      return Response.json({ version: '1.2.3' })
    }) as typeof fetch
    const client = new NpmRegistryClient(successfulFetch, 10_000, {
      SKILLHUB_NPM_REGISTRY: '   ',
      npm_config_registry: '',
      NPM_CONFIG_REGISTRY: 'https://registry.example.test'
    })

    await expect(client.latestVersion()).resolves.toBe('1.2.3')
    expect(requestedUrl).toBe('https://registry.example.test/%40astron-team%2Fskillhub/latest')
  })

  test('uses the default npm registry when no registry is configured', async () => {
    let requestedUrl = ''
    const successfulFetch = (async (input: RequestInfo | URL) => {
      requestedUrl = String(input)
      return Response.json({ version: '1.2.3' })
    }) as typeof fetch
    const client = new NpmRegistryClient(successfulFetch, 10_000, {})

    await expect(client.latestVersion()).resolves.toBe('1.2.3')
    expect(requestedUrl).toBe('https://registry.npmjs.org/%40astron-team%2Fskillhub/latest')
  })

  test('classifies network failures as CLI errors', async () => {
    const failingFetch = (async () => {
      throw new TypeError('fetch failed')
    }) as unknown as typeof fetch
    const client = new NpmRegistryClient(failingFetch, 10_000, {
      NPM_CONFIG_REGISTRY: 'https://registry.example.test'
    })

    await expect(client.latestVersion()).rejects.toMatchObject({
      message: 'npm registry unreachable',
      exitCode: 3,
      details: {
        registry: 'https://registry.example.test',
        cause: 'fetch failed',
        next: 'check npm registry/proxy configuration and retry'
      }
    })
  })

  test('reports registry context for non-2xx responses', async () => {
    const failingFetch = (async () => new Response('{}', { status: 503 })) as unknown as typeof fetch
    const client = new NpmRegistryClient(failingFetch, 10_000, {
      SKILLHUB_NPM_REGISTRY: 'https://registry.example.test'
    })

    await expect(client.latestVersion()).rejects.toMatchObject({
      message: 'npm registry returned 503',
      exitCode: 3,
      details: {
        registry: 'https://registry.example.test'
      }
    })
  })

  test('rejects registry responses without a version', async () => {
    const failingFetch = (async () => Response.json({ name: '@astron-team/skillhub' })) as unknown as typeof fetch
    const client = new NpmRegistryClient(failingFetch, 10_000, {
      npm_config_registry: 'https://registry.example.test'
    })

    await expect(client.latestVersion()).rejects.toMatchObject({
      message: 'npm registry response missing version',
      exitCode: 3,
      details: {
        registry: 'https://registry.example.test'
      }
    })
  })

  test('rejects invalid JSON registry responses', async () => {
    const failingFetch = (async () => new Response('<html>not json</html>')) as unknown as typeof fetch
    const client = new NpmRegistryClient(failingFetch, 10_000, {
      npm_config_registry: 'https://registry.example.test'
    })

    await expect(client.latestVersion()).rejects.toMatchObject({
      message: 'npm registry response invalid',
      exitCode: 3,
      details: {
        registry: 'https://registry.example.test'
      }
    })
  })

  test('rejects invalid registry configuration', async () => {
    const client = new NpmRegistryClient(fetch, 10_000, {
      npm_config_registry: 'https://['
    })

    await expect(client.latestVersion()).rejects.toMatchObject({
      message: 'invalid npm registry URL',
      exitCode: 5,
      details: {
        registry: 'https://[',
        next: 'check npm registry configuration and retry'
      }
    })
  })
})
