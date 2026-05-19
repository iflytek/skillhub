/**
 * CLI skill version-upgrade flow.
 *
 * These tests cover scenarios that span multiple `install` invocations
 * against a registry whose state changes between runs — i.e. the user-facing
 * "upgrade an installed skill" workflow. They complement the per-command
 * tests in install-command.test.ts which use a single static registry.
 *
 * Coverage focus (per test-case-design-skill methodology):
 *   - VU1 (state-transition): full v1 → v2 upgrade lifecycle. Asserts that
 *     metadata.json, inventory.json AND the on-disk bundle all reflect v2
 *     after `install --force`, with no orphaned v1 entry left behind.
 *   - VU2 (equivalence-class on `--version`): pinning to a specific version
 *     forwards `?version=` to /resolve so the registry can serve the
 *     intended bundle.
 */
import { mkdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { zipSync, strToU8 } from 'fflate'
import { createTempHome } from '../helpers/temp-env'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'

function makeSkillZipWithBody(body: string): Uint8Array {
  return zipSync({ 'SKILL.md': strToU8(body) })
}

let registry: Awaited<ReturnType<typeof startFakeRegistry>> | undefined

afterEach(() => {
  registry?.stop()
  registry = undefined
})

describe('version upgrade flow', () => {
  // -------------------------------------------------------------------------
  // VU1 — full upgrade lifecycle:
  //   1. Registry serves pdf-parser@1.0.0 → install → metadata=v1, content=v1
  //   2. Stop registry, start a new one serving pdf-parser@2.0.0
  //   3. Install --force using the new registry URL
  //   4. metadata.json, inventory.json AND on-disk SKILL.md all reflect v2
  // -------------------------------------------------------------------------
  test('VU1 install v1 then upgrade to v2 with --force replaces metadata, inventory, and content', async () => {
    const env = await createTempHome()

    // --- Stage 1: install v1 ----------------------------------------------
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u1', displayName: 'User One' },
      skills: [{
        namespace: 'global',
        slug: 'pdf-parser',
        version: '1.0.0',
        zipBytes: makeSkillZipWithBody('# pdf-parser v1\n\nVersion one body.')
      }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'skills-upgrade')
    await mkdir(installDir, { recursive: true })

    const r1 = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(r1.exitCode).toBe(0)

    const skillFile = join(installDir, 'pdf-parser', 'SKILL.md')
    const metaPath = join(installDir, 'pdf-parser', '.skillhub', 'metadata.json')
    const inventoryPath = join(env.home, '.skillhub', 'inventory.json')

    {
      const meta = JSON.parse(await readFile(metaPath, 'utf-8'))
      expect(meta.version).toBe('1.0.0')
      const body = await readFile(skillFile, 'utf-8')
      expect(body).toContain('Version one body.')
    }

    // --- Stage 2: swap registry to v2 -------------------------------------
    registry.stop()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u1', displayName: 'User One' },
      skills: [{
        namespace: 'global',
        slug: 'pdf-parser',
        version: '2.0.0',
        zipBytes: makeSkillZipWithBody('# pdf-parser v2\n\nVersion two body.')
      }]
    })

    // Re-login against the new registry (URL changed, so credentials are
    // keyed differently).
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const r2 = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok', '--force'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(r2.exitCode).toBe(0)

    // --- Stage 3: assert end state ----------------------------------------
    const finalMeta = JSON.parse(await readFile(metaPath, 'utf-8'))
    expect(finalMeta.version).toBe('2.0.0')
    expect(finalMeta.registry).toBe(registry.url)

    const finalBody = await readFile(skillFile, 'utf-8')
    expect(finalBody).toContain('Version two body.')
    expect(finalBody).not.toContain('Version one body.')

    const inventory = JSON.parse(await readFile(inventoryPath, 'utf-8')) as {
      items: Array<{ namespace: string; slug: string; version: string; targets: Array<{ installDir: string }> }>
    }
    const matching = inventory.items.filter(i => i.namespace === 'global' && i.slug === 'pdf-parser')
    expect(matching).toHaveLength(1) // no duplicate v1 entry
    expect(matching[0]?.version).toBe('2.0.0')
  })

  // -------------------------------------------------------------------------
  // VU2 — version pinning:
  //   `install --version=X` must forward ?version=X to /resolve so the
  //   registry can serve the requested bundle. The fake registry captures
  //   the resolve query for assertion.
  // -------------------------------------------------------------------------
  test('VU2 --version pins resolve to the requested version string', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u1', displayName: 'User One' },
      skills: [{
        namespace: 'global',
        slug: 'pdf-parser',
        version: '1.5.0',
        zipBytes: makeSkillZipWithBody('# pinned')
      }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'skills-pin')
    await mkdir(installDir, { recursive: true })

    const result = await runCli(
      [
        'install', 'pdf-parser',
        '--version', '1.5.0',
        '--dir', installDir,
        '--registry', registry.url,
        '--token', 'sk_ok'
      ],
      { HOME: env.home, USERPROFILE: env.home }
    )

    expect(result.exitCode).toBe(0)
    expect(registry.received.resolve?.namespace).toBe('global')
    expect(registry.received.resolve?.slug).toBe('pdf-parser')
    expect(registry.received.resolve?.version).toBe('1.5.0')

    const meta = JSON.parse(await readFile(join(installDir, 'pdf-parser', '.skillhub', 'metadata.json'), 'utf-8'))
    expect(meta.version).toBe('1.5.0')
  })
})
