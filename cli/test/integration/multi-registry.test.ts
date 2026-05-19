/**
 * Multi-registry credential isolation.
 *
 * credentials.json keys tokens by registry URL. Operations on one registry
 * must not leak into another. These tests cover:
 *   - Logging into A then B preserves both tokens.
 *   - Logging out of A leaves B's token intact.
 *   - whoami after logout reflects per-registry session state.
 *   - Re-login to A overwrites only A's slot.
 */
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'
import { createTempHome } from '../helpers/temp-env'

let regA: Awaited<ReturnType<typeof startFakeRegistry>> | undefined
let regB: Awaited<ReturnType<typeof startFakeRegistry>> | undefined

afterEach(() => {
  regA?.stop(); regA = undefined
  regB?.stop(); regB = undefined
})

async function readCreds(home: string): Promise<{ tokens: Record<string, string> }> {
  return JSON.parse(await readFile(join(home, '.skillhub', 'credentials.json'), 'utf-8'))
}

describe('multi-registry credential isolation', () => {
  test('login to A then B leaves both tokens in credentials.json', async () => {
    const env = await createTempHome()
    regA = await startFakeRegistry({ token: 'sk_a', user: { handle: 'a', displayName: 'A' } })
    regB = await startFakeRegistry({ token: 'sk_b', user: { handle: 'b', displayName: 'B' } })

    await runCli(
      ['login', '--registry', regA.url, '--token', 'sk_a'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    await runCli(
      ['login', '--registry', regB.url, '--token', 'sk_b'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    const creds = await readCreds(env.home)
    expect(creds.tokens[regA.url]).toBe('sk_a')
    expect(creds.tokens[regB.url]).toBe('sk_b')
  })

  test('logout from A removes A token while B token survives', async () => {
    const env = await createTempHome()
    regA = await startFakeRegistry({ token: 'sk_a', user: { handle: 'a', displayName: 'A' } })
    regB = await startFakeRegistry({ token: 'sk_b', user: { handle: 'b', displayName: 'B' } })

    await runCli(['login', '--registry', regA.url, '--token', 'sk_a'], { HOME: env.home, USERPROFILE: env.home })
    await runCli(['login', '--registry', regB.url, '--token', 'sk_b'], { HOME: env.home, USERPROFILE: env.home })
    await runCli(['logout', '--registry', regA.url], { HOME: env.home, USERPROFILE: env.home })

    const creds = await readCreds(env.home)
    expect(creds.tokens[regA.url]).toBeUndefined()
    expect(creds.tokens[regB.url]).toBe('sk_b')
  })

  test('whoami after logout-A: A reports not-logged-in, B still authenticates', async () => {
    const env = await createTempHome()
    regA = await startFakeRegistry({ token: 'sk_a', user: { handle: 'a-user', displayName: 'A' } })
    regB = await startFakeRegistry({ token: 'sk_b', user: { handle: 'b-user', displayName: 'B' } })

    await runCli(['login', '--registry', regA.url, '--token', 'sk_a'], { HOME: env.home, USERPROFILE: env.home })
    await runCli(['login', '--registry', regB.url, '--token', 'sk_b'], { HOME: env.home, USERPROFILE: env.home })
    await runCli(['logout', '--registry', regA.url], { HOME: env.home, USERPROFILE: env.home })

    const whoamiA = await runCli(
      ['whoami', '--registry', regA.url],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(whoamiA.exitCode).toBe(2) // EXIT.auth
    expect(whoamiA.stderr.toLowerCase()).toContain('not logged in')

    const whoamiB = await runCli(
      ['whoami', '--registry', regB.url],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(whoamiB.exitCode).toBe(0)
    expect(whoamiB.stdout).toContain('b-user')
  })

  test('re-login to A overwrites only A entry; B token unchanged', async () => {
    const env = await createTempHome()
    // Don't pin a token on either registry so any value passes whoami; we
    // only care about credentials.json bookkeeping here.
    regA = await startFakeRegistry({ user: { handle: 'a', displayName: 'A' } })
    regB = await startFakeRegistry({ user: { handle: 'b', displayName: 'B' } })

    await runCli(['login', '--registry', regA.url, '--token', 'sk_a_old'], { HOME: env.home, USERPROFILE: env.home })
    await runCli(['login', '--registry', regB.url, '--token', 'sk_b'], { HOME: env.home, USERPROFILE: env.home })

    {
      const creds = await readCreds(env.home)
      expect(creds.tokens[regA.url]).toBe('sk_a_old')
      expect(creds.tokens[regB.url]).toBe('sk_b')
    }

    await runCli(['login', '--registry', regA.url, '--token', 'sk_a_new'], { HOME: env.home, USERPROFILE: env.home })

    const creds = await readCreds(env.home)
    expect(creds.tokens[regA.url]).toBe('sk_a_new')
    expect(creds.tokens[regB.url]).toBe('sk_b')
  })
})
