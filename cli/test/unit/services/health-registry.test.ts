import { afterEach, describe, expect, test } from 'bun:test'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { createTempHome } from '../../helpers/temp-env'
import { runHealth } from '../../../src/services/health-service'

const homes: string[] = []
const registry = 'https://registry.example.com'
const token = 'sk_health_fixture_secret'
afterEach(async () => { for (const home of homes.splice(0)) await rm(home, { recursive: true, force: true }) })
async function fixture() {
  const { home, cwd } = await createTempHome(); homes.push(home, cwd)
  return home
}

describe('bounded registry diagnosis', () => {
  test('identity success makes one GET, uses the selected token and does not certify permissions', async () => {
    const home = await fixture(); let calls = 0
    const result = await runHealth({ registry, token }, { home, env: {}, fetch: async (input, init) => {
      calls++; expect(String(input)).toBe(registry + '/api/cli/v1/auth/whoami')
      expect(init?.method ?? 'GET').toBe('GET'); expect(init?.redirect).toBe('manual')
      expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer ' + token)
      return Response.json({ data: { handle: 'fixture-user', displayName: token, capabilities: [] } })
    } })
    expect(calls).toBe(1); expect(result.ok).toBe(true); expect(result.credentialSource).toBe('argument')
    expect(result.checks.find(x => x.id === 'identity')?.message).toContain('permissions were not tested')
    expect(JSON.stringify(result)).not.toContain(token)
  })
  test.each([401, 403])('HTTP %i does not delete credentials or expose raw server output', async status => {
    const home = await fixture(); await mkdir(join(home, '.skillhub'))
    const bytes = JSON.stringify({ tokens: { [registry]: token, 'https://other.example.com': 'sk_other_fixture' } })
    await writeFile(join(home, '.skillhub/credentials.json'), bytes)
    const result = await runHealth({ registry }, { home, env: {}, fetch: async () => Response.json({ msg: token, requestId: token }, { status }) })
    expect(result.exitCode).toBe(2)
    expect(result.checks.find(x => x.id === 'identity')?.code).toBe(status === 401 ? 'CREDENTIAL_REJECTED' : 'ACCESS_DENIED')
    expect(await readFile(join(home, '.skillhub/credentials.json'), 'utf8')).toBe(bytes)
    expect(JSON.stringify(result)).not.toContain(token)
    expect(result.checks.find(x => x.id === 'identity')?.next).toContain('logout --registry')
  })
  test('missing credential and a 401 are a warning, not a failure of anonymous usability', async () => {
    const result = await runHealth({ registry }, { home: await fixture(), env: {}, fetch: async (_input, init) => {
      expect(new Headers(init?.headers).has('Authorization')).toBe(false)
      return Response.json({ msg: 'not logged in' }, { status: 401 })
    } })
    expect(result.ok).toBe(true); expect(result.checks.find(x => x.id === 'identity')?.status).toBe('warn')
  })
  test.each(['argument', 'environment', 'stored', 'none'])('%s recovery guidance never interpolates a registry into shell commands', async source => {
    const home = await fixture()
    const selected = 'https://registry.example.com/$(uname)'
    if (source === 'stored') {
      await mkdir(join(home, '.skillhub'))
      await writeFile(join(home, '.skillhub/credentials.json'), JSON.stringify({ tokens: { [selected]: token } }))
    }
    const result = await runHealth({ registry: selected, ...(source === 'argument' ? { token } : {}) }, {
      home, env: source === 'environment' ? { SKILLHUB_TOKEN: token } : {}, fetch: async () => new Response(null, { status: 401 })
    })
    const next = result.checks.find(x => x.id === 'identity')?.next
    expect(result.registry).toBe(selected)
    expect(result.credentialSource).toBe(source)
    expect(next).toBeDefined()
    expect(next).not.toContain('$(uname)')
    expect(next).not.toContain(selected)
    expect(next).toContain('--registry')
  })
  test.each([['argument', { token }, {}], ['environment', {}, { SKILLHUB_TOKEN: token }]] as const)('%s token failure identifies its override source', async (source, options, env) => {
    const result = await runHealth({ registry, ...options }, { home: await fixture(), env, fetch: async () => new Response(null, { status: 401 }) })
    expect(result.credentialSource).toBe(source); expect(result.exitCode).toBe(2)
    expect(result.checks.find(x => x.id === 'identity')?.next).toContain(source === 'argument' ? 'without --token' : 'SKILLHUB_TOKEN')
  })
  test.each([[500, 3], [503, 3], [429, 1], [404, 1]])('HTTP %i remains an HTTP failure with exit %i', async (status, exit) => {
    const result = await runHealth({ registry, token }, { home: await fixture(), env: {}, fetch: async () => Response.json({ msg: token }, { status }) })
    expect(result.exitCode).toBe(exit); expect(result.checks.find(x => x.id === 'connectivity')?.code).toBe('HTTP_FAILURE')
    expect(result.checks.find(x => x.id === 'identity')?.status).toBe('skipped')
    expect(JSON.stringify(result)).not.toContain(token)
  })
  test.each(['ENOTFOUND', 'ECONNREFUSED', 'DEPTH_ZERO_SELF_SIGNED_CERT', 'UNABLE_TO_VERIFY_LEAF_SIGNATURE', 'ETIMEDOUT', 'ECONNRESET'])('transport code %s is classified without raw exception text', async code => {
    const result = await runHealth({ registry, token }, { home: await fixture(), env: {}, fetch: async () => {
      throw new TypeError('private message ' + token, { cause: { code } })
    } })
    const expected = code === 'ENOTFOUND' ? 'DNS_FAILURE' : code === 'ECONNREFUSED' ? 'CONNECTION_REFUSED'
      : code === 'ETIMEDOUT' ? 'TIMEOUT' : code === 'ECONNRESET' ? 'NETWORK_FAILURE' : 'TLS_FAILURE'
    expect(result.exitCode).toBe(3); expect(result.checks.find(x => x.id === 'connectivity')?.code).toBe(expected)
    expect(JSON.stringify(result)).not.toContain(token); expect(JSON.stringify(result)).not.toContain('private message')
  })
  test('timeout covers a pending response and does not hang', async () => {
    const started = Date.now()
    const result = await runHealth({ registry }, { home: await fixture(), env: {}, timeoutMs: 15, fetch: async (_input, init) => {
      return new Promise((_resolve, reject) => init?.signal?.addEventListener('abort', () => reject(new Error('aborted'))))
    } })
    expect(result.exitCode).toBe(3); expect(result.checks.find(x => x.id === 'connectivity')?.code).toBe('TIMEOUT')
    expect(Date.now() - started).toBeLessThan(2000)
  })
  test.each([Response.json({ data: {} }), new Response('<html>not a registry</html>'), new Response(null, { status: 204 })])('invalid identity payload is never reported as authenticated', async response => {
    const result = await runHealth({ registry, token }, { home: await fixture(), env: {}, fetch: async () => response.clone() })
    expect(result.exitCode).toBe(1); expect(result.checks.find(x => x.id === 'connectivity')?.code).toBe('INVALID_RESPONSE')
  })
  test('redirect is refused rather than forwarding a credential', async () => {
    let calls = 0
    const result = await runHealth({ registry, token }, { home: await fixture(), env: {}, fetch: async (_input, init) => {
      calls++; expect(init?.redirect).toBe('manual')
      return new Response(null, { status: 302, headers: { Location: 'https://unrelated.example.com' } })
    } })
    expect(calls).toBe(1); expect(result.checks.find(x => x.id === 'connectivity')?.code).toBe('REDIRECT_REFUSED')
    expect(result.exitCode).toBe(1)
  })
  test('short token values do not corrupt fixed status and schema fields', async () => {
    const result = await runHealth({ registry, token: 'a', offline: true }, { home: await fixture(), env: {} })
    expect(result.mode).toBe('health'); expect(result.checks.some(x => x.status === 'pass')).toBe(true)
  })
})
