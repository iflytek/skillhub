import { afterEach, expect, test } from 'bun:test'
import { createHash } from 'node:crypto'
import { readFile, rename, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { zipSync, strToU8 } from 'fflate'
import { createTempHome } from '../helpers/temp-env'
import { runCli } from '../helpers/run-cli'
import type { RepositoryInstallPlan } from '../../src/clients/skillhub-client'

const servers: Array<ReturnType<typeof Bun.serve>> = []
const dirs: string[] = []
afterEach(async () => {
  servers.splice(0).forEach(server => server.stop(true))
  await Promise.all(dirs.splice(0).map(dir => rm(dir, { recursive: true, force: true })))
})
async function fixture(count = 2) {
  const env = await createTempHome()
  dirs.push(env.home, env.cwd)
  const archives = new Map<string, ArrayBuffer>()
  const requests: string[] = []
  const plan: RepositoryInstallPlan = { repositoryId: '42', owner: 'fixture', name: 'repo', members: [] }
  const add = (slug: string, version: string, versionId?: number) => {
    const bytes = strToU8(`# ${slug} ${version}`)
    const hash = createHash('sha256').update(bytes).digest('hex')
    const old = plan.members.find(member => member.slug === slug)
    const id = old?.skillId ?? plan.members.length + 1
    const member = { skillId: id, namespace: 'github' as const, slug, displayName: slug, version, versionId: versionId ?? id + Number(version[0]) * 100,
      fingerprint: 'sha256:' + createHash('sha256').update(`SKILL.md:${hash}\n`).digest('hex'),
      downloadUrl: `/api/cli/v1/skills/github/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}/download` }
    archives.set(member.downloadUrl, new Uint8Array(zipSync({ 'SKILL.md': bytes })).buffer)
    if (old) plan.members[plan.members.indexOf(old)] = member
    else plan.members.push(member)
  }
  for (let i = 0; i < count; i++) add(`skill-${i}`, '1.0.0')
  let denied = ''
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
    const path = new URL(request.url).pathname
    requests.push(path)
    if (request.headers.get('Authorization') !== 'Bearer fake_read_token') return Response.json({ msg: 'denied' }, { status: 403 })
    if (path.endsWith('/42/install-plan')) return Response.json({ data: plan })
    if (denied && path.includes(denied)) return Response.json({ msg: 'unavailable' }, { status: 403 })
    if (archives.has(path)) return new Response(archives.get(path)!.slice(0))
    return Response.json({ msg: 'do not resolve latest again' }, { status: 500 })
  } })
  servers.push(server)
  const target = join(env.cwd, 'skills')
  const run = (action: string, extra: string[] = []) => runCli(['repository', action, '42', '--dir', target, '--registry', server.url.origin, '--json', ...extra],
    { HOME: env.home, USERPROFILE: env.home, SKILLHUB_TOKEN: 'fake_read_token' })
  const metadata = async (slug: string) => JSON.parse(await readFile(join(target, slug, '.skillhub/metadata.json'), 'utf8'))
  return { env, plan, requests, target, run, add, metadata, deny: (slug: string) => { denied = slug } }
}

test('repository check and upgrade --check remain compatible read-only entry points', async () => {
  const f = await fixture(1)
  expect((await f.run('install')).exitCode).toBe(0)
  f.add('skill-0', '2.0.0')
  const inventoryPath = join(f.env.home, '.skillhub', 'inventory.json')
  const inventory = await readFile(inventoryPath, 'utf8')
  const metadata = await f.metadata('skill-0')
  const checks = [await f.run('check'), await f.run('upgrade', ['--check'])]
  expect(checks[0]).toEqual(checks[1])
  expect(checks[0]!.exitCode).toBe(0)
  expect(JSON.parse(checks[0]!.stdout).summary.upgrades).toBe(1)
  expect(await readFile(inventoryPath, 'utf8')).toBe(inventory)
  expect(await f.metadata('skill-0')).toEqual(metadata)
})

test('check is read-only and upgrade pins only existing members, never new upstream skills', async () => {
  const f = await fixture(1)
  expect((await f.run('install')).exitCode).toBe(0)
  f.add('skill-0', '2.0.0')
  f.add('new-member', '1.0.0')
  const checked = await f.run('check')
  expect(checked.exitCode).toBe(0)
  expect(JSON.parse(checked.stdout).summary).toEqual({ upgrades: 1, unchanged: 0, blocked: 0 })
  expect((await f.metadata('skill-0')).version).toBe('1.0.0')
  const upgraded = await f.run('upgrade')
  expect(upgraded.exitCode).toBe(0)
  expect(JSON.parse(upgraded.stdout).summary.upgraded).toBe(1)
  expect((await f.metadata('skill-0')).version).toBe('2.0.0')
  expect((await f.metadata('skill-0')).sourceRepositoryId).toBe('42')
  expect(await Bun.file(join(f.target, 'new-member/SKILL.md')).exists()).toBe(false)
  expect(f.requests.some(path => path.includes('/resolve'))).toBe(false)
})

test('local changes remain protected while an independent safe member upgrades', async () => {
  const f = await fixture()
  expect((await f.run('install')).exitCode).toBe(0)
  f.add('skill-0', '2.0.0'); f.add('skill-1', '2.0.0')
  await writeFile(join(f.target, 'skill-0/SKILL.md'), '# my edits')
  const result = await f.run('upgrade')
  expect(result.exitCode).toBe(6)
  const output = JSON.parse(result.stdout)
  expect(output.ok).toBe(false)
  expect(output.summary).toMatchObject({ upgraded: 1, blocked: 1, notAttempted: 1 })
  expect(await readFile(join(f.target, 'skill-0/SKILL.md'), 'utf8')).toBe('# my edits')
  expect((await f.metadata('skill-0')).version).toBe('1.0.0')
  expect((await f.metadata('skill-1')).version).toBe('2.0.0')
})

test('no-longer-listed repository members are reported and preserved without guessing private server state', async () => {
  const f = await fixture()
  expect((await f.run('install')).exitCode).toBe(0)
  f.plan.members = f.plan.members.filter(member => member.slug !== 'skill-1')
  const result = await f.run('check')
  expect(result.exitCode).toBe(6)
  const output = JSON.parse(result.stdout)
  expect(output.summary).toMatchObject({ unchanged: 1, blocked: 1 })
  expect(output.items.find((item: { coordinate: string }) => item.coordinate === '@github/skill-1').reason).toContain('local files are preserved')
  expect((await f.metadata('skill-1')).version).toBe('1.0.0')
})

test('legacy metadata without repository provenance remains readable and upgradeable', async () => {
  const f = await fixture(1)
  expect((await f.run('install')).exitCode).toBe(0)
  const metadata = await f.metadata('skill-0')
  delete metadata.sourceRepositoryId
  await writeFile(join(f.target, 'skill-0/.skillhub/metadata.json'), JSON.stringify(metadata))
  f.add('skill-0', '2.0.0')
  expect((await f.run('upgrade', ['--check'])).exitCode).toBe(0)
  expect((await f.metadata('skill-0')).version).toBe('1.0.0')
  expect((await f.run('upgrade')).exitCode).toBe(0)
  expect((await f.metadata('skill-0')).version).toBe('2.0.0')
})

test('partial download failure retains committed upgrades and fails without lying about remaining work', async () => {
  const f = await fixture()
  expect((await f.run('install')).exitCode).toBe(0)
  f.add('skill-0', '2.0.0'); f.add('skill-1', '2.0.0'); f.deny('skill-1')
  const result = await f.run('upgrade')
  expect(result.exitCode).toBe(2)
  expect(JSON.parse(result.stdout).summary).toMatchObject({ upgraded: 1, failed: 1 })
  expect((await f.metadata('skill-0')).version).toBe('2.0.0')
  expect((await f.metadata('skill-1')).version).toBe('1.0.0')
  expect(result.stdout + result.stderr).not.toContain('fake_read_token')
})

test('repository checks cover more than the individual command selection limit without re-resolving', async () => {
  const f = await fixture(51)
  expect((await f.run('install')).exitCode).toBe(0)
  const start = f.requests.length
  const result = await f.run('check')
  expect(result.exitCode).toBe(0)
  expect(JSON.parse(result.stdout).summary).toEqual({ upgrades: 0, unchanged: 51, blocked: 0 })
  expect(f.requests.slice(start)).toEqual(['/api/cli/v1/github-repositories/42/install-plan'])
}, 30000)

const githubVersion = (hash: string, ingestion: string) => `0.0.0-g${hash.repeat(40)}-${ingestion.repeat(16)}`

test('real GitHub versions follow verified creation order in both hash directions and on upstream revert', async () => {
  for (const [before, after] of [['f', '1'], ['1', 'f']]) {
    const f = await fixture(1)
    f.add('skill-0', githubVersion(before!, '1'), 101)
    expect((await f.run('install')).exitCode).toBe(0)
    f.add('skill-0', githubVersion(after!, '2'), 201)
    expect((await f.run('upgrade')).exitCode).toBe(0)
    expect((await f.metadata('skill-0')).versionId).toBe(201)
    // Returning to old content is a distinct later ingestion, not a downgrade.
    f.add('skill-0', githubVersion(before!, '3'), 301)
    expect((await f.run('upgrade')).exitCode).toBe(0)
    expect((await f.metadata('skill-0')).versionId).toBe(301)
    const unchanged = await f.run('check')
    expect(unchanged.exitCode).toBe(0)
    expect(JSON.parse(unchanged.stdout).summary.unchanged).toBe(1)
  }
})

test('an older remote GitHub release cannot be mistaken for an upgrade by hash ordering', async () => {
  const f = await fixture(1)
  f.add('skill-0', githubVersion('1', '2'), 201)
  expect((await f.run('install')).exitCode).toBe(0)
  f.add('skill-0', githubVersion('f', '1'), 101)
  const result = await f.run('upgrade')
  expect(result.exitCode).toBe(6)
  expect(JSON.parse(result.stdout).summary.blocked).toBe(1)
  expect((await f.metadata('skill-0')).versionId).toBe(201)
})

test('legacy GitHub metadata without ordering evidence is protected without forced migration', async () => {
  const f = await fixture(1)
  f.add('skill-0', githubVersion('1', '1'), 101)
  expect((await f.run('install')).exitCode).toBe(0)
  const old = await f.metadata('skill-0')
  delete old.versionId
  await writeFile(join(f.target, 'skill-0/.skillhub/metadata.json'), JSON.stringify(old))
  expect((await f.run('check')).exitCode).toBe(0) // Exact version/fingerprint needs no ordering migration.
  f.add('skill-0', githubVersion('f', '2'), 201)
  expect((await f.run('upgrade')).exitCode).toBe(6)
  expect((await f.metadata('skill-0')).version).toBe(old.version)
  expect((await f.metadata('skill-0')).versionId).toBeUndefined()
})

test('disabled members are planned as protected while independent enabled members upgrade', async () => {
  const f = await fixture()
  expect((await f.run('install')).exitCode).toBe(0)
  await rename(join(f.target, 'skill-0/SKILL.md'), join(f.target, 'skill-0/SKILL.md.disabled'))
  f.add('skill-0', '2.0.0'); f.add('skill-1', '2.0.0')
  const checked = await f.run('check')
  expect(checked.exitCode, checked.stderr).toBe(6)
  expect(JSON.parse(checked.stdout).summary).toEqual({ upgrades: 1, unchanged: 0, blocked: 1 })
  const result = await f.run('upgrade')
  expect(result.exitCode).toBe(6)
  expect(JSON.parse(result.stdout).summary).toMatchObject({ upgraded: 1, blocked: 1, failed: 0 })
  expect(await Bun.file(join(f.target, 'skill-0/SKILL.md.disabled')).exists()).toBe(true)
  expect(await Bun.file(join(f.target, 'skill-0/SKILL.md')).exists()).toBe(false)
  expect((await f.metadata('skill-0')).version).toBe('1.0.0')
  expect((await f.metadata('skill-1')).version).toBe('2.0.0')
})

test('an unverifiable disabled target is blocked without aborting safe members', async () => {
  const f = await fixture()
  expect((await f.run('install')).exitCode).toBe(0)
  await rename(join(f.target, 'skill-0/SKILL.md'), join(f.target, 'skill-0/SKILL.md.disabled'))
  const old = await f.metadata('skill-0')
  await writeFile(join(f.target, 'skill-0/.skillhub/metadata.json'), JSON.stringify({ ...old, agent: 'different-agent' }))
  f.add('skill-0', '2.0.0'); f.add('skill-1', '2.0.0')
  const checked = await f.run('check')
  expect(checked.exitCode, checked.stderr).toBe(6)
  expect(JSON.parse(checked.stdout).summary).toEqual({ upgrades: 1, unchanged: 0, blocked: 1 })
  const result = await f.run('upgrade')
  expect(result.exitCode, result.stderr).toBe(6)
  expect(JSON.parse(result.stdout).summary).toMatchObject({ upgraded: 1, blocked: 1, failed: 0 })
  expect(await readFile(join(f.target, 'skill-0/SKILL.md.disabled'), 'utf8')).toBe('# skill-0 1.0.0')
  expect((await f.metadata('skill-0')).version).toBe('1.0.0')
  expect((await f.metadata('skill-1')).version).toBe('2.0.0')
})
