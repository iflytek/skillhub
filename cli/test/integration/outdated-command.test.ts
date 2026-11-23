import { createHash } from 'node:crypto'
import { lstat, mkdir, readdir, readFile, realpath, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { strToU8, zipSync } from 'fflate'
import { createTempHome } from '../helpers/temp-env'
import { startFakeRegistry, type FakeSkill } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'
import { CredentialsStore } from '../../src/stores/credentials-store'
import { InventoryStore } from '../../src/stores/inventory-store'

let registries: Array<Awaited<ReturnType<typeof startFakeRegistry>>> = []

afterEach(() => {
  for (const registry of registries) registry.stop()
  registries = []
})

function makeSkill(content: string): { fingerprint: string; zipBytes: Uint8Array } {
  const bytes = strToU8(content)
  const fileHash = createHash('sha256').update(bytes).digest('hex')
  return {
    fingerprint: `sha256:${createHash('sha256').update(`SKILL.md:${fileHash}\n`).digest('hex')}`,
    zipBytes: zipSync({ 'SKILL.md': bytes })
  }
}

/** Every file below the given roots, keyed by path, so any write is detectable. */
async function snapshotTree(...roots: string[]): Promise<Record<string, string>> {
  const files: Record<string, string> = {}
  for (const root of roots) {
    for (const relativePath of await readdir(root, { recursive: true })) {
      const path = join(root, relativePath)
      if (!(await lstat(path)).isFile()) continue
      files[path] = createHash('sha256').update(await readFile(path)).digest('hex')
    }
  }
  return files
}

async function setup(skills: FakeSkill[]) {
  const env = await createTempHome()
  const registryOptions: NonNullable<Parameters<typeof startFakeRegistry>[0]> = { skills }
  const registry = await startFakeRegistry(registryOptions)
  registries.push(registry)
  await mkdir(join(env.cwd, 'skills'), { recursive: true })
  const rootDir = await realpath(join(env.cwd, 'skills'))
  const cliEnv = { HOME: env.home, USERPROFILE: env.home }
  for (const skill of skills) {
    const installed = await runCli([
      'install', `@${skill.namespace}/${skill.slug}`, '--dir', rootDir, '--registry', registry.url
    ], cliEnv, { cwd: env.cwd })
    expect(installed.exitCode).toBe(0)
  }
  return { env, registry, rootDir, cliEnv, registryOptions }
}

function publish(skill: FakeSkill, version: string): void {
  skill.version = version
  skill.versionId = (skill.versionId ?? 1) + 1
  Object.assign(skill, makeSkill(`# ${skill.slug} ${version}`))
}

describe('outdated command', () => {
  test('shows the upstream repository tag of a GitHub-synced remote version', async () => {
    const synced: FakeSkill = { namespace: 'github', slug: 'tdd', version: '0.0.0-ga-1', ...makeSkill('# tdd') }
    const plain: FakeSkill = { namespace: 'team', slug: 'plain', version: '1.0.0', ...makeSkill('# plain') }
    const { registry, cliEnv, env } = await setup([synced, plain])
    publish(synced, '0.0.0-gb-2')
    publish(plain, '1.0.1')
    synced.githubSource = {
      ownerLogin: 'mattpocock', repositoryName: 'skills', skillPath: 'skills/tdd',
      commitSha: '4ae2317ddb22b046e26455e4f9c9b3aaaf62212d', syncRevision: 2,
      upstreamTag: { status: 'AFTER_TAG', scope: 'REPOSITORY', tag: 'v1.3.2',
        tagCommitSha: 'd1478fcb140db0b93c0c8df17bcfd311844749d0', commitsAfterTag: 3 },
      declaredVersion: null
    }

    const text = await runCli(['outdated', '--registry', registry.url], cliEnv, { cwd: env.cwd })
    expect(text.exitCode).toBe(0)
    expect(text.stdout).toContain('@github/tdd 0.0.0-ga-1 -> 0.0.0-gb-2  (upstream v1.3.2+3)  custom')
    expect(text.stdout).toContain('@team/plain 1.0.0 -> 1.0.1  custom')

    const json = await runCli(['outdated', '--registry', registry.url, '--json'], cliEnv, { cwd: env.cwd })
    const items = JSON.parse(json.stdout).items as Array<{ coordinate: string; remoteSource?: unknown }>
    expect(items.find(item => item.coordinate === '@github/tdd')?.remoteSource).toEqual(synced.githubSource)
    expect(items.find(item => item.coordinate === '@team/plain')).not.toHaveProperty('remoteSource')
  })

  test('classifies newer versions, flags local edits and changes no files', async () => {
    const skills: FakeSkill[] = [
      { namespace: 'team', slug: 'code-review', version: '1.0.0', ...makeSkill('# code-review') },
      { namespace: 'team', slug: 'java-guide', version: '1.0.0', ...makeSkill('# java-guide') },
      { namespace: 'global', slug: 'example-skill', version: '1.0.0', ...makeSkill('# example-skill') },
      { namespace: 'team', slug: 'beta-skill', version: '1.0.0', ...makeSkill('# beta-skill') },
      { namespace: 'team', slug: 'nightly-skill', version: '1.0.0', ...makeSkill('# nightly-skill') },
      { namespace: 'team', slug: 'stable-skill', version: '1.0.0', ...makeSkill('# stable-skill') }
    ]
    const { env, registry, rootDir, cliEnv } = await setup(skills)
    publish(skills[0]!, '1.0.1')
    publish(skills[1]!, '1.1.0')
    publish(skills[2]!, '2.0.0')
    publish(skills[3]!, '1.1.0-beta.1')
    publish(skills[4]!, 'nightly')
    await writeFile(join(rootDir, 'code-review', 'SKILL.md'), '# locally edited')

    const before = await snapshotTree(env.home, env.cwd)
    const downloadsBefore = registry.received.downloads

    const json = await runCli(['outdated', '--registry', registry.url, '--json'], cliEnv, { cwd: env.cwd })
    expect(json.exitCode).toBe(0)
    expect(json.stderr).toBe('')
    const output = JSON.parse(json.stdout)
    expect(output).toMatchObject({
      ok: true,
      registry: registry.url,
      summary: { checked: 6, outdated: 4, unknown: 1 }
    })
    const byCoordinate = Object.fromEntries(
      output.items.map((item: { coordinate: string }) => [item.coordinate, item])
    )
    expect(Object.keys(byCoordinate).sort()).toEqual([
      '@global/example-skill',
      '@team/beta-skill',
      '@team/code-review',
      '@team/java-guide',
      '@team/nightly-skill'
    ])
    expect(byCoordinate['@team/code-review']).toEqual({
      coordinate: '@team/code-review',
      registry: registry.url,
      agent: 'custom',
      installDir: join(rootDir, 'code-review'),
      currentVersion: '1.0.0',
      remoteVersion: '1.0.1',
      change: 'patch',
      localState: 'modified',
      changedFiles: [`${join(rootDir, 'code-review')}:SKILL.md`]
    })
    expect(byCoordinate['@team/java-guide']).toMatchObject({ change: 'minor', localState: 'clean', changedFiles: [] })
    expect(byCoordinate['@global/example-skill']).toMatchObject({ change: 'major', remoteVersion: '2.0.0' })
    expect(byCoordinate['@team/beta-skill']).toMatchObject({ change: 'prerelease', remoteVersion: '1.1.0-beta.1' })
    expect(byCoordinate['@team/nightly-skill']).toMatchObject({
      change: 'unknown',
      remoteVersion: 'nightly',
      reason: 'cannot determine version order'
    })

    const text = await runCli(['outdated', '--registry', registry.url], cliEnv, { cwd: env.cwd })
    expect(text.exitCode).toBe(0)
    expect(text.stdout).toContain('Outdated: 4 with newer versions, 1 unknown, 0 failed, 6 checked')
    expect(text.stdout).toContain(
      `patch      @team/code-review 1.0.0 -> 1.0.1  custom  ${join(rootDir, 'code-review')} [local changes: 1 file]`
    )
    expect(text.stdout).toContain(`major      @global/example-skill 1.0.0 -> 2.0.0  custom  ${join(rootDir, 'example-skill')}`)
    expect(text.stdout).toContain('prerelease @team/beta-skill 1.0.0 -> 1.1.0-beta.1')
    expect(text.stdout).toContain('unknown    @team/nightly-skill 1.0.0 -> nightly')
    expect(text.stdout).not.toContain('stable-skill')

    // Read-only: no file under home or cwd changed, nothing was downloaded.
    expect(await snapshotTree(env.home, env.cwd)).toEqual(before)
    expect(registry.received.downloads).toBe(downloadsBefore)
    expect(await readFile(join(rootDir, 'code-review', 'SKILL.md'), 'utf-8')).toBe('# locally edited')
  })

  test('reports up-to-date and empty inventories with exit 0', async () => {
    const skills: FakeSkill[] = [
      { namespace: 'team', slug: 'code-review', version: '1.0.0', ...makeSkill('# code-review') }
    ]
    const { env, registry, cliEnv } = await setup(skills)

    const text = await runCli(['outdated', '--registry', registry.url], cliEnv, { cwd: env.cwd })
    expect(text.exitCode).toBe(0)
    expect(text.stdout).toBe('All 1 checked installations are up to date.')

    const json = await runCli(['outdated', '--registry', registry.url, '--json'], cliEnv, { cwd: env.cwd })
    expect(json.exitCode).toBe(0)
    expect(JSON.parse(json.stdout)).toEqual({
      ok: true,
      registry: registry.url,
      summary: { checked: 1, outdated: 0, unknown: 0, failed: 0 },
      items: []
    })

    const empty = await createTempHome()
    const none = await runCli(['outdated', '--registry', registry.url], { HOME: empty.home, USERPROFILE: empty.home })
    expect(none.exitCode).toBe(0)
    expect(none.stdout).toBe('No skills installed.')
  })

  test('applies namespace and agent filters and reports unresolvable skills as unknown', async () => {
    const skills: FakeSkill[] = [
      { namespace: 'team', slug: 'code-review', version: '1.0.0', ...makeSkill('# code-review') },
      { namespace: 'global', slug: 'example-skill', version: '1.0.0', ...makeSkill('# example-skill') }
    ]
    const { env, registry, cliEnv } = await setup(skills)
    publish(skills[1]!, '1.0.1')
    skills.splice(0, 1)

    const team = await runCli(['outdated', '--registry', registry.url, '--namespace', 'team', '--json'], cliEnv, { cwd: env.cwd })
    expect(team.exitCode).toBe(1)
    const teamOutput = JSON.parse(team.stdout)
    expect(teamOutput.summary).toEqual({ checked: 1, outdated: 0, unknown: 1, failed: 1 })
    expect(teamOutput.ok).toBe(false)
    expect(teamOutput.items[0]).toMatchObject({
      coordinate: '@team/code-review',
      remoteVersion: null,
      change: 'unknown',
      localState: 'clean'
    })
    expect(typeof teamOutput.items[0].reason).toBe('string')

    const otherAgent = await runCli(['outdated', '--registry', registry.url, '--agent', 'codex', '--json'], cliEnv, { cwd: env.cwd })
    expect(otherAgent.exitCode).toBe(0)
    expect(JSON.parse(otherAgent.stdout).summary).toEqual({ checked: 0, outdated: 0, unknown: 0, failed: 0 })
  })

  test('appears in help', async () => {
    const help = await runCli(['help', 'outdated'])
    expect(help.exitCode).toBe(0)
    expect(help.stdout).toContain('skillhub outdated')
  })

  test('does not silently hide a GitHub update because its hash sorts backwards', async () => {
    const skill: FakeSkill = { namespace: 'github', slug: 'hash-skill',
      version: `0.0.0-g${'b'.repeat(40)}-${'1'.repeat(16)}`, ...makeSkill('# hash skill') }
    const { env, registry, cliEnv } = await setup([skill])
    publish(skill, `0.0.0-g${'a'.repeat(40)}-${'2'.repeat(16)}`)
    const before = await snapshotTree(env.home, env.cwd)
    const downloads = registry.received.downloads
    const result = await runCli(['outdated', '--registry', registry.url, '--json'], cliEnv, { cwd: env.cwd })
    expect(result.exitCode).toBe(0)
    expect(JSON.parse(result.stdout)).toMatchObject({
      ok: true, summary: { checked: 1, outdated: 0, unknown: 1, failed: 0 },
      items: [{ coordinate: '@github/hash-skill', change: 'unknown', remoteVersion: skill.version }]
    })
    expect(await snapshotTree(env.home, env.cwd)).toEqual(before)
    expect(registry.received.downloads).toBe(downloads)
  })

  test.each([['auth', 2], ['forbidden', 2], ['server_error', 1], ['rate_limited', 1]] as const)(
    'reports an incomplete %s lookup with a nonzero exit and unchanged files', async (failure, exitCode) => {
      const skill: FakeSkill = { namespace: 'team', slug: 'failed-skill', ...makeSkill('# skill') }
      const { env, registry, cliEnv, registryOptions } = await setup([skill])
      registryOptions.failures = { resolve: failure }
      const before = await snapshotTree(env.home, env.cwd)
      const downloads = registry.received.downloads
      const result = await runCli(['outdated', '--registry', registry.url, '--json'], cliEnv, { cwd: env.cwd })
      expect(result.exitCode).toBe(exitCode)
      expect(JSON.parse(result.stdout)).toMatchObject({
        ok: false, summary: { checked: 1, outdated: 0, unknown: 1, failed: 1 },
        items: [{ coordinate: '@team/failed-skill', remoteVersion: null, change: 'unknown' }]
      })
      expect(JSON.parse(result.stderr)).toMatchObject({ ok: false, exitCode })
      expect(await snapshotTree(env.home, env.cwd)).toEqual(before)
      expect(registry.received.downloads).toBe(downloads)
    }
  )

  test('preserves successful results when another lookup fails', async () => {
    const skills: FakeSkill[] = [
      { namespace: 'team', slug: 'missing-skill', ...makeSkill('# missing') },
      { namespace: 'team', slug: 'updated-skill', ...makeSkill('# updated') }
    ]
    const { env, registry, cliEnv } = await setup(skills)
    publish(skills[1]!, '1.1.0')
    skills.splice(0, 1)
    const result = await runCli(['outdated', '--registry', registry.url, '--json'], cliEnv, { cwd: env.cwd })
    expect(result.exitCode).not.toBe(0)
    const output = JSON.parse(result.stdout)
    expect(output).toMatchObject({ ok: false, summary: { checked: 2, outdated: 1, unknown: 1, failed: 1 } })
    expect(output.items).toContainEqual(expect.objectContaining({ coordinate: '@team/updated-skill', change: 'minor' }))
    expect(output.items).toContainEqual(expect.objectContaining({ coordinate: '@team/missing-skill', change: 'unknown' }))
  })

  test('checks only the effective registry and uses only its stored credential', async () => {
    const first: FakeSkill = { namespace: 'team', slug: 'first-skill', ...makeSkill('# first') }
    const second: FakeSkill = { namespace: 'team', slug: 'second-skill', ...makeSkill('# second') }
    const { env, registry, cliEnv } = await setup([first])
    const other = await startFakeRegistry({ skills: [second] })
    registries.push(other)
    expect((await runCli(['install', '@team/second-skill', '--dir', join(env.cwd, 'other-skills'),
      '--registry', other.url], cliEnv, { cwd: env.cwd })).exitCode).toBe(0)
    const credentials = new CredentialsStore(env.home)
    await credentials.setToken(registry.url, 'test-read-token-first')
    await credentials.setToken(other.url, 'test-read-token-second')
    publish(first, '1.0.1')
    publish(second, '1.1.0')
    registry.received.resolve = null
    other.received.resolve = null
    const before = await snapshotTree(env.home, env.cwd)
    const downloads = [registry.received.downloads, other.received.downloads]

    const selected = { ...cliEnv, SKILLHUB_REGISTRY: registry.url }
    const current = await runCli(['outdated', '--json'], selected, { cwd: env.cwd })
    expect(current.exitCode).toBe(0)
    expect(JSON.parse(current.stdout)).toMatchObject({ registry: registry.url,
      summary: { checked: 1 }, items: [{ coordinate: '@team/first-skill' }] })
    expect(registry.received.resolve).toMatchObject({ token: 'Bearer test-read-token-first' })
    expect(other.received.resolve).toBeNull()

    registry.received.resolve = null
    const explicit = await runCli(['outdated', '--registry', other.url, '--json'], selected, { cwd: env.cwd })
    expect(explicit.exitCode).toBe(0)
    expect(JSON.parse(explicit.stdout)).toMatchObject({ registry: other.url,
      summary: { checked: 1 }, items: [{ coordinate: '@team/second-skill' }] })
    expect(other.received.resolve).toMatchObject({ token: 'Bearer test-read-token-second' })
    expect(registry.received.resolve).toBeNull()
    expect(await snapshotTree(env.home, env.cwd)).toEqual(before)
    expect([registry.received.downloads, other.received.downloads]).toEqual(downloads)
  })

  test('reports a disconnected registry without changing local files', async () => {
    const { env, registry, cliEnv } = await setup([
      { namespace: 'team', slug: 'offline-skill', ...makeSkill('# offline') }
    ])
    const before = await snapshotTree(env.home, env.cwd)
    registry.stop()
    const result = await runCli(['outdated', '--registry', registry.url, '--json'], cliEnv, { cwd: env.cwd })
    expect(result.exitCode).toBe(3)
    expect(JSON.parse(result.stdout)).toMatchObject({ ok: false, summary: { checked: 1, failed: 1 } })
    expect(JSON.parse(result.stderr)).toMatchObject({ ok: false, exitCode: 3 })
    expect(await snapshotTree(env.home, env.cwd)).toEqual(before)
  })

  test('reads a legacy inventory without migrating or rewriting it', async () => {
    const skill: FakeSkill = { namespace: 'team', slug: 'legacy-skill', ...makeSkill('# legacy') }
    const { env, registry, cliEnv } = await setup([skill])
    const store = new InventoryStore(env.home)
    const current = await store.readPersisted()
    const item = current.items[0]!
    const target = item.targets[0]!
    await writeFile(store.path, JSON.stringify({ items: [{ registry: item.registry,
      namespace: item.namespace, slug: item.slug, version: target.version, fingerprint: target.fingerprint,
      targets: [{ agent: target.agent, rootDir: target.rootDir, installDir: target.installDir,
        installedAt: target.installedAt }] }] }))
    publish(skill, '1.0.1')
    const before = await snapshotTree(env.home, env.cwd)
    const result = await runCli(['outdated', '--registry', registry.url, '--json'], cliEnv, { cwd: env.cwd })
    expect(result.exitCode).toBe(0)
    expect(JSON.parse(result.stdout)).toMatchObject({ ok: true,
      items: [{ coordinate: '@team/legacy-skill', change: 'patch', localState: 'clean' }] })
    expect(await snapshotTree(env.home, env.cwd)).toEqual(before)
    expect(await store.diskVersion()).toBe(1)
  })
})
