import { afterEach, describe, expect, test } from 'bun:test'
import { createTempHome } from '../helpers/temp-env'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'

let registry: { url: string; stop: () => void } | undefined

afterEach(() => {
  registry?.stop()
  registry = undefined
})

describe('whoami command', () => {
  test('not logged in — exits with EXIT.auth and prints not logged in', async () => {
    const { home } = await createTempHome()
    const env = { HOME: home, USERPROFILE: home }

    const result = await runCli(['whoami'], env)

    expect(result.exitCode).toBe(2)
    expect(result.stderr).toContain('not logged in')
  })

  test('happy path — human output contains handle and displayName', async () => {
    registry = await startFakeRegistry({
      user: { handle: 'testuser', displayName: 'Test User', email: 'test@example.com' }
    })
    const { home } = await createTempHome()
    const env = { HOME: home, USERPROFILE: home }

    // Login first to store credentials
    const loginResult = await runCli(
      ['login', '--registry', registry.url, '--token', 'sk_ok'],
      env,
    )
    expect(loginResult.exitCode).toBe(0)

    // Now run whoami
    const result = await runCli(['whoami'], env)

    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('testuser')
    expect(result.stdout).toContain('Test User')
  })

  test('--json flag — output is valid JSON with ok:true and user fields', async () => {
    registry = await startFakeRegistry({
      user: { handle: 'testuser', displayName: 'Test User', email: 'test@example.com' }
    })
    const { home } = await createTempHome()
    const env = { HOME: home, USERPROFILE: home }

    // Login first to store credentials
    const loginResult = await runCli(
      ['login', '--registry', registry.url, '--token', 'sk_ok'],
      env,
    )
    expect(loginResult.exitCode).toBe(0)

    // Now run whoami --json
    const result = await runCli(['whoami', '--json'], env)

    expect(result.exitCode).toBe(0)
    const json = JSON.parse(result.stdout)
    expect(json.ok).toBe(true)
    expect(json.handle).toBe('testuser')
    expect(json.displayName).toBe('Test User')
  })

  test('--token override — works without prior login', async () => {
    registry = await startFakeRegistry({
      user: { handle: 'testuser', displayName: 'Test User', email: 'test@example.com' }
    })
    const { home } = await createTempHome()
    const env = { HOME: home, USERPROFILE: home }

    // No login — pass token directly
    const result = await runCli(
      ['whoami', '--token', 'sk_ok', '--registry', registry.url],
      env,
    )

    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('testuser')
  })

  test('auth failure from server — exits with EXIT.auth', async () => {
    registry = await startFakeRegistry({ failures: { whoami: 'auth' } })
    const { home } = await createTempHome()
    const env = { HOME: home, USERPROFILE: home }

    // Pass a bad token explicitly so the server returns 401
    const result = await runCli(
      ['whoami', '--token', 'sk_bad', '--registry', registry.url],
      env,
    )

    expect(result.exitCode).toBe(2)
    expect(result.stderr.toLowerCase()).toContain('authentication failed')
  })

  // ---------------------------------------------------------------------------
  // P1 — Stored-token revocation: token persists in credentials.json but
  // server now rejects it. whoami must surface the auth failure on first
  // call, not silently use a stale principal.
  // ---------------------------------------------------------------------------
  test('stored token that the server now rejects surfaces EXIT.auth on whoami', async () => {
    // Configure a registry that requires sk_new but seed sk_old in credentials
    // — simulates a token that was valid at login time but has since been
    // revoked or rotated server-side.
    registry = await startFakeRegistry({
      token: 'sk_new',
      user: { handle: 'should-not-see', displayName: 'X' }
    })
    const { home } = await createTempHome()
    const env = { HOME: home, USERPROFILE: home }

    const { mkdir, writeFile } = await import('node:fs/promises')
    const { join } = await import('node:path')
    await mkdir(join(home, '.skillhub'), { recursive: true })
    await writeFile(
      join(home, '.skillhub', 'credentials.json'),
      JSON.stringify({ tokens: { [registry.url]: 'sk_old_revoked' } })
    )

    const result = await runCli(['whoami', '--registry', registry.url], env)
    expect(result.exitCode).toBe(2)
    expect(result.stderr.toLowerCase()).toMatch(/auth|401|unauthorized/)
  })

  // ---------------------------------------------------------------------------
  // P1 — Token priority --token > SKILLHUB_TOKEN > stored, end-to-end via
  // whoami. Cross-checks the auth-resolution suite by verifying the wired
  // contract on this specific command.
  // ---------------------------------------------------------------------------
  test('--token wins over SKILLHUB_TOKEN env on whoami', async () => {
    registry = await startFakeRegistry({
      token: 'sk_winner',
      user: { handle: 'winner', displayName: 'W' }
    })
    const { home } = await createTempHome()
    const env = { HOME: home, USERPROFILE: home, SKILLHUB_TOKEN: 'sk_loser_env' }

    const result = await runCli(
      ['whoami', '--token', 'sk_winner', '--registry', registry.url],
      env
    )
    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('winner')
  })
})
