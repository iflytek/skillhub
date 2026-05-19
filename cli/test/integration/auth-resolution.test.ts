/**
 * End-to-end integration coverage for token / registry priority resolution.
 *
 * The unit test in test/unit/services/registry-service.test.ts pins the
 * resolution function in isolation. These tests verify the same priorities
 * are wired through the actual CLI subprocess: --flag > SKILLHUB_* env >
 * stored config / credentials > built-in default.
 *
 * Why this matters: a regression in the wiring (e.g. command forgets to
 * forward `process.env`) would silently downgrade users to the wrong
 * registry / token without surfacing in unit tests.
 */
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'
import { createTempHome } from '../helpers/temp-env'

let registry: Awaited<ReturnType<typeof startFakeRegistry>> | undefined
let registryB: Awaited<ReturnType<typeof startFakeRegistry>> | undefined

afterEach(() => {
  registry?.stop(); registry = undefined
  registryB?.stop(); registryB = undefined
})

async function seedCredentials(home: string, registryUrl: string, token: string): Promise<void> {
  await mkdir(join(home, '.skillhub'), { recursive: true })
  await writeFile(
    join(home, '.skillhub', 'credentials.json'),
    JSON.stringify({ tokens: { [registryUrl]: token } })
  )
}

async function seedConfig(home: string, registryUrl: string): Promise<void> {
  await mkdir(join(home, '.skillhub'), { recursive: true })
  await writeFile(
    join(home, '.skillhub', 'config.json'),
    JSON.stringify({ registry: registryUrl })
  )
}

// ---------------------------------------------------------------------------
// Token priority: --token > SKILLHUB_TOKEN > stored
// ---------------------------------------------------------------------------

describe('auth resolution — token priority', () => {
  test('--token flag wins over SKILLHUB_TOKEN env', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_from_flag',
      user: { handle: 'flag-user', displayName: 'Flag' }
    })

    const result = await runCli(
      ['whoami', '--registry', registry.url, '--token', 'sk_from_flag'],
      { HOME: env.home, USERPROFILE: env.home, SKILLHUB_TOKEN: 'sk_wrong_from_env' }
    )
    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('flag-user')
  })

  test('SKILLHUB_TOKEN env wins over stored token', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_from_env',
      user: { handle: 'env-user', displayName: 'Env' }
    })
    await seedCredentials(env.home, registry.url, 'sk_wrong_from_storage')

    const result = await runCli(
      ['whoami', '--registry', registry.url],
      { HOME: env.home, USERPROFILE: env.home, SKILLHUB_TOKEN: 'sk_from_env' }
    )
    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('env-user')
  })

  test('stored token used when neither --token nor env is set', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_from_storage',
      user: { handle: 'storage-user', displayName: 'Storage' }
    })
    await seedCredentials(env.home, registry.url, 'sk_from_storage')

    const result = await runCli(
      ['whoami', '--registry', registry.url],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('storage-user')
  })
})

// ---------------------------------------------------------------------------
// Registry priority: --registry > SKILLHUB_REGISTRY > config.json
// ---------------------------------------------------------------------------

describe('auth resolution — registry priority', () => {
  test('--registry flag wins over SKILLHUB_REGISTRY env', async () => {
    const env = await createTempHome()
    // Each registry only authenticates its own token. The wrong registry
    // would 401, so a successful whoami proves the right one was used.
    registry = await startFakeRegistry({
      token: 'sk_a',
      user: { handle: 'a-user', displayName: 'A' }
    })
    registryB = await startFakeRegistry({
      token: 'sk_b',
      user: { handle: 'b-user', displayName: 'B' }
    })

    const result = await runCli(
      ['whoami', '--registry', registry.url, '--token', 'sk_a'],
      { HOME: env.home, USERPROFILE: env.home, SKILLHUB_REGISTRY: registryB.url }
    )
    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('a-user')
  })

  test('SKILLHUB_REGISTRY env wins over config.registry', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_env',
      user: { handle: 'env-reg', displayName: 'EnvReg' }
    })
    registryB = await startFakeRegistry({
      token: 'sk_config',
      user: { handle: 'config-reg', displayName: 'ConfigReg' }
    })
    await seedConfig(env.home, registryB.url)

    const result = await runCli(
      ['whoami', '--token', 'sk_env'],
      { HOME: env.home, USERPROFILE: env.home, SKILLHUB_REGISTRY: registry.url }
    )
    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('env-reg')
  })

  test('config.registry used when no --registry / env present', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_config',
      user: { handle: 'config-only-user', displayName: 'CfgOnly' }
    })
    await seedConfig(env.home, registry.url)
    await seedCredentials(env.home, registry.url, 'sk_config')

    const result = await runCli(
      ['whoami'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('config-only-user')
  })
})
