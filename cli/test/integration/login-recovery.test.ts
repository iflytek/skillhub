import { afterEach, describe, expect, test } from 'bun:test'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { createTempHome } from '../helpers/temp-env'
import { runCli } from '../helpers/run-cli'

const homes: string[] = []
const servers: Array<ReturnType<typeof Bun.serve>> = []
afterEach(async () => {
  for (const server of servers.splice(0)) server.stop(true)
  for (const home of homes.splice(0)) await rm(home, { recursive: true, force: true })
})
interface Scenario { initialStatus?: number; freshStatus?: number; deviceStatus?: number; pollStatus?: number; pollError?: string; expiresIn?: number; freshIdentity?: unknown; onPoll?: () => Promise<void> }
async function fixture(options: Scenario = {}) {
  const { home, cwd } = await createTempHome(); homes.push(home, cwd)
  const calls = { identity: 0, device: 0, poll: 0 }
  const failure = (status: number) => Response.json({ msg: status === 403 ? 'access denied' : 'authentication failed' }, { status })
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, async fetch(req) {
    const path = new URL(req.url).pathname
    if (path.endsWith('/api/cli/v1/auth/whoami')) {
      calls.identity++
      const status = req.headers.get('authorization') === 'Bearer sk_recovery_new' ? options.freshStatus ?? 200 : options.initialStatus ?? 401
      return status === 200 ? Response.json({ data: 'freshIdentity' in options ? options.freshIdentity : { handle: 'recovered-user' } }) : failure(status)
    }
    if (path.endsWith('/api/v1/auth/device/code')) {
      calls.device++
      if (options.deviceStatus) return failure(options.deviceStatus)
      return Response.json({ data: { deviceCode: 'recovery-device-secret', userCode: 'ABCD-2345', verificationUri: '/device', expiresIn: options.expiresIn ?? 30, interval: 0.001 } })
    }
    if (path.endsWith('/api/v1/auth/device/token')) {
      calls.poll++
      await options.onPoll?.()
      if (options.pollStatus) return failure(options.pollStatus)
      return Response.json({ data: options.pollError ? { error: options.pollError } : { accessToken: 'sk_recovery_new', tokenType: 'Bearer' } })
    }
    return new Response(null, { status: 404 })
  } }); servers.push(server)
  const registry = `http://127.0.0.1:${server.port}`
  const config = join(home, '.skillhub/config.json'), credentials = join(home, '.skillhub/credentials.json')
  await mkdir(join(home, '.skillhub'))
  await writeFile(config, JSON.stringify({ registry, preference: 'keep' }))
  await writeFile(credentials, JSON.stringify({ user: { token: 'third-party-keep' }, tokens: { [registry]: 'sk_recovery_old', 'https://other.example.com': 'sk_other_keep' } }))
  const before = await Promise.all([readFile(config), readFile(credentials)])
  return { home, cwd, registry, config, credentials, calls, before, env: { HOME: home, USERPROFILE: home, BUN_RUNTIME_TRANSPILER_CACHE_PATH: '0' } }
}
async function unchanged(f: Awaited<ReturnType<typeof fixture>>) {
  expect(await Promise.all([readFile(f.config), readFile(f.credentials)])).toEqual(f.before)
}
function jsonEvents(stderr: string): Array<Record<string, unknown>> {
  return stderr.split('\n').filter(Boolean).map(line => JSON.parse(line))
}
function secretFree(output: string) {
  for (const value of ['sk_recovery_old', 'sk_recovery_new', 'sk_other_keep', 'third-party-keep', 'recovery-device-secret']) expect(output).not.toContain(value)
}

describe('saved credential recovery boundaries', () => {
  test.each([403, 404, 429, 500, 503])('stored HTTP %i never starts device authorization or changes local state', async status => {
    const f = await fixture({ initialStatus: status })
    const result = await runCli(['login', '--registry', f.registry, '--no-open', '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(status === 403 ? 2 : status === 503 ? 3 : 1)
    expect(result.stdout).toBe(''); expect(jsonEvents(result.stderr)).toHaveLength(1)
    expect(f.calls).toEqual({ identity: 1, device: 0, poll: 0 })
    await unchanged(f); secretFree(result.stderr)
  })
  const explicitCases = ['argument', 'environment'].flatMap(source => [401, 403].flatMap(status => [false, true].map(json => ({ source, status, json }))))
  test.each(explicitCases)('explicit input %j keeps overrides and recovery instructions safe', async ({ source, status, json }) => {
    const f = await fixture({ initialStatus: status }); const registry = f.registry + '/$(uname)'
    const args = ['login', '--registry', registry, '--no-open', ...(json ? ['--json'] : []), ...(source === 'argument' ? ['--token', 'sk_recovery_explicit'] : [])]
    const result = await runCli(args, { ...f.env, ...(source === 'environment' ? { SKILLHUB_TOKEN: 'sk_recovery_explicit' } : {}) }, { cwd: f.cwd })
    expect(result.exitCode).toBe(2); expect(result.stdout).toBe('')
    const error = json ? jsonEvents(result.stderr)[0]! : undefined
    const next = json ? String((error!.details as Record<string, unknown>)?.next ?? '') : result.stderr.split('\n').find(line => line.startsWith('Next:')) ?? ''
    if (status === 401) {
      expect(next).toContain(source === 'argument' ? 'without --token' : 'Unset SKILLHUB_TOKEN')
      expect(next).toContain('--registry <registry>')
    } else {
      expect(result.stderr).toContain('access denied'); expect(next).not.toContain('browser')
    }
    expect(next).not.toContain('$(uname)'); expect(result.stderr).not.toContain('sk_recovery_explicit')
    expect(f.calls).toEqual({ identity: 1, device: 0, poll: 0 })
    if (json) {
      expect(Object.keys(error!).sort()).toEqual(['details', 'exitCode', 'message', 'ok'])
      expect((error!.details as Record<string, unknown>).httpStatus).toBeUndefined()
    }
    await unchanged(f)
  })
  test('successful recovery changes only the selected credential and retains compatible fields', async () => {
    const f = await fixture()
    const result = await runCli(['login', '--registry', f.registry, '--no-open', '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(0)
    expect(JSON.parse(result.stdout)).toEqual({ ok: true, registry: f.registry, handle: 'recovered-user' })
    expect(jsonEvents(result.stderr)).toMatchObject([{ event: 'device_authorization', browserOpened: false }])
    expect(JSON.parse(await readFile(f.credentials, 'utf8'))).toEqual({ user: { token: 'third-party-keep' }, tokens: { [f.registry]: 'sk_recovery_new', 'https://other.example.com': 'sk_other_keep' } })
    expect(JSON.parse(await readFile(f.config, 'utf8'))).toEqual({ registry: f.registry, preference: 'keep' })
    expect(f.calls).toEqual({ identity: 2, device: 1, poll: 1 }); secretFree(result.stdout + result.stderr)
  })
  test('recovery preserves another registry update made while authorization is pending', async () => {
    const options: Scenario = {}
    const f = await fixture(options)
    options.onPoll = async () => {
      const current = JSON.parse(await readFile(f.credentials, 'utf8'))
      current.tokens['https://other.example.com'] = 'sk_other_updated'
      await writeFile(f.credentials, JSON.stringify(current))
    }
    const result = await runCli(['login', '--registry', f.registry, '--no-open', '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(0)
    expect(JSON.parse(await readFile(f.credentials, 'utf8')).tokens).toEqual({ [f.registry]: 'sk_recovery_new', 'https://other.example.com': 'sk_other_updated' })
    expect(f.calls.device).toBe(1)
  })
  test('an invalid stored identity is a protocol failure, not a reason to start another authorization', async () => {
    const f = await fixture({ initialStatus: 200, freshIdentity: null })
    const result = await runCli(['login', '--registry', f.registry, '--no-open', '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(1)
    expect(f.calls).toEqual({ identity: 1, device: 0, poll: 0 })
    await unchanged(f)
  })
  test.each([401, 403, 500])('a new token rejected with HTTP %i leaves the old credential intact without another device flow', async freshStatus => {
    const f = await fixture({ freshStatus })
    const result = await runCli(['login', '--registry', f.registry, '--no-open', '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(freshStatus === 500 ? 1 : 2); expect(result.stdout).toBe('')
    expect(jsonEvents(result.stderr)).toMatchObject([{ event: 'device_authorization' }, { ok: false }])
    expect(f.calls).toEqual({ identity: 2, device: 1, poll: 1 }); await unchanged(f); secretFree(result.stderr)
  })
  test.each([null, {}, { handle: '' }, { handle: 123 }])('an invalid new identity %j cannot replace the old credential', async freshIdentity => {
    const f = await fixture({ freshIdentity })
    const result = await runCli(['login', '--registry', f.registry, '--no-open', '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(1)
    expect(jsonEvents(result.stderr).at(-1)).toMatchObject({ ok: false })
    await unchanged(f); secretFree(result.stderr)
  })
  test.each(['access_denied', 'expired_token'])('device authorization %s preserves both state files', async pollError => {
    const f = await fixture({ pollError })
    const result = await runCli(['login', '--registry', f.registry, '--no-open', '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(2)
    expect(jsonEvents(result.stderr)).toMatchObject([{ event: 'device_authorization' }, { ok: false }])
    await unchanged(f); secretFree(result.stderr)
  })
  test.each([{ deviceStatus: 503 }, { pollStatus: 503 }])('device network-stage failure %j does not replace credentials', async scenario => {
    const f = await fixture(scenario)
    const result = await runCli(['login', '--registry', f.registry, '--no-open', '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(3); expect(jsonEvents(result.stderr).at(-1)).toMatchObject({ ok: false, exitCode: 3 })
    await unchanged(f); secretFree(result.stderr)
  })
  test('authorization expires without replacing the old credential', async () => {
    const f = await fixture({ pollError: 'authorization_pending', expiresIn: 0.002 })
    const result = await runCli(['login', '--registry', f.registry, '--no-open', '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(2); expect(jsonEvents(result.stderr).at(-1)).toMatchObject({ message: 'device authorization expired' })
    expect(f.calls.device).toBe(1); await unchanged(f); secretFree(result.stderr)
  })
})
