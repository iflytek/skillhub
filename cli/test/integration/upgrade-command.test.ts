import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { strToU8, zipSync } from 'fflate'
import { createTempHome } from '../helpers/temp-env'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'

let registries: Array<Awaited<ReturnType<typeof startFakeRegistry>>> = []

afterEach(() => {
  for (const registry of registries) registry.stop()
  registries = []
})

function makeSkillZip(content: string): Uint8Array {
  return zipSync({ 'SKILL.md': strToU8(content) })
}

describe('upgrade command', () => {
  test('check is side-effect free and execute upgrades an installed skill', async () => {
    const env = await createTempHome()
    const skill = {
      namespace: 'global',
      slug: 'skillhub-registry',
      version: '1.0.0',
      versionId: 1,
      fingerprint: 'fp-v1',
      zipBytes: makeSkillZip('# v1')
    }
    const registry = await startFakeRegistry({ skills: [skill] })
    registries.push(registry)
    const rootDir = join(env.cwd, 'skills')
    await mkdir(rootDir, { recursive: true })

    const installed = await runCli([
      'install', '@global/skillhub-registry', '--dir', rootDir, '--registry', registry.url
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(installed.exitCode).toBe(0)

    skill.version = '1.1.0'
    skill.versionId = 2
    skill.fingerprint = 'fp-v2'
    skill.zipBytes = makeSkillZip('# v2')

    const checked = await runCli([
      'upgrade', '@global/skillhub-registry', '--registry', registry.url, '--check', '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(checked.exitCode).toBe(0)
    expect(JSON.parse(checked.stdout).items[0]).toMatchObject({
      coordinate: '@global/skillhub-registry',
      currentVersion: '1.0.0',
      remoteVersion: '1.1.0',
      action: 'upgrade'
    })
    expect(await readFile(join(rootDir, 'skillhub-registry', 'SKILL.md'), 'utf-8')).toBe('# v1')
    expect(registry.received.downloads).toBe(1)

    const upgraded = await runCli([
      'upgrade', '@global/skillhub-registry', '--registry', registry.url, '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(upgraded.exitCode).toBe(0)
    expect(JSON.parse(upgraded.stdout).items[0].action).toBe('upgraded')
    expect(await readFile(join(rootDir, 'skillhub-registry', 'SKILL.md'), 'utf-8')).toBe('# v2')
    expect(registry.received.downloads).toBe(2)

    const metadata = JSON.parse(await readFile(
      join(rootDir, 'skillhub-registry', '.skillhub', 'metadata.json'),
      'utf-8'
    ))
    expect(metadata).toMatchObject({ schemaVersion: 1, version: '1.1.0', versionId: 2, fingerprint: 'fp-v2' })
  })

  test('local changes block by default and --force replaces only the same source', async () => {
    const env = await createTempHome()
    const skill = {
      namespace: 'team',
      slug: 'code-review',
      version: '1.0.0',
      fingerprint: 'fp-v1',
      zipBytes: makeSkillZip('# v1')
    }
    const registry = await startFakeRegistry({ skills: [skill] })
    registries.push(registry)
    const rootDir = join(env.cwd, 'skills')
    await mkdir(rootDir, { recursive: true })
    await runCli(['install', '@team/code-review', '--dir', rootDir, '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    await writeFile(join(rootDir, 'code-review', 'SKILL.md'), '# locally edited')
    skill.version = '1.1.0'
    skill.fingerprint = 'fp-v2'
    skill.zipBytes = makeSkillZip('# v2')

    const blocked = await runCli([
      'upgrade', '@team/code-review', '--registry', registry.url, '--check', '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(blocked.exitCode).toBe(6)
    expect(JSON.parse(blocked.stdout).items[0]).toMatchObject({ action: 'blocked' })
    expect(JSON.parse(blocked.stdout).items[0].reason).toContain('local changes')
    expect(await readFile(join(rootDir, 'code-review', 'SKILL.md'), 'utf-8')).toBe('# locally edited')

    const forced = await runCli([
      'upgrade', '@team/code-review', '--registry', registry.url, '--force', '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(forced.exitCode).toBe(0)
    expect(await readFile(join(rootDir, 'code-review', 'SKILL.md'), 'utf-8')).toBe('# v2')
  })

  test('source conflict is a hard block even with --force', async () => {
    const env = await createTempHome()
    const skill = {
      namespace: 'global',
      slug: 'demo',
      version: '1.0.0',
      fingerprint: 'fp-v1',
      zipBytes: makeSkillZip('# v1')
    }
    const registry = await startFakeRegistry({ skills: [skill] })
    registries.push(registry)
    const rootDir = join(env.cwd, 'skills')
    await mkdir(rootDir, { recursive: true })
    await runCli(['install', '@global/demo', '--dir', rootDir, '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    const metadataPath = join(rootDir, 'demo', '.skillhub', 'metadata.json')
    const metadata = JSON.parse(await readFile(metadataPath, 'utf-8'))
    metadata.namespace = 'another-team'
    await writeFile(metadataPath, JSON.stringify(metadata))
    skill.version = '2.0.0'
    skill.fingerprint = 'fp-v2'

    const result = await runCli([
      'upgrade', '@global/demo', '--registry', registry.url, '--force', '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(result.exitCode).toBe(6)
    expect(JSON.parse(result.stdout).items[0].reason).toContain('source-conflict')
    expect(await readFile(join(rootDir, 'demo', 'SKILL.md'), 'utf-8')).toBe('# v1')
  })

  test('a bare slug must identify exactly one installed source', async () => {
    const env = await createTempHome()
    const skillA = { namespace: 'team-a', slug: 'demo', version: '1.0.0', fingerprint: 'a', zipBytes: makeSkillZip('# A') }
    const skillB = { namespace: 'team-b', slug: 'demo', version: '1.0.0', fingerprint: 'b', zipBytes: makeSkillZip('# B') }
    const registryA = await startFakeRegistry({ skills: [skillA] })
    const registryB = await startFakeRegistry({ skills: [skillB] })
    registries.push(registryA, registryB)
    const rootA = join(env.cwd, 'a')
    const rootB = join(env.cwd, 'b')
    await mkdir(rootA, { recursive: true })
    await mkdir(rootB, { recursive: true })
    await runCli(['install', '@team-a/demo', '--dir', rootA, '--registry', registryA.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })
    await runCli(['install', '@team-b/demo', '--dir', rootB, '--registry', registryB.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    const ambiguous = await runCli(['upgrade', 'demo', '--check'], {
      HOME: env.home,
      USERPROFILE: env.home
    })
    expect(ambiguous.exitCode).toBe(5)
    expect(ambiguous.stderr).toContain('ambiguous')

    const selected = await runCli(['upgrade', 'demo', '--namespace', 'team-a', '--registry', registryA.url, '--check'], {
      HOME: env.home,
      USERPROFILE: env.home
    })
    expect(selected.exitCode).toBe(0)
    expect(selected.stdout).toContain('@team-a/demo')
  })

  test('one resolved archive is reused for every managed target', async () => {
    const env = await createTempHome()
    const skill = {
      namespace: 'global',
      slug: 'shared',
      version: '1.0.0',
      fingerprint: 'fp-v1',
      zipBytes: makeSkillZip('# v1')
    }
    const registry = await startFakeRegistry({ skills: [skill] })
    registries.push(registry)

    const installed = await runCli([
      'install', '@global/shared', '--agent', 'codex', '--agent', 'claude-code', '--registry', registry.url
    ], { HOME: env.home, USERPROFILE: env.home }, { cwd: env.cwd })
    expect(installed.exitCode).toBe(0)
    expect(registry.received.downloads).toBe(1)

    skill.version = '1.1.0'
    skill.fingerprint = 'fp-v2'
    skill.zipBytes = makeSkillZip('# v2')

    const partial = await runCli([
      'upgrade', '@global/shared', '--registry', registry.url, '--agent', 'codex', '--check', '--json'
    ], { HOME: env.home, USERPROFILE: env.home }, { cwd: env.cwd })
    expect(partial.exitCode).toBe(6)
    expect(JSON.parse(partial.stdout).items[0].reason).toContain('partial-target')
    expect(registry.received.downloads).toBe(1)

    const upgraded = await runCli(['upgrade', '@global/shared', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    }, { cwd: env.cwd })
    expect(upgraded.exitCode).toBe(0)
    expect(registry.received.downloads).toBe(2)
    expect(await readFile(join(env.home, '.codex', 'skills', 'shared', 'SKILL.md'), 'utf-8')).toBe('# v2')
    expect(await readFile(join(env.home, '.claude', 'skills', 'shared', 'SKILL.md'), 'utf-8')).toBe('# v2')
  })

  test('never downgrades when the registry latest version moves backwards', async () => {
    const env = await createTempHome()
    const skill = {
      namespace: 'global',
      slug: 'stable',
      version: '2.0.0',
      versionId: 2,
      fingerprint: 'fp-v2',
      zipBytes: makeSkillZip('# v2')
    }
    const registry = await startFakeRegistry({ skills: [skill] })
    registries.push(registry)
    const rootDir = join(env.cwd, 'skills')
    await mkdir(rootDir, { recursive: true })
    await runCli(['install', '@global/stable', '--dir', rootDir, '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    skill.version = '1.0.0'
    skill.versionId = 1
    skill.fingerprint = 'fp-v1'
    skill.zipBytes = makeSkillZip('# v1')

    const result = await runCli([
      'upgrade', '@global/stable', '--registry', registry.url, '--force', '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(result.exitCode).toBe(6)
    expect(JSON.parse(result.stdout).items[0].reason).toContain('older')
    expect(await readFile(join(rootDir, 'stable', 'SKILL.md'), 'utf-8')).toBe('# v2')
    expect(registry.received.downloads).toBe(1)
  })

  test('a blocked batch reports a plan and does not claim successful writes', async () => {
    const env = await createTempHome()
    const first = { namespace: 'global', slug: 'first', version: '1.0.0', fingerprint: 'first-v1', zipBytes: makeSkillZip('# first v1') }
    const second = { namespace: 'global', slug: 'second', version: '1.0.0', fingerprint: 'second-v1', zipBytes: makeSkillZip('# second v1') }
    const registry = await startFakeRegistry({ skills: [first, second] })
    registries.push(registry)
    const rootDir = join(env.cwd, 'skills')
    await mkdir(rootDir, { recursive: true })
    for (const slug of ['first', 'second']) {
      await runCli(['install', `@global/${slug}`, '--dir', rootDir, '--registry', registry.url], {
        HOME: env.home,
        USERPROFILE: env.home
      })
    }

    first.version = '1.1.0'
    first.fingerprint = 'first-v2'
    first.zipBytes = makeSkillZip('# first v2')
    second.version = '1.1.0'
    second.fingerprint = 'second-v2'
    second.zipBytes = makeSkillZip('# second v2')
    await writeFile(join(rootDir, 'second', 'SKILL.md'), '# local change')

    const result = await runCli([
      'upgrade', '@global/first', '@global/second', '--registry', registry.url, '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(result.exitCode).toBe(6)
    const output = JSON.parse(result.stdout)
    expect(output.items.find((item: { coordinate: string }) => item.coordinate.endsWith('/first')).action).toBe('upgrade')
    expect(output.items.find((item: { coordinate: string }) => item.coordinate.endsWith('/second')).action).toBe('blocked')
    expect(await readFile(join(rootDir, 'first', 'SKILL.md'), 'utf-8')).toBe('# first v1')
  })

  test('legacy metadata without a file baseline requires explicit force migration', async () => {
    const env = await createTempHome()
    const skill = { namespace: 'global', slug: 'legacy', version: '1.0.0', fingerprint: 'v1', zipBytes: makeSkillZip('# v1') }
    const registry = await startFakeRegistry({ skills: [skill] })
    registries.push(registry)
    const rootDir = join(env.cwd, 'skills')
    await mkdir(rootDir, { recursive: true })
    await runCli(['install', '@global/legacy', '--dir', rootDir, '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })
    const metadataPath = join(rootDir, 'legacy', '.skillhub', 'metadata.json')
    const metadata = JSON.parse(await readFile(metadataPath, 'utf-8'))
    delete metadata.files
    delete metadata.schemaVersion
    await writeFile(metadataPath, JSON.stringify(metadata))
    skill.version = '1.1.0'
    skill.fingerprint = 'v2'
    skill.zipBytes = makeSkillZip('# v2')

    const blocked = await runCli([
      'upgrade', '@global/legacy', '--registry', registry.url, '--check', '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(blocked.exitCode).toBe(6)
    expect(JSON.parse(blocked.stdout).items[0].reason).toContain('no file baseline')

    const migrated = await runCli([
      'upgrade', '@global/legacy', '--registry', registry.url, '--force', '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(migrated.exitCode).toBe(0)
    expect(await readFile(join(rootDir, 'legacy', 'SKILL.md'), 'utf-8')).toBe('# v2')
  })

  test('never installs a missing skill and never offers an implicit upgrade-all', async () => {
    const env = await createTempHome()
    const missing = await runCli(['upgrade', '@global/missing', '--check'], {
      HOME: env.home,
      USERPROFILE: env.home
    })
    expect(missing.exitCode).toBe(5)
    expect(missing.stderr).toContain('use skillhub install')

    const empty = await runCli(['upgrade'], { HOME: env.home, USERPROFILE: env.home })
    expect(empty.exitCode).toBe(5)
    expect(empty.stderr).toContain('at least one')

    const tooMany = await runCli([
      'upgrade', ...Array.from({ length: 51 }, (_, index) => `@global/skill-${index}`)
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(tooMany.exitCode).toBe(5)
    expect(tooMany.stderr).toContain('at most 50')
  })
})
