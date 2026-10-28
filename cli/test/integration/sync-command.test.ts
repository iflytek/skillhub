import { InventoryStore } from '../../src/stores/inventory-store'
import { createHash } from 'node:crypto'
import { mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { strToU8, zipSync } from 'fflate'
import { describe, expect, test } from 'bun:test'
import { startFakeRegistry, type FakeSkill } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'
import { createTempHome } from '../helpers/temp-env'
import { SkillHubClient } from '../../src/clients/skillhub-client'
import { pullNamespace } from '../../src/services/sync-service'
import { renderPullResult } from '../../src/commands/sync'

function makeSkill(body: string): { zipBytes: Uint8Array; fingerprint: string } {
  const content = strToU8(body)
  const fileHash = createHash('sha256').update(content).digest('hex')
  const fingerprint = `sha256:${createHash('sha256').update(`SKILL.md:${fileHash}\n`).digest('hex')}`
  return { zipBytes: zipSync({ 'SKILL.md': content }), fingerprint }
}

async function makeLocalSkill(rootDir: string, slug = 'demo'): Promise<string> {
  const skillDir = join(rootDir, slug)
  await mkdir(skillDir, { recursive: true })
  await writeFile(join(skillDir, 'SKILL.md'), `---\nname: ${slug}\ndescription: Demo\nversion: 1.0.0\n---\n`)
  return skillDir
}

describe('sync command', () => {
  test('install and sync preserve a registry sub-path for application-relative downloads', async () => {
    const env = await createTempHome()
    const registry = await startFakeRegistry({
      token: 'prefix-token', basePath: '/skillhub', applicationRelativeDownloads: true,
      skills: [{ namespace: 'team-a', slug: 'demo', ...makeSkill('# prefix download\n') }]
    })
    const shared = ['--registry', registry.url, '--token', 'prefix-token', '--json']
    try {
      const installDir = join(env.cwd, 'install')
      const installed = await runCli(['install', '@team-a/demo', '--dir', installDir, ...shared], { HOME: env.home }, { cwd: env.cwd })
      expect(installed.exitCode).toBe(0)
      expect(await readFile(join(installDir, 'demo', 'SKILL.md'), 'utf8')).toBe('# prefix download\n')
      const workspace = join(env.cwd, 'workspace')
      const pulled = await runCli(['sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', workspace, ...shared], { HOME: env.home }, { cwd: env.cwd })
      expect(pulled.exitCode).toBe(0)
      expect(await readFile(join(workspace, 'demo', 'SKILL.md'), 'utf8')).toBe('# prefix download\n')
      const status = await runCli(['sync', 'status', '--namespace', 'team-a', '--dir', workspace, ...shared], { HOME: env.home }, { cwd: env.cwd })
      expect(status.exitCode).toBe(0)
      expect(JSON.parse(status.stdout).items).toMatchObject([{ slug: 'demo', status: 'up-to-date' }])
      expect(registry.received.downloads).toBe(2)
    } finally {
      registry.stop()
    }
  })

  test('mutating pull service rejects an empty selection before reading the namespace', async () => {
    const registry = await startFakeRegistry({
      token: 'token',
      skills: [{ namespace: 'team-a', slug: 'demo', ...makeSkill('# demo\n') }]
    })

    try {
      await expect(pullNamespace({
        client: new SkillHubClient(registry.url, 'token'),
        registry: registry.url,
        token: 'token',
        namespace: 'team-a',
        rootDir: '/unused',
        check: false,
        prune: false,
        force: false,
        selectedSlugs: []
      })).rejects.toThrow('requires at least one selected skill')
      expect(registry.received.namespaceRequests).toBe(0)
    } finally {
      registry.stop()
    }
  })

  test('all sync actions reject a missing or global namespace before a registry request', async () => {
    const env = await createTempHome()
    const registry = await startFakeRegistry({ token: 'token' })
    const invocations = [
      ['pull', '--check'],
      ['status'],
      ['diff'],
      ['push', '--all']
    ]

    try {
      for (const invocation of invocations) {
        for (const namespaceArgs of [[], ['--namespace', 'global']]) {
          const result = await runCli([
            'sync', ...invocation, ...namespaceArgs,
            '--registry', registry.url, '--token', 'token', '--json'
          ], { HOME: env.home }, { cwd: env.cwd })
          expect(result.exitCode).toBe(5)
          expect(result.stdout).toBe('')
          expect(JSON.parse(result.stderr).message).toMatch(/namespace|required|global/)
        }
      }
      expect(registry.received.namespaceRequests).toBe(0)
      expect(registry.received.publish).toBeNull()
    } finally {
      registry.stop()
    }
  })

  test('non-pull sync actions reject the pull-only --skill option', async () => {
    const result = await runCli([
      'sync', 'status', '--namespace', 'team-a', '--skill', 'demo', '--json'
    ])

    expect(result.exitCode).toBe(5)
    expect(result.stdout).toBe('')
    expect(JSON.parse(result.stderr).message).toBe('--skill is only valid with sync pull')
  })

  test('non-interactive JSON pull without --skill returns usage error without prompting or requesting', async () => {
    const env = await createTempHome()
    const registry = await startFakeRegistry({
      token: 'token',
      skills: [{ namespace: 'team-a', slug: 'demo', ...makeSkill('# demo\n') }]
    })

    try {
      const result = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(result.exitCode).toBe(5)
      expect(result.stdout).toBe('')
      expect(JSON.parse(result.stderr)).toMatchObject({
        ok: false,
        message: expect.stringContaining('requires at least one --skill'),
        exitCode: 5
      })
      expect(registry.received.namespaceRequests).toBe(0)
    } finally {
      registry.stop()
    }
  })

  test('sync preserves server membership errors and request IDs', async () => {
    const env = await createTempHome()
    const registry = await startFakeRegistry({
      token: 'token',
      failures: { namespaceSync: 'forbidden' }
    })

    try {
      const result = await runCli([
        'sync', 'status', '--namespace', 'team-a',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(result.exitCode).toBe(2)
      expect(result.stdout).toBe('')
      expect(JSON.parse(result.stderr)).toMatchObject({
        ok: false,
        message: 'API token is missing required scope: skill:publish',
        details: { requestId: 'req-test-forbidden' }
      })
    } finally {
      registry.stop()
    }
  })

  test('pull --check inspects the whole namespace without writing files or requiring --skill', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const registry = await startFakeRegistry({
      token: 'token',
      skills: [
        { namespace: 'team-a', slug: 'first', ...makeSkill('# first\n') },
        { namespace: 'team-a', slug: 'second', ...makeSkill('# second\n') }
      ]
    })

    try {
      const result = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir, '--check',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(result.exitCode).toBe(0)
      expect(JSON.parse(result.stdout)).toMatchObject({ ok: true, check: true })
      expect(JSON.parse(result.stdout).entries).toHaveLength(2)
      expect(await Bun.file(join(skillsDir, 'first', 'SKILL.md')).exists()).toBe(false)
      expect(await Bun.file(join(skillsDir, '.skillhub', 'namespace-sync.json')).exists()).toBe(false)
    } finally {
      registry.stop()
    }
  })

  test('pull installs only explicitly selected skills', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const registry = await startFakeRegistry({
      token: 'token',
      skills: [
        { namespace: 'team-a', slug: 'first', ...makeSkill('# first\n') },
        { namespace: 'team-a', slug: 'second', ...makeSkill('# second\n') }
      ]
    })

    try {
      const result = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'second', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(result.exitCode).toBe(0)
      expect(JSON.parse(result.stdout).actions).toEqual([{ slug: 'second', action: 'installed' }])
      expect(await Bun.file(join(skillsDir, 'first')).exists()).toBe(false)
      expect(await Bun.file(join(skillsDir, 'second', 'SKILL.md')).exists()).toBe(true)
      const workspaceState = JSON.parse(
        await readFile(join(skillsDir, '.skillhub', 'namespace-sync.json'), 'utf8')
      )
      expect(Object.keys(workspaceState.skills)).toEqual(['second'])
    } finally {
      registry.stop()
    }
  })

  test('pull reads every cursor page without duplicates and still writes only the selected skill', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const registry = await startFakeRegistry({
      token: 'token',
      namespacePageSize: 1,
      skills: ['first', 'second', 'third'].map(slug => ({
        namespace: 'team-a',
        slug,
        ...makeSkill(`# ${slug}\n`)
      }))
    })

    try {
      const result = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'third', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      const output = JSON.parse(result.stdout)

      expect(result.exitCode).toBe(0)
      expect(registry.received.namespaceRequests).toBe(3)
      expect(output.entries.map((item: { slug: string }) => item.slug)).toEqual(['first', 'second', 'third'])
      expect(new Set(output.entries.map((item: { slug: string }) => item.slug)).size).toBe(3)
      expect(output.actions).toEqual([{ slug: 'third', action: 'installed' }])
      expect(await Bun.file(join(skillsDir, 'first')).exists()).toBe(false)
      expect(await Bun.file(join(skillsDir, 'second')).exists()).toBe(false)
      expect(await Bun.file(join(skillsDir, 'third', 'SKILL.md')).exists()).toBe(true)
    } finally {
      registry.stop()
    }
  })

  test('pull propagates committed install warnings', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const fixture = makeSkill('---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n')
    const registry = await startFakeRegistry({
      token: 'token',
      skills: [{ namespace: 'team-a', slug: 'demo', ...fixture }]
    })

    try {
      const result = await pullNamespace({
        client: new SkillHubClient(registry.url, 'token'),
        registry: registry.url,
        token: 'token',
        namespace: 'team-a',
        rootDir: skillsDir,
        check: false,
        prune: false,
        force: false,
        selectedSlugs: ['demo'],
        installSkillFn: async () => ({
          installed: [{ agent: 'workspace', dir: join(skillsDir, 'demo') }],
          warnings: ['target lock cleanup failed: simulated release failure']
        })
      })

      expect(result.actions).toEqual([{ slug: 'demo', action: 'installed' }])
      expect(result.warnings).toEqual([{
        slug: 'demo',
        message: 'target lock cleanup failed: simulated release failure'
      }])
      expect(JSON.parse(renderPullResult(result, true, false)).warnings).toEqual(result.warnings)
      expect(renderPullResult(result, false, false)).toContain('warning    demo: target lock cleanup failed')
    } finally {
      registry.stop()
    }
  })

  test('pull installs a namespace incrementally and writes workspace metadata', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const first = makeSkill('---\nname: first\ndescription: First\nversion: 1.0.0\n---\n')
    const second = makeSkill('---\nname: second\ndescription: Second\nversion: 1.0.0\n---\n')
    const registry = await startFakeRegistry({
      token: 'token',
      skills: [
        { namespace: 'team-a', slug: 'first', ...first },
        { namespace: 'team-a', slug: 'second', ...second }
      ]
    })

    try {
      const pulled = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'first', '--skill', 'second', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(pulled.exitCode).toBe(0)
      expect(JSON.parse(pulled.stdout).actions).toHaveLength(2)
      const metadata = JSON.parse(await readFile(join(skillsDir, 'first', '.skillhub', 'metadata.json'), 'utf8'))
      expect(metadata).toMatchObject({
        source: 'skillhub', namespace: 'team-a', slug: 'first', fingerprint: first.fingerprint
      })
      expect(await readFile(join(skillsDir, '.skillhub', 'namespace-sync.json'), 'utf8')).toContain('team-a')

      const secondPull = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'first', '--skill', 'second', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(secondPull.exitCode).toBe(0)
      expect(JSON.parse(secondPull.stdout).actions).toHaveLength(0)
      expect(JSON.parse(secondPull.stdout).entries.every((item: { status: string }) => item.status === 'up-to-date')).toBe(true)
    } finally {
      registry.stop()
    }
  })

  test('status detects local changes and pull does not overwrite without force', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const remoteBody = '---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n'
    const fixture = makeSkill(remoteBody)
    const registry = await startFakeRegistry({
      token: 'token',
      skills: [{ namespace: 'team-a', slug: 'demo', ...fixture }]
    })

    try {
      await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })
      await writeFile(join(skillsDir, 'demo', 'SKILL.md'), '# local change\n')

      const status = await runCli([
        'sync', 'status', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(JSON.parse(status.stdout).items[0].status).toBe('local-changed')

      const pull = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(pull.exitCode).toBe(1)
      expect(await readFile(join(skillsDir, 'demo', 'SKILL.md'), 'utf8')).toBe('# local change\n')

      const forced = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir, '--force',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(forced.exitCode).toBe(0)
      expect(JSON.parse(forced.stdout).actions).toEqual([{ slug: 'demo', action: 'updated' }])
      expect(await readFile(join(skillsDir, 'demo', 'SKILL.md'), 'utf8')).toBe(remoteBody)
    } finally {
      registry.stop()
    }
  })

  test('reports a newer remote version as update-available even when content is unchanged', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const fixture = makeSkill('---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n')
    const skill: FakeSkill = {
      namespace: 'team-a',
      slug: 'demo',
      version: '1.0.0',
      versionId: 1,
      ...fixture
    }
    const registry = await startFakeRegistry({ token: 'token', skills: [skill] })

    try {
      await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })
      skill.version = '1.1.0'
      skill.versionId = 2

      const status = await runCli([
        'sync', 'status', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(JSON.parse(status.stdout).items[0]).toMatchObject({
        status: 'update-available',
        localVersion: '1.0.0',
        remoteVersion: '1.1.0'
      })
    } finally {
      registry.stop()
    }
  })

  test('blocks same-version remote content drift even with force', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const original = makeSkill('# original\n')
    const changed = makeSkill('# changed without a version bump\n')
    const skill: FakeSkill = {
      namespace: 'team-a',
      slug: 'demo',
      version: '1.0.0',
      versionId: 1,
      ...original
    }
    const registry = await startFakeRegistry({ token: 'token', skills: [skill] })

    try {
      await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })
      skill.fingerprint = changed.fingerprint
      skill.zipBytes = changed.zipBytes

      const status = await runCli([
        'sync', 'status', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(JSON.parse(status.stdout).items[0]).toMatchObject({
        status: 'blocked',
        reason: 'remote content changed without a newer version; use explicit install after verifying the release'
      })

      const checked = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir, '--check',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(checked.exitCode).toBe(6)
      expect(JSON.parse(checked.stdout)).toMatchObject({ ok: false, check: true })

      const pulled = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir, '--force',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(pulled.exitCode).toBe(6)
      expect(await readFile(join(skillsDir, 'demo', 'SKILL.md'), 'utf8')).toBe('# original\n')
    } finally {
      registry.stop()
    }
  })

  test('blocks automatic downgrade even when remote content is unchanged', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const fixture = makeSkill('# stable content\n')
    const skill: FakeSkill = {
      namespace: 'team-a',
      slug: 'demo',
      version: '2.0.0',
      versionId: 2,
      ...fixture
    }
    const registry = await startFakeRegistry({ token: 'token', skills: [skill] })

    try {
      await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })
      skill.version = '1.0.0'
      skill.versionId = 1

      const pulled = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir, '--force',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(pulled.exitCode).toBe(6)
      expect(JSON.parse(pulled.stdout).entries[0]).toMatchObject({
        status: 'blocked',
        localVersion: '2.0.0',
        remoteVersion: '1.0.0',
        reason: 'remote version is older than the installed version; local files were kept'
      })
      expect(await readFile(join(skillsDir, 'demo', 'SKILL.md'), 'utf8')).toBe('# stable content\n')
    } finally {
      registry.stop()
    }
  })

  test('blocks sync when local and remote versions cannot be ordered', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const fixture = makeSkill('# stable content\n')
    const skill: FakeSkill = {
      namespace: 'team-a',
      slug: 'demo',
      version: 'release-a',
      versionId: 1,
      ...fixture
    }
    const registry = await startFakeRegistry({ token: 'token', skills: [skill] })

    try {
      await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })
      skill.version = 'release-b'
      skill.versionId = 2

      const pulled = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir, '--force',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(pulled.exitCode).toBe(6)
      expect(JSON.parse(pulled.stdout).entries[0]).toMatchObject({
        status: 'blocked',
        localVersion: 'release-a',
        remoteVersion: 'release-b',
        reason: 'cannot determine version order; use explicit install after verifying the release'
      })
    } finally {
      registry.stop()
    }
  })

  test('hard remote guards cannot be bypassed by local changes and force', async () => {
    const original = makeSkill('# original\n')
    const variants = [
      {
        name: 'downgrade',
        initialVersion: '2.0.0',
        remoteVersion: '1.0.0',
        remote: original,
        reason: 'remote version is older than the installed version; local files were kept'
      },
      {
        name: 'same-version drift',
        initialVersion: '1.0.0',
        remoteVersion: '1.0.0',
        remote: makeSkill('# changed without a version bump\n'),
        reason: 'remote content changed without a newer version; use explicit install after verifying the release'
      },
      {
        name: 'unknown version order',
        initialVersion: 'release-a',
        remoteVersion: 'release-b',
        remote: original,
        reason: 'cannot determine version order; use explicit install after verifying the release'
      }
    ]

    for (const variant of variants) {
      const env = await createTempHome()
      const skillsDir = join(env.cwd, 'team-skills')
      const skill: FakeSkill = {
        namespace: 'team-a',
        slug: 'demo',
        version: variant.initialVersion,
        versionId: 1,
        ...original
      }
      const registry = await startFakeRegistry({ token: 'token', skills: [skill] })
      try {
        await runCli([
          'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir,
          '--registry', registry.url, '--token', 'token'
        ], { HOME: env.home }, { cwd: env.cwd })
        await writeFile(join(skillsDir, 'demo', 'SKILL.md'), `# local edit before ${variant.name}\n`)
        skill.version = variant.remoteVersion
        skill.versionId = 2
        skill.fingerprint = variant.remote.fingerprint
        skill.zipBytes = variant.remote.zipBytes

        const pulled = await runCli([
          'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir, '--force',
          '--registry', registry.url, '--token', 'token', '--json'
        ], { HOME: env.home }, { cwd: env.cwd })
        const output = JSON.parse(pulled.stdout)
        expect(pulled.exitCode, variant.name).toBe(6)
        expect(output.entries[0], variant.name).toMatchObject({ status: 'blocked', reason: variant.reason })
        expect(output.entries[0].changedFiles, variant.name).toEqual(['SKILL.md'])
        expect(output.actions, variant.name).toEqual([])
        expect(await readFile(join(skillsDir, 'demo', 'SKILL.md'), 'utf8'))
          .toBe(`# local edit before ${variant.name}\n`)
      } finally {
        registry.stop()
      }
    }
  })

  test('an unselected blocked skill does not change the selected skill failure classification', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const chosen = makeSkill('# chosen\n')
    const blockedOriginal = makeSkill('# blocked original\n')
    const blockedChanged = makeSkill('# blocked changed\n')
    const skills: FakeSkill[] = [
      { namespace: 'team-a', slug: 'chosen', version: '1.0.0', ...chosen },
      { namespace: 'team-a', slug: 'blocked', version: '1.0.0', ...blockedOriginal }
    ]
    const registry = await startFakeRegistry({ token: 'token', skills })

    try {
      await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'chosen', '--skill', 'blocked', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })
      await writeFile(join(skillsDir, 'chosen', 'SKILL.md'), '# chosen local edit\n')
      skills[1]!.fingerprint = blockedChanged.fingerprint
      skills[1]!.zipBytes = blockedChanged.zipBytes

      const result = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'chosen', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(result.exitCode).toBe(1)
      expect(JSON.parse(result.stdout).failures).toEqual([{
        slug: 'chosen',
        message: 'local changes detected; pass --force to overwrite'
      }])
      expect(JSON.parse(result.stderr).message).toBe('namespace sync completed with failures')
      expect(await readFile(join(skillsDir, 'chosen', 'SKILL.md'), 'utf8')).toBe('# chosen local edit\n')
      expect(await readFile(join(skillsDir, 'blocked', 'SKILL.md'), 'utf8')).toBe('# blocked original\n')
    } finally {
      registry.stop()
    }
  })

  test('prune removes only unchanged managed orphan skills', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const fixture = makeSkill('---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n')
    const skills: FakeSkill[] = [
      { namespace: 'team-a', slug: 'demo', ...fixture },
      { namespace: 'team-a', slug: 'keep', ...makeSkill('# keep\n') }
    ]
    const registry = await startFakeRegistry({ token: 'token', skills })

    try {
      await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--skill', 'keep', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })
      skills.splice(0, skills.length)

      const pruned = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir, '--prune',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(pruned.exitCode).toBe(0)
      expect(JSON.parse(pruned.stdout).actions).toContainEqual({ slug: 'demo', action: 'pruned' })
      expect(await Bun.file(join(skillsDir, 'demo')).exists()).toBe(false)
      expect(await Bun.file(join(skillsDir, 'keep', 'SKILL.md')).exists()).toBe(true)
    } finally {
      registry.stop()
    }
  })

  test('force prune refuses Suite-owned orphans without deleting their files', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const fixture = makeSkill('---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n')
    const skills: FakeSkill[] = [
      { namespace: 'team-a', slug: 'demo', ...fixture },
      { namespace: 'team-a', slug: 'keep', ...makeSkill('# keep\n') }
    ]
    const registry = await startFakeRegistry({ token: 'token', skills })

    try {
      await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--skill', 'keep', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })
      const inventory = new InventoryStore(env.home)
      await inventory.mutateAtomic(value => { value.items.find(item => item.slug === 'demo')!.targets[0]!.installedBy = ['suite:@team-a/set@1'] })
      const before = await inventory.read()
      skills.splice(0, skills.length)

      const pruned = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir, '--prune', '--force',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(pruned.exitCode).not.toBe(0)
      expect(JSON.parse(pruned.stdout).actions).toEqual([])
      expect(JSON.parse(pruned.stdout).failures[0].message).toContain('shared or Suite-owned')
      expect(await inventory.read()).toEqual(before)
      expect(await readFile(join(skillsDir, 'demo/SKILL.md'), 'utf8')).toContain('name: demo')
      expect(await Bun.file(join(skillsDir, 'keep', 'SKILL.md')).exists()).toBe(true)
    } finally {
      registry.stop()
    }
  })

  test('push all validates packages and submits an uploaded version for review', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const skillDir = join(skillsDir, 'demo')
    await mkdir(join(skillDir, '.skillhub'), { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n')
    await writeFile(join(skillDir, '.skillhub', 'metadata.json'), '{"must":"not be uploaded"}')
    await writeFile(join(skillDir, '.skillhub', 'examples.json'), '{invalid authored content}')
    const registry = await startFakeRegistry({ token: 'token', publishStatus: 'UPLOADED' })

    try {
      const pushed = await runCli([
        'sync', 'push', '--all', '--namespace', 'team-a', '--dir', skillsDir,
        '--submit-review', '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(pushed.exitCode).toBe(0)
      expect(JSON.parse(pushed.stdout).items[0].action).toBe('submitted-review')
      expect(JSON.parse(pushed.stdout).items[0]).toMatchObject({
        status: 'UPLOADED',
        reviewStatus: 'PENDING_REVIEW'
      })
      expect(registry.received.publish?.visibility).toBe('NAMESPACE_ONLY')
      expect(registry.received.publish?.rejectExistingVersion).toBe(true)
      expect(registry.received.publish?.archiveEntries).toContain('.skillhub/examples.json')
      expect(registry.received.publish?.archiveEntries).not.toContain('.skillhub/metadata.json')
      expect(registry.received.review).toMatchObject({
        namespace: 'team-a', slug: 'demo', version: '1.0.0', targetVisibility: 'NAMESPACE_ONLY'
      })
      expect(registry.received.reviews).toBe(1)
    } finally {
      registry.stop()
      await rm(skillsDir, { recursive: true, force: true })
    }
  })

  test.each([
    { status: 'SCANNING', submitReview: false, action: 'uploaded', reviews: 0 },
    { status: 'SCANNING', submitReview: true, action: 'uploaded', reviews: 0 },
    { status: 'UPLOADED', submitReview: true, action: 'submitted-review', reviews: 1 },
    { status: 'PENDING_REVIEW', submitReview: true, action: 'submitted-review', reviews: 0 },
    { status: 'PUBLISHED', submitReview: true, action: 'uploaded', reviews: 0 }
  ])('push preserves $status and applies submit-review boundary', async scenario => {
    const env = await createTempHome()
    const skillDir = await makeLocalSkill(env.cwd)
    const registry = await startFakeRegistry({ token: 'token', publishStatus: scenario.status })

    try {
      const result = await runCli([
        'sync', 'push', skillDir, '--namespace', 'team-a',
        ...(scenario.submitReview ? ['--submit-review'] : []),
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      const item = JSON.parse(result.stdout).items[0]

      expect(result.exitCode).toBe(0)
      expect(item).toMatchObject({ status: scenario.status, action: scenario.action })
      expect(registry.received.reviews).toBe(scenario.reviews)
    } finally {
      registry.stop()
    }
  })

  test('PUBLIC uploaded push submits review once with PUBLIC visibility', async () => {
    const env = await createTempHome()
    const skillDir = await makeLocalSkill(env.cwd)
    const registry = await startFakeRegistry({ token: 'token', publishStatus: 'UPLOADED' })

    try {
      const result = await runCli([
        'sync', 'push', skillDir, '--namespace', 'team-a', '--visibility', 'public', '--submit-review',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(result.exitCode).toBe(0)
      expect(registry.received.reviews).toBe(1)
      expect(registry.received.review?.targetVisibility).toBe('PUBLIC')
    } finally {
      registry.stop()
    }
  })

  test('PRIVATE push rejects --submit-review before validation or upload', async () => {
    const env = await createTempHome()
    const skillDir = await makeLocalSkill(env.cwd)
    const registry = await startFakeRegistry({ token: 'token' })

    try {
      const result = await runCli([
        'sync', 'push', skillDir, '--namespace', 'team-a', '--visibility', 'private', '--submit-review',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(result.exitCode).toBe(5)
      expect(JSON.parse(result.stderr).message).toContain('--submit-review requires public or namespace-only visibility')
      expect(registry.received.validate).toBeNull()
      expect(registry.received.publish).toBeNull()
      expect(registry.received.reviews).toBe(0)
    } finally {
      registry.stop()
    }
  })

  test('human push output always uses submitted semantics and includes the raw status', async () => {
    const env = await createTempHome()
    const skillDir = await makeLocalSkill(env.cwd)
    const registry = await startFakeRegistry({ token: 'token', publishStatus: 'SCANNING' })

    try {
      const result = await runCli([
        'sync', 'push', skillDir, '--namespace', 'team-a',
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(result.exitCode).toBe(0)
      expect(result.stdout).toContain('submitted')
      expect(result.stdout).toContain('status=SCANNING')
      expect(result.stdout).not.toContain('uploaded')
      expect(result.stdout).toContain('Check the Web page for final publish or review status.')
    } finally {
      registry.stop()
    }
  })

  test('push dry-run uses strict validation without uploading', async () => {
    const env = await createTempHome()
    const skillDir = join(env.cwd, 'demo')
    await mkdir(skillDir, { recursive: true })
    await writeFile(join(skillDir, 'SKILL.md'), '---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n')
    const registry = await startFakeRegistry({ token: 'token' })

    try {
      const result = await runCli([
        'sync', 'push', skillDir, '--namespace', 'team-a', '--dry-run',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(result.exitCode).toBe(0)
      expect(JSON.parse(result.stdout).items[0].action).toBe('validated')
      expect(registry.received.validate?.rejectExistingVersion).toBe(true)
      expect(registry.received.publish).toBeNull()
    } finally {
      registry.stop()
    }
  })

  test('pull refuses to replace an unmanaged conflicting directory', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    await mkdir(join(skillsDir, 'demo'), { recursive: true })
    await writeFile(join(skillsDir, 'demo', 'local.txt'), 'keep')
    const fixture = makeSkill('---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n')
    const registry = await startFakeRegistry({
      token: 'token', skills: [{ namespace: 'team-a', slug: 'demo', ...fixture }]
    })

    try {
      const result = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(result.exitCode).toBe(1)
      expect(await readFile(join(skillsDir, 'demo', 'local.txt'), 'utf8')).toBe('keep')
    } finally {
      registry.stop()
    }
  })
})


test.each([true, false])('sync push keeps malformed examples and reports nonblocking warnings (dryRun=%s)', async dryRun => {
  const env = await createTempHome()
  const directory = await makeLocalSkill(env.cwd)
  await mkdir(join(directory, '.skillhub'), { recursive: true })
  await writeFile(join(directory, '.skillhub/examples.json'), '{invalid but publishable}')
  const registry = await startFakeRegistry({ token: 'token' })
  try {
    for (const json of [true, false]) {
      const result = await runCli(['sync', 'push', directory, '--namespace', 'team-a', '--registry', registry.url, '--token', 'token',
        ...(dryRun ? ['--dry-run'] : []), ...(json ? ['--json'] : [])], { HOME: env.home }, { cwd: env.cwd })
      expect(result.exitCode).toBe(0)
      if (json) {
        const item = JSON.parse(result.stdout).items[0]
        expect(item.examplesStatus).toBe('invalid')
        expect(item.warnings).toHaveLength(1)
        expect(item.action).toBe(dryRun ? 'validated' : 'uploaded')
      } else expect(result.stdout).toContain('remains ordinary package content')
    }
    if (!dryRun) expect(registry.received.publish!.archiveEntries).toContain('.skillhub/examples.json')
  } finally { registry.stop() }
})

test.each(['ready', 'none'] as const)('sync push reports %s examples for raw ZIP in JSON and human output', async state => {
  const env = await createTempHome()
  const path = join(env.cwd, 'skill.zip')
  const content = JSON.stringify({ schemaVersion: 1, defaultLocale: 'en', examples: { en: [{ id: 'one', title: 'One', prompt: 'Use {{skill}}' }] } })
  await writeFile(path, zipSync({ 'SKILL.md': strToU8('# Demo'), ...(state === 'ready' ? { '.skillhub/examples.json': strToU8(content) } : {}) }))
  const registry = await startFakeRegistry({ token: 'token' })
  try {
    for (const json of [true, false]) {
      const result = await runCli(['sync', 'push', path, '--namespace', 'team-a', '--registry', registry.url, '--token', 'token', ...(json ? ['--json'] : [])], { HOME: env.home }, { cwd: env.cwd })
      expect(result.exitCode).toBe(0)
      if (json) expect(JSON.parse(result.stdout).items[0]).toMatchObject({ examplesStatus: state, examplesCount: state === 'ready' ? 1 : 0, warnings: [] })
      else expect(result.stdout).toContain(state === 'ready' ? 'Task examples: 1' : 'Task examples: none')
    }
  } finally { registry.stop() }
})


test('namespace sync preserves a disabled installation even with force and prune', async () => {
  const env = await createTempHome()
  const skill = { namespace: 'team-a', slug: 'demo', ...makeSkill('# disabled fixture') }
  const registry = await startFakeRegistry({ token: 'fixture-token', skills: [skill] })
  const root = join(env.cwd, 'workspace')
  const flags = ['--registry', registry.url, '--token', 'fixture-token', '--json']
  try {
    const installed = await runCli(['install', '@team-a/demo', '--dir', root, ...flags], { HOME: env.home }, { cwd: env.cwd })
    expect(installed.exitCode).toBe(0)
    const dir = join(root, 'demo')
    await rename(join(dir, 'SKILL.md'), join(dir, 'SKILL.md.disabled'))
    const store = new InventoryStore(env.home)
    await store.mutateAtomic(inventory => { inventory.items[0]!.targets[0]!.installationState = 'disabled' })
    const before = await readFile(store.path, 'utf8')
    const result = await runCli(['sync', 'pull', '--namespace', 'team-a', '--skill', 'demo', '--dir', root,
      '--force', '--prune', ...flags], { HOME: env.home }, { cwd: env.cwd })
    expect(result.stdout + result.stderr).toContain('disabled')
    expect(await readFile(store.path, 'utf8')).toBe(before)
    expect(await readFile(join(dir, 'SKILL.md.disabled'), 'utf8')).toBe('# disabled fixture')
    expect(await Bun.file(join(dir, 'SKILL.md')).exists()).toBe(false)
  } finally { registry.stop() }
})
