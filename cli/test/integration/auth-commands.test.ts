import { afterEach, describe, expect, test } from 'bun:test'
import { createTempHome } from '../helpers/temp-env'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'

let registry: { url: string; stop: () => void } | undefined

afterEach(() => {
  registry?.stop()
  registry = undefined
})

describe('auth commands', () => {
  // -------------------------------------------------------------------------
  // login
  // -------------------------------------------------------------------------

  test('login stores registry and token only after whoami succeeds', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok', user: { handle: 'u1', displayName: 'User One' } })

    const result = await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('Logged in')
    expect(await Bun.file(`${env.home}/.skillhub/config.json`).json()).toMatchObject({ registry: registry.url })
    expect(await Bun.file(`${env.home}/.skillhub/credentials.json`).json()).toMatchObject({ tokens: { [registry.url]: 'sk_ok' } })
  })

  test('login fails with invalid token', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })

    const result = await runCli(['login', '--registry', registry.url, '--token', 'sk_bad'], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(2)
    expect(result.stderr).toContain('authentication failed')
  })

  // [P0] missing token → EXIT.usage, stderr contains "token is required"
  test('login without --token exits with usage error', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })

    const result = await runCli(['login', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(5) // EXIT.usage
    expect(result.stderr).toContain('token is required')
  })

  // [P0] whoami failure must NOT write credentials
  test('login does not write credentials when whoami fails', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok', failures: { whoami: 'auth' } })

    const result = await runCli(['login', '--registry', registry.url, '--token', 'sk_bad'], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).not.toBe(0)

    const credFile = Bun.file(`${env.home}/.skillhub/credentials.json`)
    const exists = await credFile.exists()
    if (exists) {
      const creds = await credFile.json() as { tokens?: Record<string, string> }
      expect(creds.tokens?.[registry.url]).toBeUndefined()
    }
    // file not existing is also acceptable — either way no token was stored
  })

  // [P1] --json output shape on success
  test('login --json emits { ok, registry, handle }', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok', user: { handle: 'u1', displayName: 'User One' } })

    const result = await runCli(['login', '--registry', registry.url, '--token', 'sk_ok', '--json'], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(0)
    const parsed = JSON.parse(result.stdout)
    expect(parsed).toEqual({ ok: true, registry: registry.url, handle: 'u1' })
  })

  // [P1] network error → EXIT.network. The exit code is the contract; the
  // exact message can be "registry unreachable" or "registry returned 5xx"
  // depending on whether Bun's fetch throws or returns a 5xx Response on
  // connection refusal — both indicate the same network-class failure.
  test('login with network failure exits with network error', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok', failures: { whoami: 'network' } })

    const result = await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(3) // EXIT.network
    expect(result.stderr).toMatch(/registry unreachable|registry returned 5\d\d/)
  })

  // -------------------------------------------------------------------------
  // logout
  // -------------------------------------------------------------------------

  test('logout removes token', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok', user: { handle: 'u1', displayName: 'User One' } })

    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    const result = await runCli(['logout', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('Logged out')
  })

  // [P0] credentials entry is actually deleted after logout
  test('logout actually removes the token from credentials.json', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok', user: { handle: 'u1', displayName: 'User One' } })

    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    // Confirm token is present before logout
    const before = await Bun.file(`${env.home}/.skillhub/credentials.json`).json() as { tokens: Record<string, string> }
    expect(before.tokens[registry.url]).toBe('sk_ok')

    await runCli(['logout', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    // Token must be absent after logout
    const after = await Bun.file(`${env.home}/.skillhub/credentials.json`).json() as { tokens: Record<string, string> }
    expect(after.tokens[registry.url]).toBeUndefined()
  })

  // [P1] logout when no token exists should still succeed
  test('logout when not logged in exits 0 with success message', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({})

    const result = await runCli(['logout', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('Logged out')
  })

  // [P1] --json output on logout
  test('logout --json emits { ok, registry }', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok', user: { handle: 'u1', displayName: 'User One' } })

    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    const result = await runCli(['logout', '--registry', registry.url, '--json'], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(0)
    const parsed = JSON.parse(result.stdout)
    expect(parsed).toEqual({ ok: true, registry: registry.url })
  })
})
