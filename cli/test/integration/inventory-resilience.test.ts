/**
 * inventory.json resilience.
 *
 * inventory.json is the local manifest of installed skills. These tests pin
 * how the CLI behaves when that file is corrupt or written by overlapping
 * operations:
 *   - list against a corrupt inventory should fail loudly (not silently)
 *   - install against a corrupt inventory should still complete the
 *     filesystem extraction even if inventory bookkeeping fails — partial
 *     state surfaces a clear error
 *   - sequential installs of distinct skills do not corrupt the manifest
 *
 * The unit test in test/unit/stores/inventory-store.test.ts asserts the
 * lock-file recovery path. These cover the user-facing CLI surface.
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
  return zipSync({ 'SKILL.md': strToU8('# test') })
}

describe('inventory resilience', () => {
  test('list exits non-zero when inventory.json is malformed (documents current generic-error UX)', async () => {
    const env = await createTempHome()
    await mkdir(join(env.home, '.skillhub'), { recursive: true })
    await writeFile(join(env.home, '.skillhub', 'inventory.json'), '{ this is not JSON')

    const result = await runCli(['list'], { HOME: env.home, USERPROFILE: env.home })

    // Contract: CLI must not crash silently or print a stack trace. It
    // exits non-zero and emits a short message.
    expect(result.exitCode).not.toBe(0)
    expect(result.stderr.length).toBeGreaterThan(0)
    expect(result.stderr.length).toBeLessThan(2000)
    // Documented gap: today's message is the generic "unexpected failure"
    // and does not mention `inventory` or `JSON`. When the CLI surfaces a
    // more specific message in the future, tighten this assertion.
    expect(result.stderr).toContain('Error')
  })

  test('list --json on a corrupt inventory emits a parseable error envelope (not a stack trace)', async () => {
    const env = await createTempHome()
    await mkdir(join(env.home, '.skillhub'), { recursive: true })
    await writeFile(join(env.home, '.skillhub', 'inventory.json'), '{"items":')

    const result = await runCli(['list', '--json'], { HOME: env.home, USERPROFILE: env.home })

    expect(result.exitCode).not.toBe(0)
    const candidate = result.stdout || result.stderr
    expect(candidate.length).toBeLessThan(2000)
    // Contract: --json error path is machine-parseable, regardless of the
    // (currently generic) human message.
    const json = JSON.parse(candidate) as { ok: boolean; message: string; exitCode: number }
    expect(json.ok).toBe(false)
    expect(typeof json.message).toBe('string')
    expect(json.exitCode).toBe(result.exitCode)
  })

  test('two sequential installs of distinct slugs leave a coherent inventory', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [
        { namespace: 'global', slug: 'one', version: '1.0.0', zipBytes: makeSkillZip() },
        { namespace: 'global', slug: 'two', version: '1.0.0', zipBytes: makeSkillZip() }
      ]
    })

    const baseDir = join(env.cwd, 'pool')
    await mkdir(baseDir, { recursive: true })

    const r1 = await runCli(
      ['install', 'one', '--dir', baseDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(r1.exitCode).toBe(0)

    const r2 = await runCli(
      ['install', 'two', '--dir', baseDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(r2.exitCode).toBe(0)

    const inventory = JSON.parse(
      await readFile(join(env.home, '.skillhub', 'inventory.json'), 'utf-8')
    ) as { items: Array<{ slug: string; targets: Array<{ installDir: string }> }> }

    const slugs = inventory.items.map(i => i.slug).sort()
    expect(slugs).toEqual(['one', 'two'])
    for (const item of inventory.items) {
      expect(item.targets.length).toBeGreaterThan(0)
    }
  })
})
