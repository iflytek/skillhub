import { afterEach, expect, test } from 'bun:test'
import { mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { strToU8, zipSync } from 'fflate'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'
import { createTempHome } from '../helpers/temp-env'

const cleanup: Array<() => void | Promise<void>> = []
afterEach(async () => {
  for (const stop of cleanup.splice(0).reverse()) await stop()
})

async function fixture(response: () => Response) {
  const env = await createTempHome()
  cleanup.push(() => rm(env.home, { recursive: true, force: true }), () => rm(env.cwd, { recursive: true, force: true }))
  const skill = { namespace: 'global', slug: 'ordinary-skill', version: '1.0.0', versionId: 1,
    zipBytes: zipSync({ 'SKILL.md': strToU8('# Ordinary skill') }) }
  const backend = await startFakeRegistry({ skills: [skill], applicationRelativeDownloads: true })
  cleanup.push(backend.stop)
  const requests: string[] = []
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, async fetch(request) {
    const url = new URL(request.url)
    requests.push(url.pathname)
    if (url.pathname.endsWith('/install-plan')) return response()
    return fetch(new URL(url.pathname + url.search, backend.url), { headers: request.headers })
  } })
  cleanup.push(() => { server.stop(true) })
  const registry = server.url.origin
  const target = join(env.cwd, 'skills')
  const state = join(env.home, '.skillhub')
  await mkdir(target, { recursive: true })
  await mkdir(state, { recursive: true })
  const credentialPath = join(state, 'credentials.json')
  const credentials = JSON.stringify({ tokens: { [registry]: 'fake-compatibility-token' } })
  await writeFile(credentialPath, credentials)
  await writeFile(join(target, 'keep.txt'), 'preserve local files')
  const run = (args: string[]) => runCli([...args, '--registry', registry, '--json'],
    { HOME: env.home, USERPROFILE: env.home }, { cwd: env.cwd })
  const assertUnchanged = async () => {
    expect(await readFile(credentialPath, 'utf8')).toBe(credentials)
    expect(await readdir(state)).toEqual(['credentials.json'])
    expect(await readdir(target)).toEqual(['keep.txt'])
    expect(await readFile(join(target, 'keep.txt'), 'utf8')).toBe('preserve local files')
  }
  return { env, registry, target, requests, run, assertUnchanged, skill, stop: () => { server.stop(true) } }
}

for (const action of ['install', 'check', 'upgrade']) {
  test.each([404, 405, 501])(`repository ${action} explains unavailable optional endpoints (%s) without local writes`, async status => {
    const f = await fixture(() => new Response('<html>unavailable upstream page</html>', { status }))
    const result = await f.run(['repository', action, '42', '--dir', f.target])
    expect(result.exitCode).toBe(1)
    expect(result.stdout).toBe('')
    expect(JSON.parse(result.stderr)).toMatchObject({ ok: false, exitCode: 1,
      message: expect.stringContaining(status === 404 ? 'may not be enabled' : 'not available'),
      details: { feature: 'repository', status, next: expect.stringContaining('install <coordinate>') } })
    expect(result.stderr).not.toContain('upstream page')
    expect(result.stderr).not.toContain('fake-compatibility-token')
    expect(f.requests).toEqual(['/api/cli/v1/github-repositories/42/install-plan'])
    await f.assertUnchanged()
  })

  test(`repository ${action} explains a successful HTML fallback without unexpected failure`, async () => {
    const f = await fixture(() => new Response('<html>private proxy page</html>', { headers: { 'Content-Type': 'text/html' } }))
    const result = await f.run(['repository', action, '42', '--dir', f.target])
    expect(result.exitCode).toBe(6)
    expect(result.stdout).toBe('')
    expect(JSON.parse(result.stderr)).toMatchObject({ ok: false,
      message: expect.stringContaining('compatible repository installation plan'),
      details: { feature: 'repository', status: 200 } })
    expect(result.stderr).not.toContain('private proxy page')
    await f.assertUnchanged()
  })
}

test.each([[401, 2], [403, 2], [429, 1], [500, 1], [503, 3]])('repository preserves status %s instead of claiming feature unavailability', async (status, exitCode) => {
  const f = await fixture(() => Response.json({ msg: 'original public reason', requestId: 'req-compatibility' }, { status }))
  const result = await f.run(['repository', 'check', '42', '--dir', f.target])
  expect(result.exitCode).toBe(exitCode)
  expect(result.stdout).toBe('')
  const error = JSON.parse(result.stderr)
  expect(error).toMatchObject({ message: 'original public reason', details: { requestId: 'req-compatibility' } })
  expect(error.details.feature).toBeUndefined()
  await f.assertUnchanged()
})

test('structured repository not-found remains a resource error with its original request ID', async () => {
  const f = await fixture(() => Response.json({ msg: 'Source not found', requestId: 'req-source',
    errorCode: 'error.githubSync.source.notFound' }, { status: 404 }))
  const result = await f.run(['repository', 'check', '42'])
  expect(result.exitCode).toBe(1)
  expect(JSON.parse(result.stderr)).toMatchObject({ message: 'Source not found',
    details: { errorCode: 'error.githubSync.source.notFound', requestId: 'req-source' } })
  expect(result.stderr).not.toContain('not enabled')
  await f.assertUnchanged()
})

test('repository network failure stays a network error without local writes', async () => {
  const f = await fixture(() => new Response(null, { status: 404 }))
  f.stop()
  const result = await f.run(['repository', 'check', '42'])
  expect(result.exitCode).toBe(3)
  expect(result.stdout).toBe('')
  expect(JSON.parse(result.stderr)).toMatchObject({ message: 'registry unreachable' })
  expect(result.stderr).not.toContain('may not be enabled')
  await f.assertUnchanged()
})

test.each([null, { data: null }, { data: { repositoryId: '42', members: [] } }])('incompatible JSON plans fail validation without writes (%j)', async body => {
  const f = await fixture(() => Response.json(body))
  const result = await f.run(['repository', 'install', '42', '--dir', f.target])
  expect(result.exitCode).toBe(6)
  expect(result.stdout).toBe('')
  expect(JSON.parse(result.stderr).message).toBe('registry returned an invalid repository installation plan')
  await f.assertUnchanged()
})

test('human output explains unavailable repository support and a safe next step', async () => {
  const f = await fixture(() => new Response(null, { status: 501 }))
  const result = await runCli(['repository', 'check', '42', '--registry', f.registry],
    { HOME: f.env.home, USERPROFILE: f.env.home }, { cwd: f.env.cwd })
  expect(result.exitCode).toBe(1)
  expect(result.stdout).toBe('')
  expect(result.stderr).toContain('Repository batch operations are not available')
  expect(result.stderr).toContain('Next:')
  expect(result.stderr).toContain('skillhub install <coordinate>')
  await f.assertUnchanged()
})

test('ordinary skill install, outdated and upgrade keep working on a registry without repository support', async () => {
  const f = await fixture(() => new Response(null, { status: 404 }))
  expect((await f.run(['repository', 'check', '42'])).exitCode).toBe(1)
  expect((await f.run(['whoami'])).exitCode).toBe(0)
  expect((await f.run(['search', 'ordinary'])).exitCode).toBe(0)
  expect((await f.run(['install', '@global/ordinary-skill', '--dir', f.target])).exitCode).toBe(0)
  const inventory = join(f.env.home, '.skillhub', 'inventory.json')
  const before = await readFile(inventory, 'utf8')
  f.skill.version = '1.1.0'
  f.skill.versionId = 2
  const discovered = await f.run(['outdated', '--dir', f.target])
  expect(discovered.exitCode).toBe(0)
  expect(JSON.parse(discovered.stdout).items[0]).toMatchObject({ coordinate: '@global/ordinary-skill', change: 'minor' })
  const checked = await f.run(['upgrade', '@global/ordinary-skill', '--dir', f.target, '--check'])
  expect(checked.exitCode).toBe(0)
  expect(JSON.parse(checked.stdout).summary.upgrades).toBe(1)
  expect(await readFile(inventory, 'utf8')).toBe(before)
  const upgraded = await f.run(['upgrade', '@global/ordinary-skill', '--dir', f.target])
  expect(upgraded.exitCode).toBe(0)
  expect(JSON.parse(upgraded.stdout).summary.upgraded).toBe(1)
  expect(f.requests.filter(path => path.includes('github-repositories'))).toHaveLength(1)
  expect(await readFile(join(f.target, 'keep.txt'), 'utf8')).toBe('preserve local files')
})
