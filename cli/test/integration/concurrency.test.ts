/**
 * Concurrency tests for inventory.json bookkeeping.
 *
 * inventory-store.ts uses an OS-level lock file with retry + stale-lock
 * detection. These tests exercise that path through real CLI subprocesses
 * (Bun.spawn) running in parallel — the same way users hit it when scripts
 * fan out installs.
 *
 * The unit test in test/unit/stores/inventory-store.test.ts pins the
 * single-process lock recovery; here we cover the cross-process case.
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { zipSync, strToU8 } from 'fflate'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'
import { createTempHome } from '../helpers/temp-env'

let registry: Awaited<ReturnType<typeof startFakeRegistry>> | undefined

afterEach(() => {
  registry?.stop(); registry = undefined
})

function makeSkillZip(): Uint8Array {
  return zipSync({ 'SKILL.md': strToU8('# c') })
}

describe('cross-process concurrency on inventory.json', () => {
  test('two parallel installs of distinct slugs preserve both inventory items', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [
        { namespace: 'global', slug: 'first', version: '1.0.0', zipBytes: makeSkillZip() },
        { namespace: 'global', slug: 'second', version: '1.0.0', zipBytes: makeSkillZip() }
      ]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const dirA = join(env.cwd, 'A')
    const dirB = join(env.cwd, 'B')
    await mkdir(dirA, { recursive: true })
    await mkdir(dirB, { recursive: true })

    const [r1, r2] = await Promise.all([
      runCli(
        ['install', 'first', '--dir', dirA, '--registry', registry.url, '--token', 'sk_ok'],
        { HOME: env.home, USERPROFILE: env.home }
      ),
      runCli(
        ['install', 'second', '--dir', dirB, '--registry', registry.url, '--token', 'sk_ok'],
        { HOME: env.home, USERPROFILE: env.home }
      )
    ])

    expect(r1.exitCode).toBe(0)
    expect(r2.exitCode).toBe(0)

    // Filesystem is correct: both bundles extracted independently.
    expect(await Bun.file(join(dirA, 'first', 'SKILL.md')).exists()).toBe(true)
    expect(await Bun.file(join(dirB, 'second', 'SKILL.md')).exists()).toBe(true)

    const inv = JSON.parse(
      await readFile(join(env.home, '.skillhub', 'inventory.json'), 'utf-8')
    ) as { items: Array<{ slug: string }> }
    const slugs = inv.items.map(i => i.slug).sort()
    expect(slugs).toEqual(['first', 'second'])
  })

  test('two parallel installs of the same slug to the same dir: exactly one wins, one conflicts', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'race', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'race-dir')
    await mkdir(installDir, { recursive: true })

    const [r1, r2] = await Promise.all([
      runCli(
        ['install', 'race', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
        { HOME: env.home, USERPROFILE: env.home }
      ),
      runCli(
        ['install', 'race', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
        { HOME: env.home, USERPROFILE: env.home }
      )
    ])

    const codes = [r1.exitCode, r2.exitCode].sort((a, b) => a - b)
    expect(codes).toEqual([0, 4])

    const inv = JSON.parse(
      await readFile(join(env.home, '.skillhub', 'inventory.json'), 'utf-8')
    ) as { items: Array<{ slug: string; targets: Array<{ installDir: string }> }> }
    const item = inv.items.find(i => i.slug === 'race')
    expect(item).toBeDefined()
    expect(item!.targets).toHaveLength(1) // no duplicate targets
  })

  test('install proceeds after a stale lock file from a dead process', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'after-stale', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    // Plant a stale lock file owned by a PID that does not exist.
    const skillhubDir = join(env.home, '.skillhub')
    await mkdir(skillhubDir, { recursive: true })
    const lockPath = join(skillhubDir, 'inventory.json.lock')
    const ancientTimestamp = Date.now() - 600_000 // 10 minutes ago — past the 30s stale threshold
    await writeFile(lockPath, JSON.stringify({ pid: 999999, timestamp: ancientTimestamp }))

    const installDir = join(env.cwd, 'stale')
    await mkdir(installDir, { recursive: true })

    const result = await runCli(
      ['install', 'after-stale', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(result.exitCode).toBe(0)

    const inv = JSON.parse(
      await readFile(join(skillhubDir, 'inventory.json'), 'utf-8')
    ) as { items: Array<{ slug: string }> }
    expect(inv.items.find(i => i.slug === 'after-stale')).toBeDefined()
  })
})
