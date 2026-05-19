import { mkdtemp, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { createTempHome } from '../helpers/temp-env'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'

let registry: Awaited<ReturnType<typeof startFakeRegistry>> | undefined

afterEach(() => {
  registry?.stop()
  registry = undefined
})

async function login(env: { home: string }, registryUrl: string) {
  const result = await runCli(['login', '--registry', registryUrl, '--token', 'sk_ok'], {
    HOME: env.home,
    USERPROFILE: env.home
  })
  if (result.exitCode !== 0) {
    throw new Error(`login failed: ${result.stderr}`)
  }
}

async function makeTempDir(...files: Array<[string, string]>) {
  const dir = await mkdtemp(join(tmpdir(), 'skillhub-dryrun-'))
  for (const [name, content] of files) {
    await writeFile(join(dir, name), content)
  }
  return dir
}

describe('publish --dry-run', () => {
  test('calls validate endpoint and reports success', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })
    await login(env, registry.url)

    const dir = await makeTempDir(['SKILL.md', '---\nname: my-skill\ndescription: A test\n---\n# Hello'])
    const result = await runCli(['publish', dir, '--dry-run', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('Validation passed')
    expect(registry.received.validate).not.toBeNull()
    expect(registry.received.validate!.namespace).toBe('global')
    expect(registry.received.publish).toBeNull()
  })

  test('--dry-run with --json returns structured response on warnings (valid=false)', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      dryRunResponse: {
        valid: false,
        errors: [],
        warnings: ['Disallowed file extension: data.bin'],
        resolvedSlug: 'my-skill',
        resolvedVersion: '2.0.0'
      }
    })
    await login(env, registry.url)

    const dir = await makeTempDir(['SKILL.md', '---\nname: my-skill\ndescription: test\n---\n'])
    const result = await runCli(['publish', dir, '--dry-run', '--json', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(6)
    const json = JSON.parse(result.stdout)
    expect(json.valid).toBe(false)
    expect(json.resolvedSlug).toBe('my-skill')
    expect(json.resolvedVersion).toBe('2.0.0')
    expect(json.warnings).toContain('Disallowed file extension: data.bin')
  })

  test('--dry-run reports validation errors', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      dryRunResponse: {
        valid: false,
        errors: ['Missing required file: SKILL.md at root'],
        warnings: [],
        resolvedSlug: null,
        resolvedVersion: null
      }
    })
    await login(env, registry.url)

    const dir = await makeTempDir(['README.md', '# No SKILL.md here'])
    const result = await runCli(['publish', dir, '--dry-run', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(6)
    expect(result.stdout).toContain('Validation failed')
    expect(result.stdout).toContain('Missing required file: SKILL.md at root')
  })

  test('--dry-run does not actually publish', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })
    await login(env, registry.url)

    const dir = await makeTempDir(['SKILL.md', '---\nname: test\ndescription: test\n---\n'])
    await runCli(['publish', dir, '--dry-run', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(registry.received.publish).toBeNull()
  })

  test('--dry-run respects --namespace', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })
    await login(env, registry.url)

    const dir = await makeTempDir(['SKILL.md', '---\nname: test\ndescription: test\n---\n'])
    await runCli(['publish', dir, '--dry-run', '--namespace', 'myteam', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(registry.received.validate!.namespace).toBe('myteam')
  })

  test('--dry-run forwards --visibility to server', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })
    await login(env, registry.url)

    const dir = await makeTempDir(['SKILL.md', '---\nname: test\ndescription: test\n---\n'])
    await runCli(['publish', dir, '--dry-run', '--visibility', 'private', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(registry.received.validate!.visibility).toBe('PRIVATE')
  })

  test('--dry-run requires authentication', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })

    const dir = await makeTempDir(['SKILL.md', '---\nname: test\ndescription: test\n---\n'])
    const result = await runCli(['publish', dir, '--dry-run', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(2)
    expect(result.stderr).toContain('authentication')
  })

  test('--dry-run reports scope error on 403', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok', failures: { validate: 'forbidden' } })
    await login(env, registry.url)

    const dir = await makeTempDir(['SKILL.md', '---\nname: test\ndescription: test\n---\n'])
    const result = await runCli(['publish', dir, '--dry-run', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(2)
    expect(result.stderr).toContain('scope')
  })
})
