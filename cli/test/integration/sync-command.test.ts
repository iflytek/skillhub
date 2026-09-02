import { createHash } from 'node:crypto'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
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

describe('sync command', () => {
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
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
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
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
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
    const fixture = makeSkill('---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n')
    const registry = await startFakeRegistry({
      token: 'token',
      skills: [{ namespace: 'team-a', slug: 'demo', ...fixture }]
    })

    try {
      await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })
      await writeFile(join(skillsDir, 'demo', 'SKILL.md'), '# local change\n')

      const status = await runCli([
        'sync', 'status', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(JSON.parse(status.stdout).items[0].status).toBe('local-changed')

      const pull = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(pull.exitCode).toBe(1)
      expect(await readFile(join(skillsDir, 'demo', 'SKILL.md'), 'utf8')).toBe('# local change\n')
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
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
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
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
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
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir, '--force',
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
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })
      skill.version = '1.0.0'
      skill.versionId = 1

      const pulled = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir, '--force',
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
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })
      skill.version = 'release-b'
      skill.versionId = 2

      const pulled = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir, '--force',
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
          'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
          '--registry', registry.url, '--token', 'token'
        ], { HOME: env.home }, { cwd: env.cwd })
        await writeFile(join(skillsDir, 'demo', 'SKILL.md'), `# local edit before ${variant.name}\n`)
        skill.version = variant.remoteVersion
        skill.versionId = 2
        skill.fingerprint = variant.remote.fingerprint
        skill.zipBytes = variant.remote.zipBytes

        const pulled = await runCli([
          'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir, '--force',
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

  test('prune removes only unchanged managed orphan skills', async () => {
    const env = await createTempHome()
    const skillsDir = join(env.cwd, 'team-skills')
    const fixture = makeSkill('---\nname: demo\ndescription: Demo\nversion: 1.0.0\n---\n')
    const skills: FakeSkill[] = [{ namespace: 'team-a', slug: 'demo', ...fixture }]
    const registry = await startFakeRegistry({ token: 'token', skills })

    try {
      await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token'
      ], { HOME: env.home }, { cwd: env.cwd })
      skills.splice(0, skills.length)

      const pruned = await runCli([
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir, '--prune',
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(pruned.exitCode).toBe(0)
      expect(JSON.parse(pruned.stdout).actions).toContainEqual({ slug: 'demo', action: 'pruned' })
      expect(await Bun.file(join(skillsDir, 'demo')).exists()).toBe(false)
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
    const registry = await startFakeRegistry({ token: 'token', publishStatus: 'UPLOADED' })

    try {
      const pushed = await runCli([
        'sync', 'push', '--all', '--namespace', 'team-a', '--dir', skillsDir,
        '--submit-review', '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })

      expect(pushed.exitCode).toBe(0)
      expect(JSON.parse(pushed.stdout).items[0].action).toBe('submitted-review')
      expect(registry.received.publish?.visibility).toBe('NAMESPACE_ONLY')
      expect(registry.received.publish?.rejectExistingVersion).toBe(true)
      expect(registry.received.review).toMatchObject({
        namespace: 'team-a', slug: 'demo', version: '1.0.0', targetVisibility: 'NAMESPACE_ONLY'
      })
    } finally {
      registry.stop()
      await rm(skillsDir, { recursive: true, force: true })
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
        'sync', 'pull', '--namespace', 'team-a', '--dir', skillsDir,
        '--registry', registry.url, '--token', 'token', '--json'
      ], { HOME: env.home }, { cwd: env.cwd })
      expect(result.exitCode).toBe(1)
      expect(await readFile(join(skillsDir, 'demo', 'local.txt'), 'utf8')).toBe('keep')
    } finally {
      registry.stop()
    }
  })
})
