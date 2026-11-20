import { afterEach, expect, test } from 'bun:test'
import { createHash } from 'node:crypto'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { zipSync, strToU8 } from 'fflate'
import { runCli } from '../helpers/run-cli'
import { createTempHome } from '../helpers/temp-env'
import type { RepositoryInstallPlan } from '../../src/clients/skillhub-client'

const servers: Array<ReturnType<typeof Bun.serve>> = []
const directories: string[] = []
afterEach(async () => {
  for (const server of servers.splice(0)) server.stop(true)
  await Promise.all(directories.splice(0).map(path => rm(path, { recursive: true, force: true })))
})

async function fixture() {
  const env = await createTempHome()
  directories.push(env.home, env.cwd)
  const requests: string[] = []
  const text = '# Repository test skill'
  const fileHash = createHash('sha256').update(text).digest('hex')
  const fingerprint = 'sha256:' + createHash('sha256').update(`SKILL.md:${fileHash}\n`).digest('hex')
  const plan: RepositoryInstallPlan = { repositoryId: '42', owner: 'test', name: 'skills', members: ['alpha', 'beta'].map((slug, i) => ({
    skillId: i + 1, namespace: 'github', slug, displayName: slug, version: `${i + 1}.0.0`, versionId: i + 11,
    fingerprint, downloadUrl: `/api/cli/v1/skills/github/${slug}/versions/${i + 1}.0.0/download`
  })) }
  let failDownload = false
  const server = Bun.serve({ port: 0, hostname: '127.0.0.1', fetch(request) {
    const path = new URL(request.url).pathname
    requests.push(path)
    if (request.headers.get('Authorization') !== 'Bearer sk_repository_test') return Response.json({ msg: 'denied' }, { status: 403 })
    if (path.endsWith('/42/install-plan')) return Response.json({ data: plan })
    const member = plan.members.find(item => path === item.downloadUrl)
    if (member) {
      if (failDownload && member.slug === 'beta') return Response.json({ msg: 'access denied' }, { status: 403 })
      return new Response(new Uint8Array(zipSync({ 'SKILL.md': strToU8(text) })).buffer)
    }
    return Response.json({ msg: 'latest must not be resolved again' }, { status: 500 })
  } })
  servers.push(server)
  const target = join(env.cwd, 'skills')
  const run = (extra: string[] = []) => runCli(['repository', 'install', '42', '--dir', target, '--registry', server.url.origin,
    '--json', ...extra], { HOME: env.home, USERPROFILE: env.home, SKILLHUB_TOKEN: 'sk_repository_test' })
  return { env, plan, requests, target, run, failBeta: () => { failDownload = true } }
}

test('pins mixed published versions in one plan without resolving latest or writing repository records', async () => {
  const f = await fixture()
  const result = await f.run()
  expect(result.exitCode).toBe(0)
  expect(result.stderr).toBe('')
  expect(JSON.parse(result.stdout).summary).toEqual({ installed: 2, skipped: 0, failed: 0 })
  for (const member of f.plan.members) {
    const metadata = JSON.parse(await readFile(join(f.target, member.slug, '.skillhub', 'metadata.json'), 'utf8'))
    expect(metadata).toMatchObject({ namespace: 'github', slug: member.slug, version: member.version, versionId: member.versionId, fingerprint: member.fingerprint, sourceRepositoryId: '42' })
  }
  expect(f.requests).toEqual(['/api/cli/v1/github-repositories/42/install-plan', ...f.plan.members.map(member => member.downloadUrl)])
  const inventory = JSON.parse(await readFile(join(f.env.home, '.skillhub', 'inventory.json'), 'utf8'))
  expect(inventory.records).toHaveLength(2)
  expect(inventory.repositories).toBeUndefined()
  expect(inventory.suites ?? []).toHaveLength(0)
})

test('partial authorization failure keeps prior success and reports nonzero JSON results', async () => {
  const f = await fixture()
  f.failBeta()
  const result = await f.run()
  expect(result.exitCode).toBe(2)
  expect(JSON.parse(result.stdout).summary).toEqual({ installed: 1, skipped: 0, failed: 1 })
  expect(JSON.parse(result.stderr).ok).toBe(false)
  expect(await readFile(join(f.target, 'alpha', 'SKILL.md'), 'utf8')).toContain('Repository test')
  expect(await Bun.file(join(f.target, 'beta', 'SKILL.md')).exists()).toBe(false)
  expect(result.stdout + result.stderr).not.toContain('sk_repository_test')
})

test('unrelated existing files are preserved and other members still install', async () => {
  const f = await fixture()
  await mkdir(join(f.target, 'alpha'), { recursive: true })
  await writeFile(join(f.target, 'alpha', 'SKILL.md'), 'my local skill')
  const result = await f.run()
  expect(result.exitCode).not.toBe(0)
  expect(JSON.parse(result.stdout).summary).toEqual({ installed: 1, skipped: 0, failed: 1 })
  expect(await readFile(join(f.target, 'alpha', 'SKILL.md'), 'utf8')).toBe('my local skill')
  expect(await Bun.file(join(f.target, 'beta', 'SKILL.md')).exists()).toBe(true)
})

test('duplicate or empty plans fail before any download or filesystem writes', async () => {
  for (const empty of [false, true]) {
    const f = await fixture()
    if (empty) f.plan.members = []
    else f.plan.members.push(f.plan.members[0]!)
    const result = await f.run()
    expect(result.exitCode).toBe(6)
    expect(result.stdout).toBe('')
    expect(f.requests).toHaveLength(1)
    expect(await Bun.file(join(f.env.home, '.skillhub', 'inventory.json')).exists()).toBe(false)
  }
})

test('fingerprint mismatch cannot publish corrupted files or successful inventory', async () => {
  const f = await fixture()
  f.plan.members[0]!.fingerprint = 'sha256:' + '0'.repeat(64)
  const result = await f.run()
  expect(result.exitCode).toBe(6)
  expect(JSON.parse(result.stdout).summary).toEqual({ installed: 1, skipped: 0, failed: 1 })
  expect(await Bun.file(join(f.target, 'alpha', 'SKILL.md')).exists()).toBe(false)
})

test('batch force is deliberately unsupported and invalid repository IDs are rejected', async () => {
  const f = await fixture()
  const forced = await f.run(['--force'])
  expect(forced.exitCode).toBe(5)
  const invalid = await runCli(['repository', 'install', '../other', '--json'], { HOME: f.env.home, USERPROFILE: f.env.home })
  expect(invalid.exitCode).toBe(5)
  expect(f.requests).toHaveLength(0)
})

test('mixed ASCII, Chinese and emoji members all install with exact encoded paths', async () => {
  const f = await fixture()
  const first = f.plan.members[0]!
  f.plan.members = ['alpha', '技能包', '🚀-rocket'].map((slug, i) => ({ ...first,
    slug, displayName: slug, skillId: i + 1, versionId: i + 11,
    downloadUrl: `/api/cli/v1/skills/github/${encodeURIComponent(slug)}/versions/1.0.0/download` }))
  const result = await f.run()
  expect(result.exitCode).toBe(0)
  expect(JSON.parse(result.stdout).summary.installed).toBe(3)
  for (const slug of ['alpha', '技能包', '🚀-rocket']) {
    expect(await Bun.file(join(f.target, slug, 'SKILL.md')).exists()).toBe(true)
  }
})

test('Unicode support does not permit path traversal or separator slugs', async () => {
  for (const slug of ['../escape', 'a/b', 'a\\b', '.skillhub']) {
    const f = await fixture()
    f.plan.members[0]!.slug = slug
    f.plan.members[0]!.downloadUrl = `/api/cli/v1/skills/github/${encodeURIComponent(slug)}/versions/1.0.0/download`
    expect((await f.run()).exitCode).toBe(6)
    expect(f.requests).toHaveLength(1)
  }
})
