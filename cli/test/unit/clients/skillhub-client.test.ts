import { describe, expect, test } from 'bun:test'
import { SkillHubClient } from '../../../src/clients/skillhub-client'
import { CliError } from '../../../src/shared/errors'
import { EXIT } from '../../../src/shared/constants'

describe('SkillHubClient', () => {
  test('uses the provided multipart file name when publishing', async () => {
    const fetchImpl = (async (_input: URL | RequestInfo, init?: RequestInit) => {
      const formData = init?.body as FormData
      const file = formData.get('file') as File
      expect(file.name).toBe('custom-skill.zip')
      expect(formData.get('visibility')).toBe('PRIVATE')
      return Response.json({
        data: {
          namespace: 'team',
          slug: 'custom-skill',
          version: '1.0.0',
          visibility: 'PRIVATE'
        }
      })
    }) as unknown as typeof fetch

    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    await expect(client.publish('team', new Blob(['zip'], { type: 'application/zip' }), 'PRIVATE', 'custom-skill.zip'))
      .resolves.toMatchObject({ slug: 'custom-skill' })
  })

  // --- download() error handling (P0) ---

  test('download() throws auth error on 401', async () => {
    const fetchImpl = (async () => new Response(null, { status: 401 })) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    const err = expect(client.download('ns', 'slug')).rejects
    await err.toBeInstanceOf(CliError)
    await err.toHaveProperty('message', 'authentication failed')
    await err.toHaveProperty('exitCode', EXIT.auth)
  })

  test('download() throws auth error on 403', async () => {
    const fetchImpl = (async () => new Response(null, { status: 403 })) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    const err = expect(client.download('ns', 'slug')).rejects
    await err.toBeInstanceOf(CliError)
    await err.toHaveProperty('message', 'authentication failed')
    await err.toHaveProperty('exitCode', EXIT.auth)
  })

  test('download() throws not-found error on 404', async () => {
    const fetchImpl = (async () => new Response(null, { status: 404 })) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    const err = expect(client.download('ns', 'slug')).rejects
    await err.toBeInstanceOf(CliError)
    await err.toHaveProperty('message', 'skill or version not found')
    await err.toHaveProperty('exitCode', EXIT.generic)
  })

  test('download() throws generic error on 400', async () => {
    const fetchImpl = (async () => new Response(null, { status: 400 })) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    const err = expect(client.download('ns', 'slug')).rejects
    await err.toBeInstanceOf(CliError)
    await err.toHaveProperty('message', 'download failed with status 400')
    await err.toHaveProperty('exitCode', EXIT.generic)
  })

  test('download() throws generic error on 500', async () => {
    const fetchImpl = (async () => new Response(null, { status: 500 })) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    const err = expect(client.download('ns', 'slug')).rejects
    await err.toBeInstanceOf(CliError)
    await err.toHaveProperty('message', 'download failed with status 500')
    await err.toHaveProperty('exitCode', EXIT.generic)
  })

  test('download() throws network error on fetch failure', async () => {
    const fetchImpl = (async () => { throw new TypeError('fetch failed') }) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    const err = expect(client.download('ns', 'slug')).rejects
    await err.toBeInstanceOf(CliError)
    await err.toHaveProperty('message', 'registry unreachable')
    await err.toHaveProperty('exitCode', EXIT.network)
  })

  // --- whoami() (P1) ---

  test('whoami() returns user data', async () => {
    const fetchImpl = (async () => Response.json({
      data: { handle: 'alice', displayName: 'Alice', email: 'a@b.com' }
    })) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    const result = await client.whoami()
    expect(result).toEqual({ handle: 'alice', displayName: 'Alice', email: 'a@b.com' })
  })

  test('whoami() throws on 401', async () => {
    const fetchImpl = (async () => new Response(null, { status: 401 })) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    const err = expect(client.whoami()).rejects
    await err.toBeInstanceOf(CliError)
    await err.toHaveProperty('message', 'authentication failed')
    await err.toHaveProperty('exitCode', EXIT.auth)
  })

  // --- search() (P1) ---

  test('search() returns items', async () => {
    const fetchImpl = (async () => Response.json({
      data: {
        items: [{ namespace: 'g', slug: 's', latestVersion: '1.0', summary: 'x' }],
        total: 1,
        limit: 20
      }
    })) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    const result = await client.search('test', 20)
    expect(result.items).toHaveLength(1)
    expect(result.items[0]).toEqual({ namespace: 'g', slug: 's', latestVersion: '1.0', summary: 'x' })
    expect(result.total).toBe(1)
    expect(result.limit).toBe(20)
  })

  test('search() returns empty results', async () => {
    const fetchImpl = (async () => Response.json({
      data: { items: [], total: 0, limit: 20 }
    })) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    const result = await client.search('nothing', 20)
    expect(result.items).toHaveLength(0)
    expect(result.total).toBe(0)
  })

  // --- resolve() (P1) ---

  test('resolve() without version omits query param', async () => {
    let capturedUrl = ''
    const fetchImpl = (async (input: URL | RequestInfo) => {
      capturedUrl = String(input)
      return Response.json({
        data: { namespace: 'ns', slug: 'sk', version: '1.0.0', versionId: 1, fingerprint: 'abc', downloadUrl: '/dl' }
      })
    }) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    await client.resolve('ns', 'sk')
    expect(capturedUrl).not.toContain('?version=')
  })

  test('resolve() with version includes query param', async () => {
    let capturedUrl = ''
    const fetchImpl = (async (input: URL | RequestInfo) => {
      capturedUrl = String(input)
      return Response.json({
        data: { namespace: 'ns', slug: 'sk', version: '2.0.0', versionId: 2, fingerprint: 'def', downloadUrl: '/dl' }
      })
    }) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    await client.resolve('ns', 'sk', '2.0.0')
    expect(capturedUrl).toContain('?version=2.0.0')
  })

  // --- handleJsonResponse() non-2xx classification ---

  test('whoami() throws generic error on 500', async () => {
    const fetchImpl = (async () => new Response(null, { status: 500 })) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    const err = expect(client.whoami()).rejects
    await err.toBeInstanceOf(CliError)
    await err.toHaveProperty('exitCode', EXIT.generic)
  })

  test('search() throws network error on 502', async () => {
    const fetchImpl = (async () => new Response(null, { status: 502 })) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    const err = expect(client.search('test', 20)).rejects
    await err.toBeInstanceOf(CliError)
    await err.toHaveProperty('exitCode', EXIT.network)
  })

  // --- deleteRemote() (P1) ---

  test('deleteRemote() returns result on success', async () => {
    const fetchImpl = (async () => Response.json({
      data: { ok: true, scope: 'remote', action: 'delete', namespace: 'global', slug: 'demo' }
    })) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    const result = await client.deleteRemote('global', 'demo')
    expect(result).toEqual({ ok: true, scope: 'remote', action: 'delete', namespace: 'global', slug: 'demo' })
  })

  test('deleteRemote() throws on network error', async () => {
    const fetchImpl = (async () => { throw new TypeError('fetch failed') }) as unknown as typeof fetch
    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    const err = expect(client.deleteRemote('global', 'demo')).rejects
    await err.toBeInstanceOf(CliError)
    await err.toHaveProperty('message', 'registry unreachable')
    await err.toHaveProperty('exitCode', EXIT.network)
  })
})
