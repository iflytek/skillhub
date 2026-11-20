import { createHash } from 'node:crypto'
import { lstat, mkdir, readdir, readFile, realpath, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { strToU8, zipSync } from 'fflate'
import { createTempHome } from '../helpers/temp-env'
import { startFakeRegistry, type FakeSkill } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'

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
  const registry = await startFakeRegistry({ skills })
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
  return { env, registry, rootDir, cliEnv }
}

function publish(skill: FakeSkill, version: string): void {
  skill.version = version
  skill.versionId = (skill.versionId ?? 1) + 1
  Object.assign(skill, makeSkill(`# ${skill.slug} ${version}`))
}

describe('outdated command', () => {
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
    expect(text.stdout).toContain('Outdated: 4 with newer versions, 1 unknown, 6 checked')
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
      summary: { checked: 1, outdated: 0, unknown: 0 },
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
    expect(team.exitCode).toBe(0)
    const teamOutput = JSON.parse(team.stdout)
    expect(teamOutput.summary).toEqual({ checked: 1, outdated: 0, unknown: 1 })
    expect(teamOutput.items[0]).toMatchObject({
      coordinate: '@team/code-review',
      remoteVersion: null,
      change: 'unknown',
      localState: 'clean'
    })
    expect(typeof teamOutput.items[0].reason).toBe('string')

    const otherAgent = await runCli(['outdated', '--registry', registry.url, '--agent', 'codex', '--json'], cliEnv, { cwd: env.cwd })
    expect(otherAgent.exitCode).toBe(0)
    expect(JSON.parse(otherAgent.stdout).summary).toEqual({ checked: 0, outdated: 0, unknown: 0 })
  })

  test('appears in help', async () => {
    const help = await runCli(['help', 'outdated'])
    expect(help.exitCode).toBe(0)
    expect(help.stdout).toContain('skillhub outdated')
  })
})
