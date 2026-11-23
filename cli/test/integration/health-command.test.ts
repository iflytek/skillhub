import { afterEach, describe, expect, test } from 'bun:test'
import { mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { createTempHome } from '../helpers/temp-env'
import { runCli } from '../helpers/run-cli'

const homes: string[] = []
const servers: Array<ReturnType<typeof Bun.serve>> = []
afterEach(async () => {
  for (const server of servers.splice(0)) server.stop(true)
  for (const home of homes.splice(0)) await rm(home, { recursive: true, force: true })
})
async function fixture() {
  const { home, cwd } = await createTempHome(); homes.push(home, cwd)
  // Keep Bun's runtime cache outside the observation contract on every OS.
  return { home, cwd, env: { HOME: home, USERPROFILE: home, BUN_RUNTIME_TRANSPILER_CACHE_PATH: '0' } }
}
function registry(handler: (request: Request) => Response) {
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch: handler }); servers.push(server)
  return `http://127.0.0.1:${server.port}`
}
async function save(home: string, name: string, value: unknown) {
  await mkdir(join(home, '.skillhub'), { recursive: true })
  await writeFile(join(home, '.skillhub', name + '.json'), JSON.stringify(value))
}

describe('doctor --health subprocess contract', () => {
  test('offline health emits a report without creating state or making HTTP requests', async () => {
    const f = await fixture(); let calls = 0
    const url = registry(() => { calls++; return new Response('unexpected') })
    const result = await runCli(['doctor', '--health', '--offline', '--registry', url, '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(0); expect(result.stderr).toBe('')
    const report = JSON.parse(result.stdout)
    expect(report.mode).toBe('health'); expect(report.schemaVersion).toBe(1); expect(report.exitCode).toBe(0)
    expect(report.checks.filter((x: { code: string }) => x.code === 'OFFLINE')).toHaveLength(2)
    expect(calls).toBe(0); expect(await readdir(f.home)).toEqual([]); expect(await readdir(f.cwd)).toEqual([])
  })
  test('selected registry and token win, while state bytes remain unchanged', async () => {
    const f = await fixture(); const token = 'sk_health_subprocess_fixture'; let calls = 0
    const selected = registry(req => {
      calls++; expect(new URL(req.url).pathname).toBe('/api/cli/v1/auth/whoami')
      expect(req.headers.get('Authorization')).toBe('Bearer ' + token)
      return Response.json({ data: { handle: 'selected', displayName: token } })
    })
    let otherCalls = 0
    const other = registry(() => { otherCalls++; return new Response('wrong registry') })
    await save(f.home, 'config', { registry: other, other: 'keep' })
    await save(f.home, 'credentials', { tokens: { [other]: 'sk_other_fixture', [selected]: 'sk_saved_fixture' } })
    await save(f.home, 'inventory', { items: [] })
    const names = ['config', 'credentials', 'inventory']
    const before = await Promise.all(names.map(name => readFile(join(f.home, '.skillhub', name + '.json'))))
    const result = await runCli(['doctor', '--health', '--registry', selected, '--token', token, '--json'], { ...f.env, SKILLHUB_REGISTRY: other, SKILLHUB_TOKEN: 'sk_environment_fixture' }, { cwd: f.cwd })
    expect(result.exitCode).toBe(0); expect(result.stderr).toBe(''); expect(calls).toBe(1); expect(otherCalls).toBe(0)
    const report = JSON.parse(result.stdout); expect(report.registrySource).toBe('argument'); expect(report.credentialSource).toBe('argument')
    expect(result.stdout).not.toContain(token)
    expect(await Promise.all(names.map(name => readFile(join(f.home, '.skillhub', name + '.json'))))).toEqual(before)
    expect((await readdir(join(f.home, '.skillhub'))).sort()).toEqual(names.map(name => name + '.json').sort())
  })
  test('a rejected saved credential produces stdout JSON and exit2 without logout/login side effects', async () => {
    const f = await fixture(); let calls = 0; const token = 'sk_rejected_fixture'
    const url = registry(() => { calls++; return Response.json({ msg: 'server echoes ' + token }, { status: 401 }) })
    await save(f.home, 'config', { registry: url })
    await save(f.home, 'credentials', { tokens: { [url]: token } })
    const before = await readFile(join(f.home, '.skillhub/credentials.json'))
    const result = await runCli(['doctor', '--health', '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(2); expect(result.stderr).toBe(''); expect(calls).toBe(1)
    const report = JSON.parse(result.stdout); expect(report.ok).toBe(false); expect(report.exitCode).toBe(2)
    expect(report.checks.find((x: { code: string }) => x.code === 'CREDENTIAL_REJECTED').next).toContain('login --registry')
    expect(result.stdout).not.toContain('logout --registry')
    expect(result.stdout).not.toContain(token); expect(await readFile(join(f.home, '.skillhub/credentials.json'))).toEqual(before)
  })
  test('missing login is a warning and human report contains actionable safe output', async () => {
    const f = await fixture(); const url = registry(() => new Response(null, { status: 401 }))
    const result = await runCli(['doctor', '--health', '--registry', url], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(0); expect(result.stderr).toBe('')
    expect(result.stdout).toContain('OK (with warnings)'); expect(result.stdout).toContain('NO_CREDENTIAL')
    expect(result.stdout).toContain('Read-only diagnosis'); expect(await readdir(f.home)).toEqual([])
  })
  test('connection refusal produces a complete report and network exit3', async () => {
    const f = await fixture(); const probe = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch: () => new Response() })
    const url = `http://127.0.0.1:${probe.port}`; probe.stop(true)
    const result = await runCli(['doctor', '--health', '--registry', url, '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(3); expect(result.stderr).toBe('')
    expect(JSON.parse(result.stdout).checks.find((x: { id: string }) => x.id === 'connectivity').status).toBe('fail')
  })
  test('a redirect never sends the token to its destination', async () => {
    const f = await fixture(); let destinationCalls = 0
    const destination = registry(() => { destinationCalls++; return Response.json({ data: { handle: 'wrong' } }) })
    const url = registry(() => new Response(null, { status: 302, headers: { Location: destination } }))
    const result = await runCli(['doctor', '--health', '--registry', url, '--token', 'sk_redirect_fixture', '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(1); expect(destinationCalls).toBe(0)
    expect(JSON.parse(result.stdout).checks.some((x: { code: string }) => x.code === 'REDIRECT_REFUSED')).toBe(true)
  })
  test('an unreadable configuration path is not hidden as missing state', async () => {
    const f = await fixture(); await mkdir(join(f.home, '.skillhub/config.json'), { recursive: true })
    const result = await runCli(['doctor', '--health', '--offline', '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(4); expect(result.stderr).toBe('')
    expect(JSON.parse(result.stdout).checks.find((x: { id: string }) => x.id === 'config').code).toBe('STATE_UNREADABLE')
  })
  test('timeout also bounds an HTTP body that never finishes', async () => {
    const f = await fixture()
    const url = registry(() => new Response(new ReadableStream({ start(controller) { controller.enqueue(new TextEncoder().encode('{"data":')) } })))
    const result = await runCli(['doctor', '--health', '--registry', url, '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(3)
    expect(JSON.parse(result.stdout).checks.find((x: { id: string }) => x.id === 'connectivity').code).toBe('TIMEOUT')
  }, 12000)
  test.each([{ args: ['--offline'] }, { args: ['--registry', 'https://registry.example.com'] }, { args: ['--token', 'sk_flag_fixture'] }, { args: ['--health=false'] }])('health-only flags %j fail before legacy inventory writes', async ({ args }) => {
    const f = await fixture(); const result = await runCli(['doctor', ...args, '--json'], f.env, { cwd: f.cwd })
    expect(result.exitCode).toBe(5); expect(result.stdout).toBe(''); expect(JSON.parse(result.stderr).ok).toBe(false)
    expect(await readdir(f.home)).toEqual([])
  })
})
