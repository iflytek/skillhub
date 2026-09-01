import { access, mkdir, readFile, realpath, rm, writeFile } from 'node:fs/promises'
import { isAbsolute, join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { strToU8, zipSync } from 'fflate'
import { createTempHome } from '../helpers/temp-env'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'
import { executeSkillUpgradePlan, planSkillUpgrades } from '../../src/services/upgrade-service'
import { installSkill } from '../../src/services/install-service'
import { renderUpgradeResult } from '../../src/commands/upgrade'

let registries: Array<Awaited<ReturnType<typeof startFakeRegistry>>> = []

afterEach(() => {
  for (const registry of registries) registry.stop()
  registries = []
})

function makeSkillZip(content: string): Uint8Array {
  return zipSync({ 'SKILL.md': strToU8(content) })
}

async function exists(path: string): Promise<boolean> {
  try {
    await access(path)
    return true
  } catch {
    return false
  }
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

    const inventoryPath = join(env.home, '.skillhub', 'inventory.json')
    const metadataPath = join(rootDir, 'skillhub-registry', '.skillhub', 'metadata.json')
    const inventoryBeforeCheck = await readFile(inventoryPath, 'utf-8')
    const metadataBeforeCheck = await readFile(metadataPath, 'utf-8')
    const checked = await runCli([
      'upgrade', '@global/skillhub-registry', '--registry', registry.url, '--dir', rootDir, '--check', '--json'
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
    expect(await readFile(inventoryPath, 'utf-8')).toBe(inventoryBeforeCheck)
    expect(await readFile(metadataPath, 'utf-8')).toBe(metadataBeforeCheck)

    const checkedAgain = await runCli([
      'upgrade', '@global/skillhub-registry', '--registry', registry.url, '--dir', rootDir, '--check', '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(checkedAgain.exitCode).toBe(0)
    expect(checkedAgain.stdout).toBe(checked.stdout)
    expect(await readFile(inventoryPath, 'utf-8')).toBe(inventoryBeforeCheck)
    expect(await readFile(metadataPath, 'utf-8')).toBe(metadataBeforeCheck)
    expect(registry.received.downloads).toBe(1)

    const upgraded = await runCli([
      'upgrade', '@global/skillhub-registry', '--registry', registry.url, '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(upgraded.exitCode).toBe(0)
    expect(JSON.parse(upgraded.stdout).items[0].action).toBe('upgraded')
    expect(await readFile(join(rootDir, 'skillhub-registry', 'SKILL.md'), 'utf-8')).toBe('# v2')
    expect(registry.received.downloads).toBe(2)

    const metadata = JSON.parse(await readFile(metadataPath, 'utf-8'))
    expect(metadata).toMatchObject({ schemaVersion: 1, version: '1.1.0', versionId: 2, fingerprint: 'fp-v2' })
    expect(Object.keys(metadata.files)).toContain('SKILL.md')
    const inventory = JSON.parse(await readFile(inventoryPath, 'utf-8'))
    expect(inventory.items[0]).toMatchObject({ version: '1.1.0', fingerprint: 'fp-v2' })
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
      'upgrade', '@team/code-review', '--registry', registry.url, '--agent', 'custom', '--check', '--json'
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

  test('a local edit made after planning is rechecked before replacement', async () => {
    const env = await createTempHome()
    const skill = {
      namespace: 'global',
      slug: 'late-edit',
      version: '1.0.0',
      versionId: 1,
      fingerprint: 'fp-v1',
      zipBytes: makeSkillZip('# v1')
    }
    const registry = await startFakeRegistry({ skills: [skill] })
    registries.push(registry)
    const rootDir = join(env.cwd, 'skills')
    await mkdir(rootDir, { recursive: true })
    await runCli(['install', '@global/late-edit', '--dir', rootDir, '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    }, { cwd: env.cwd })

    skill.version = '1.1.0'
    skill.versionId = 2
    skill.fingerprint = 'fp-v2'
    skill.zipBytes = makeSkillZip('# v2')
    const tokenForRegistry = async () => undefined
    const plan = await planSkillUpgrades({
      coordinates: ['@global/late-edit'],
      registry: registry.url,
      force: false,
      home: env.home,
      tokenForRegistry
    })
    await writeFile(join(rootDir, 'late-edit', 'SKILL.md'), '# edited after planning')

    const result = await executeSkillUpgradePlan(plan, { home: env.home, tokenForRegistry })
    expect(result.items[0]).toMatchObject({ action: 'failed' })
    expect(result.items[0]?.reason).toContain('local changes detected after upgrade planning')
    expect(await readFile(join(rootDir, 'late-edit', 'SKILL.md'), 'utf-8'))
      .toBe('# edited after planning')
    const inventory = JSON.parse(await readFile(join(env.home, '.skillhub', 'inventory.json'), 'utf-8'))
    expect(inventory.items[0]).toMatchObject({ version: '1.0.0', fingerprint: 'fp-v1' })
  })

  test('a target removed after planning is not recreated by upgrade', async () => {
    const env = await createTempHome()
    const skill = {
      namespace: 'global',
      slug: 'removed-late',
      version: '1.0.0',
      versionId: 1,
      fingerprint: 'fp-v1',
      zipBytes: makeSkillZip('# v1')
    }
    const registry = await startFakeRegistry({ skills: [skill] })
    registries.push(registry)
    const rootDir = join(env.cwd, 'skills')
    await mkdir(rootDir, { recursive: true })
    await runCli(['install', '@global/removed-late', '--dir', rootDir, '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    }, { cwd: env.cwd })

    skill.version = '1.1.0'
    skill.versionId = 2
    skill.fingerprint = 'fp-v2'
    skill.zipBytes = makeSkillZip('# v2')
    const tokenForRegistry = async () => undefined
    const plan = await planSkillUpgrades({
      coordinates: ['@global/removed-late'],
      registry: registry.url,
      force: false,
      home: env.home,
      tokenForRegistry
    })
    const skillDir = join(rootDir, 'removed-late')
    await rm(skillDir, { recursive: true })

    const result = await executeSkillUpgradePlan(plan, { home: env.home, tokenForRegistry })
    expect(result.items[0]).toMatchObject({ action: 'failed' })
    expect(result.items[0]?.reason).toContain('installed target disappeared before upgrade commit')
    expect(await exists(skillDir)).toBe(false)
    const inventory = JSON.parse(await readFile(join(env.home, '.skillhub', 'inventory.json'), 'utf-8'))
    expect(inventory.items[0]).toMatchObject({ version: '1.0.0', fingerprint: 'fp-v1' })
  })

  test('new installs persist absolute targets and legacy relative targets are blocked safely', async () => {
    const env = await createTempHome()
    const skill = {
      namespace: 'global',
      slug: 'portable',
      version: '1.0.0',
      versionId: 1,
      fingerprint: 'fp-v1',
      zipBytes: makeSkillZip('# v1')
    }
    const registry = await startFakeRegistry({ skills: [skill] })
    registries.push(registry)
    const installed = await runCli([
      'install', '@global/portable', '--dir', 'skills', '--registry', registry.url
    ], { HOME: env.home, USERPROFILE: env.home }, { cwd: env.cwd })
    expect(installed.exitCode).toBe(0)

    const inventoryPath = join(env.home, '.skillhub', 'inventory.json')
    const inventory = JSON.parse(await readFile(inventoryPath, 'utf-8'))
    expect(isAbsolute(inventory.items[0].targets[0].rootDir)).toBe(true)
    expect(inventory.items[0].targets[0].installDir)
      .toBe(join(inventory.items[0].targets[0].rootDir, 'portable'))
    expect(await realpath(inventory.items[0].targets[0].rootDir))
      .toBe(await realpath(join(env.cwd, 'skills')))
    expect(await realpath(inventory.items[0].targets[0].installDir))
      .toBe(await realpath(join(env.cwd, 'skills', 'portable')))

    inventory.items[0].targets[0].rootDir = 'skills'
    inventory.items[0].targets[0].installDir = join('skills', 'portable')
    await writeFile(inventoryPath, JSON.stringify(inventory))
    skill.version = '1.1.0'
    skill.versionId = 2
    skill.fingerprint = 'fp-v2'
    skill.zipBytes = makeSkillZip('# v2')

    const otherCwd = join(env.cwd, 'other')
    await mkdir(otherCwd, { recursive: true })
    const result = await runCli([
      'upgrade', '@global/portable', '--registry', registry.url, '--check', '--json'
    ], { HOME: env.home, USERPROFILE: env.home }, { cwd: otherCwd })
    expect(result.exitCode).toBe(6)
    expect(JSON.parse(result.stdout).items[0].reason).toContain('legacy relative target path')
    expect(await readFile(join(env.cwd, 'skills', 'portable', 'SKILL.md'), 'utf-8')).toBe('# v1')
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

    const fullCoordinate = await runCli(['upgrade', '@team-a/demo', '--check'], {
      HOME: env.home,
      USERPROFILE: env.home
    })
    expect(fullCoordinate.exitCode).toBe(0)
    expect(fullCoordinate.stdout).toContain('@team-a/demo')
    expect(fullCoordinate.stdout).not.toContain('@team-b/demo')

    const noNamespaceMatch = await runCli(['upgrade', 'demo', '--namespace', 'missing', '--check'], {
      HOME: env.home,
      USERPROFILE: env.home
    })
    expect(noNamespaceMatch.exitCode).toBe(5)
    expect(noNamespaceMatch.stderr).toContain('not installed')
  })

  test('target filters select deterministically and missing matches never install', async () => {
    const env = await createTempHome()
    const skill = { namespace: 'global', slug: 'filtered', version: '1.0.0', fingerprint: 'fp', zipBytes: makeSkillZip('# v1') }
    const registry = await startFakeRegistry({ skills: [skill] })
    registries.push(registry)
    const rootDir = join(env.cwd, 'skills')
    await mkdir(rootDir, { recursive: true })
    await runCli(['install', '@global/filtered', '--dir', rootDir, '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    for (const args of [
      ['--dir', rootDir],
      ['--agent', 'custom'],
      ['--namespace', 'global'],
      ['--registry', registry.url]
    ]) {
      const result = await runCli(['upgrade', 'filtered', ...args, '--force', '--check'], {
        HOME: env.home,
        USERPROFILE: env.home
      })
      expect(result.exitCode).toBe(0)
    }

    for (const args of [
      ['--dir', join(env.cwd, 'missing')],
      ['--agent', 'codex'],
      ['--namespace', 'missing'],
      ['--registry', 'http://unmatched.invalid']
    ]) {
      const result = await runCli(['upgrade', 'filtered', ...args, '--check'], {
        HOME: env.home,
        USERPROFILE: env.home
      })
      expect(result.exitCode).toBe(5)
      expect(result.stderr).toContain('not installed')
    }
    expect(registry.received.downloads).toBe(1)
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
    expect(registry.received.resolves).toBe(1)
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
    expect(registry.received.resolves).toBe(2)
    expect(registry.received.downloads).toBe(2)
    expect(await readFile(join(env.home, '.codex', 'skills', 'shared', 'SKILL.md'), 'utf-8')).toBe('# v2')
    expect(await readFile(join(env.home, '.claude', 'skills', 'shared', 'SKILL.md'), 'utf-8')).toBe('# v2')
    const inventory = JSON.parse(await readFile(join(env.home, '.skillhub', 'inventory.json'), 'utf-8'))
    expect(inventory.items[0]).toMatchObject({ version: '1.1.0', fingerprint: 'fp-v2' })
    expect(inventory.items[0].targets).toHaveLength(2)

    const listed = await runCli(['list', '--json', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })
    expect(listed.exitCode).toBe(0)
    expect(JSON.parse(listed.stdout).items[0]).toMatchObject({ version: '1.1.0' })
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

  test('keeps local files when resolve is unavailable or same-version content drifts', async () => {
    const env = await createTempHome()
    const failures: { resolve?: 'server_error' } = {}
    const skill = { namespace: 'global', slug: 'resilient', version: '1.0.0', fingerprint: 'fp-v1', zipBytes: makeSkillZip('# v1') }
    const registry = await startFakeRegistry({ skills: [skill], failures })
    registries.push(registry)
    const rootDir = join(env.cwd, 'skills')
    await mkdir(rootDir, { recursive: true })
    await runCli(['install', '@global/resilient', '--dir', rootDir, '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    failures.resolve = 'server_error'
    const unavailable = await runCli([
      'upgrade', '@global/resilient', '--registry', registry.url, '--force', '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(unavailable.exitCode).toBe(6)
    expect(JSON.parse(unavailable.stdout).items[0].action).toBe('blocked')
    expect(await readFile(join(rootDir, 'resilient', 'SKILL.md'), 'utf-8')).toBe('# v1')

    delete failures.resolve
    skill.fingerprint = 'fp-drift'
    skill.zipBytes = makeSkillZip('# changed without version bump')
    const drifted = await runCli([
      'upgrade', '@global/resilient', '--registry', registry.url, '--force', '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(drifted.exitCode).toBe(6)
    expect(JSON.parse(drifted.stdout).items[0].reason).toContain('without a newer version')
    expect(await readFile(join(rootDir, 'resilient', 'SKILL.md'), 'utf-8')).toBe('# v1')
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

  test('a runtime batch failure reports committed, failed, and unattempted skills', async () => {
    const env = await createTempHome()
    const first = { namespace: 'global', slug: 'first', version: '1.0.0', fingerprint: 'first-v1', zipBytes: makeSkillZip('# first v1') }
    const second = { namespace: 'global', slug: 'second', version: '1.0.0', fingerprint: 'second-v1', zipBytes: makeSkillZip('# second v1') }
    const third = { namespace: 'global', slug: 'third', version: '1.0.0', fingerprint: 'third-v1', zipBytes: makeSkillZip('# third v1') }
    const registry = await startFakeRegistry({ skills: [first, second, third] })
    registries.push(registry)
    const rootDir = join(env.cwd, 'skills')
    await mkdir(rootDir, { recursive: true })
    for (const slug of ['first', 'second', 'third']) {
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
    second.zipBytes = strToU8('not a zip archive')
    third.version = '1.1.0'
    third.fingerprint = 'third-v2'
    third.zipBytes = makeSkillZip('# third v2')

    const result = await runCli([
      'upgrade', '@global/first', '@global/second', '@global/third',
      '--registry', registry.url, '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(result.exitCode).toBe(1)
    const output = JSON.parse(result.stdout)
    expect(output.summary).toEqual({ upgraded: 1, unchanged: 0, failed: 1, notAttempted: 1 })
    expect(output.items.map((item: { action: string }) => item.action))
      .toEqual(['upgraded', 'failed', 'not-attempted'])
    expect(await readFile(join(rootDir, 'first', 'SKILL.md'), 'utf-8')).toBe('# first v2')
    expect(await readFile(join(rootDir, 'second', 'SKILL.md'), 'utf-8')).toBe('# second v1')
    expect(await readFile(join(rootDir, 'third', 'SKILL.md'), 'utf-8')).toBe('# third v1')
    const inventory = JSON.parse(await readFile(join(env.home, '.skillhub', 'inventory.json'), 'utf-8'))
    expect(inventory.items.find((item: { slug: string }) => item.slug === 'first').version).toBe('1.1.0')
    expect(inventory.items.find((item: { slug: string }) => item.slug === 'second').version).toBe('1.0.0')
    expect(inventory.items.find((item: { slug: string }) => item.slug === 'third').version).toBe('1.0.0')
    expect(registry.received.downloads).toBe(5)
  })

  test('a committed upgrade keeps success and renders a post-commit warning', async () => {
    const env = await createTempHome()
    const skill = { namespace: 'global', slug: 'warned', version: '1.0.0', fingerprint: 'v1', zipBytes: makeSkillZip('# v1') }
    const registry = await startFakeRegistry({ skills: [skill] })
    registries.push(registry)
    const rootDir = join(env.cwd, 'skills')
    await mkdir(rootDir, { recursive: true })
    await runCli(['install', '@global/warned', '--dir', rootDir, '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })
    skill.version = '1.1.0'
    skill.fingerprint = 'v2'
    skill.zipBytes = makeSkillZip('# v2')
    const tokenForRegistry = async () => undefined
    const plan = await planSkillUpgrades({
      coordinates: ['@global/warned'],
      registry: registry.url,
      force: false,
      home: env.home,
      tokenForRegistry
    })

    const result = await executeSkillUpgradePlan(plan, {
      home: env.home,
      tokenForRegistry,
      installSkillFn: options => installSkill({
        ...options,
        acquireTargetLock: async () => async () => { throw new Error('simulated release failure') }
      })
    })

    expect(result).toMatchObject({ upgraded: 1, failed: 0 })
    expect(result.items[0]).toMatchObject({ action: 'upgraded' })
    expect(result.items[0]?.warnings).toEqual(['target lock cleanup failed: simulated release failure'])
    expect(JSON.parse(renderUpgradeResult(plan, result, true)).items[0].warnings).toHaveLength(1)
    expect(renderUpgradeResult(plan, result, false)).toContain('upgraded')
    expect(renderUpgradeResult(plan, result, false)).toContain('[warning: target lock cleanup failed')
    expect(await readFile(join(rootDir, 'warned', 'SKILL.md'), 'utf-8')).toBe('# v2')
    const inventory = JSON.parse(await readFile(join(env.home, '.skillhub', 'inventory.json'), 'utf-8'))
    expect(inventory.items[0]).toMatchObject({ version: '1.1.0', fingerprint: 'v2' })
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
    const migratedMetadata = JSON.parse(await readFile(metadataPath, 'utf-8'))
    expect(migratedMetadata.schemaVersion).toBe(1)
    expect(Object.keys(migratedMetadata.files)).toContain('SKILL.md')
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

  test('accepts exactly fifty explicitly installed coordinates', async () => {
    const env = await createTempHome()
    const skills = Array.from({ length: 50 }, (_, index) => ({
      namespace: 'global',
      slug: `skill-${index}`,
      version: '1.0.0',
      fingerprint: `fp-${index}`,
      zipBytes: makeSkillZip(`# skill ${index}`)
    }))
    const registry = await startFakeRegistry({ skills })
    registries.push(registry)
    const rootDir = join(env.cwd, 'skills')
    await mkdir(rootDir, { recursive: true })
    const items = []
    for (const skill of skills) {
      const skillDir = join(rootDir, skill.slug)
      await mkdir(join(skillDir, '.skillhub'), { recursive: true })
      await writeFile(join(skillDir, 'SKILL.md'), `# ${skill.slug}`)
      await writeFile(join(skillDir, '.skillhub', 'metadata.json'), JSON.stringify({
        registry: registry.url,
        namespace: skill.namespace,
        slug: skill.slug,
        version: skill.version,
        fingerprint: skill.fingerprint,
        source: 'skillhub'
      }))
      items.push({
        registry: registry.url,
        namespace: skill.namespace,
        slug: skill.slug,
        version: skill.version,
        fingerprint: skill.fingerprint,
        targets: [{
          agent: 'custom', rootDir, installDir: skillDir, installedAt: '2026-09-01T00:00:00Z'
        }]
      })
    }
    await mkdir(join(env.home, '.skillhub'), { recursive: true })
    await writeFile(join(env.home, '.skillhub', 'inventory.json'), JSON.stringify({ items }))

    const result = await runCli([
      'upgrade', ...skills.map(skill => `@global/${skill.slug}`),
      '--registry', registry.url, '--force', '--check', '--json'
    ], { HOME: env.home, USERPROFILE: env.home })
    expect(result.exitCode).toBe(0)
    expect(JSON.parse(result.stdout).summary).toMatchObject({ unchanged: 50, blocked: 0 })
    expect(registry.received.downloads).toBe(0)
  })
})
