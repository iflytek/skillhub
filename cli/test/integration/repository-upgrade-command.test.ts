import { afterEach, expect, test } from 'bun:test'
import { createHash } from 'node:crypto'
import { readFile, rm, writeFile } from 'node:fs/promises'
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
  const add = (slug: string, version: string) => {
    const bytes = strToU8(`# ${slug} ${version}`)
    const hash = createHash('sha256').update(bytes).digest('hex')
    const old = plan.members.find(member => member.slug === slug)
    const id = old?.skillId ?? plan.members.length + 1
    const member = { skillId: id, namespace: 'github' as const, slug, displayName: slug, version, versionId: id + Number(version[0]) * 100,
      fingerprint: 'sha256:' + createHash('sha256').update(`SKILL.md:${hash}\n`).digest('hex'),
      downloadUrl: `/api/cli/v1/skills/github/${slug}/versions/${version}/download` }
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
