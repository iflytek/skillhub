/**
 * Cross-command flow tests.
 *
 * Per-command tests verify each subcommand in isolation. These cases pin
 * behaviors that only emerge when commands chain — e.g. "logout then install
 * fails with auth" or "install + fs-delete + list reports status=missing".
 * Bugs in the boundaries between commands (shared inventory, credentials,
 * config) tend to slip through single-command suites.
 */
import { mkdir, rm, writeFile, readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { zipSync, strToU8 } from 'fflate'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'
import { createTempHome } from '../helpers/temp-env'

let registry: Awaited<ReturnType<typeof startFakeRegistry>> | undefined
let registryB: Awaited<ReturnType<typeof startFakeRegistry>> | undefined

afterEach(() => {
  registry?.stop(); registry = undefined
  registryB?.stop(); registryB = undefined
})

function makeSkillZip(): Uint8Array {
  return zipSync({ 'SKILL.md': strToU8('# x-cross') })
}

// ---------------------------------------------------------------------------
// 1. Auth lifecycle: login → whoami → logout → whoami
// ---------------------------------------------------------------------------

describe('cross-command — auth lifecycle', () => {
  test('login → whoami(success) → logout → whoami(not logged in)', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'cycle-user', displayName: 'Cycle' }
    })

    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })
    const w1 = await runCli(['whoami', '--registry', registry.url], { HOME: env.home, USERPROFILE: env.home })
    expect(w1.exitCode).toBe(0)
    expect(w1.stdout).toContain('cycle-user')

    await runCli(['logout', '--registry', registry.url], { HOME: env.home, USERPROFILE: env.home })
    const w2 = await runCli(['whoami', '--registry', registry.url], { HOME: env.home, USERPROFILE: env.home })
    expect(w2.exitCode).toBe(2)
    expect(w2.stderr.toLowerCase()).toContain('not logged in')
  })

  test('logout-then-install against an auth-required registry fails with EXIT.auth', async () => {
    const env = await createTempHome()
    // Inject auth failure on resolve so this fake server behaves like a
    // production registry that requires a bearer token even on resolve.
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      failures: { resolve: 'auth' }
    })

    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })
    await runCli(['logout', '--registry', registry.url], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'after-logout')
    await mkdir(installDir, { recursive: true })

    // No --token here — credentials were just cleared by logout.
    const result = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(result.exitCode).toBe(2) // EXIT.auth
    expect(result.stderr.toLowerCase()).toMatch(/auth|401|unauthorized/)
  })
})

// ---------------------------------------------------------------------------
// 2. Full local lifecycle: install → list → remove → list
// ---------------------------------------------------------------------------

describe('cross-command — local lifecycle', () => {
  test('install → list → remove --all → list shows empty', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'lifecycle')
    await mkdir(installDir, { recursive: true })

    await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    const list1 = await runCli(
      ['list', '--registry', registry.url, '--json'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(JSON.parse(list1.stdout).items).toHaveLength(1)

    await runCli(
      ['remove', 'pdf-parser', '--all', '--registry', registry.url],
      { HOME: env.home, USERPROFILE: env.home }
    )

    const list2 = await runCli(
      ['list', '--registry', registry.url, '--json'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(JSON.parse(list2.stdout).items).toHaveLength(0)
  })

  test('install x2 same slug + same dir without --force conflicts; --force succeeds; second install replaces first', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'reinstall-here')
    await mkdir(installDir, { recursive: true })

    const r1 = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(r1.exitCode).toBe(0)

    const r2 = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(r2.exitCode).toBe(4) // EXIT.filesystem (already installed)

    const r3 = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok', '--force'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(r3.exitCode).toBe(0)

    // Inventory has exactly one target, not two duplicates.
    const inv = JSON.parse(
      await readFile(join(env.home, '.skillhub', 'inventory.json'), 'utf-8')
    ) as { items: Array<{ slug: string; targets: Array<{ installDir: string }> }> }
    const item = inv.items.find(i => i.slug === 'pdf-parser')
    expect(item?.targets).toHaveLength(1)
  })

  test('install A then install B (different slugs, same parent dir) → list shows both', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [
        { namespace: 'global', slug: 'a-skill', version: '1.0.0', zipBytes: makeSkillZip() },
        { namespace: 'global', slug: 'b-skill', version: '1.0.0', zipBytes: makeSkillZip() }
      ]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'two-skills')
    await mkdir(installDir, { recursive: true })

    await runCli(
      ['install', 'a-skill', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    await runCli(
      ['install', 'b-skill', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    const list = await runCli(
      ['list', '--registry', registry.url, '--json'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    const items = JSON.parse(list.stdout).items as Array<{ slug: string }>
    expect(items.map(i => i.slug).sort()).toEqual(['a-skill', 'b-skill'])
  })

  test('remove --all → install same slug again succeeds (no stale inventory state)', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'reuse')
    await mkdir(installDir, { recursive: true })

    await runCli(['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })
    await runCli(['remove', 'pdf-parser', '--all', '--registry', registry.url], { HOME: env.home, USERPROFILE: env.home })

    // Re-install at the same dir without --force should now succeed, since
    // the previous install was removed.
    const reinstall = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(reinstall.exitCode).toBe(0)
  })
})

// ---------------------------------------------------------------------------
// 3. Filesystem drift between install dir and inventory
// ---------------------------------------------------------------------------

describe('cross-command — filesystem drift', () => {
  test('install → fs-delete the install dir → list reports status=missing', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'drift')
    await mkdir(installDir, { recursive: true })
    await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    // External clobber: delete the install dir behind the CLI's back.
    await rm(join(installDir, 'pdf-parser'), { recursive: true, force: true })

    const list = await runCli(
      ['list', '--registry', registry.url, '--json'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(list.exitCode).toBe(0)
    const items = JSON.parse(list.stdout).items as Array<{ slug: string; status: string }>
    expect(items[0]?.slug).toBe('pdf-parser')
    expect(items[0]?.status).toBe('missing')
  })

  // After commit a14d89d8 ("refactor(cli): improve doctor command
  // semantics and transparency") doctor switched from REPLACE to MERGE
  // semantics: it never removes inventory entries, even when the install
  // dir on disk is gone. Stale entries are surfaced via `list --json`'s
  // status="missing" instead. This test pins that contract.
  test('install → fs-delete the install dir → doctor preserves the entry; list reports status=missing', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    // Install into an agent-shaped dir under cwd so doctor will scan it.
    const codexSkills = join(env.cwd, '.codex', 'skills')
    await mkdir(codexSkills, { recursive: true })
    await runCli(
      ['install', 'pdf-parser', '--dir', codexSkills, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    // Wipe the install but leave the dir tree shape — metadata gone.
    await rm(join(codexSkills, 'pdf-parser'), { recursive: true, force: true })

    const doctor = await runCli(['doctor', '--json'], { HOME: env.home, USERPROFILE: env.home }, { cwd: env.cwd })
    expect(doctor.exitCode).toBe(0)

    // Inventory still has the entry — doctor preserved it because the
    // installDir was NOT in the (now-empty) scan result.
    const inv = JSON.parse(
      await readFile(join(env.home, '.skillhub', 'inventory.json'), 'utf-8')
    ) as { items: Array<{ slug: string }> }
    expect(inv.items.find(i => i.slug === 'pdf-parser')).toBeDefined()

    // The user-facing surface for "this is gone on disk" is `list` — it
    // reports status="missing" by stat'ing the installDir at read time.
    const list = await runCli(
      ['list', '--registry', registry.url, '--json'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    const items = JSON.parse(list.stdout).items as Array<{ slug: string; status: string }>
    expect(items.find(i => i.slug === 'pdf-parser')?.status).toBe('missing')
  })
})

// ---------------------------------------------------------------------------
// 4. doctor idempotence
// ---------------------------------------------------------------------------

describe('cross-command — doctor idempotence', () => {
  test('two consecutive doctor runs produce identical inventory (idempotent)', async () => {
    const env = await createTempHome()

    // Seed one valid metadata file.
    const metaDir = join(env.cwd, '.codex', 'skills', 'pdf-parser', '.skillhub')
    await mkdir(metaDir, { recursive: true })
    await writeFile(join(metaDir, 'metadata.json'), JSON.stringify({
      registry: 'https://skill.xfyun.cn',
      namespace: 'global',
      slug: 'pdf-parser',
      version: '1.0.0',
      agent: 'codex',
      installedAt: '2026-04-20T12:00:00Z'
    }))

    await runCli(['doctor'], { HOME: env.home, USERPROFILE: env.home }, { cwd: env.cwd })
    const after1 = await readFile(join(env.home, '.skillhub', 'inventory.json'), 'utf-8')

    await runCli(['doctor'], { HOME: env.home, USERPROFILE: env.home }, { cwd: env.cwd })
    const after2 = await readFile(join(env.home, '.skillhub', 'inventory.json'), 'utf-8')

    expect(after2).toBe(after1)
  })
})

// ---------------------------------------------------------------------------
// 5. publish does not change local inventory
// ---------------------------------------------------------------------------

describe('cross-command — publish vs local inventory', () => {
  test('publish does NOT add the published skill to local inventory', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok', user: { handle: 'u', displayName: 'U' } })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    // Build a tiny skill dir to publish.
    const dir = join(env.cwd, 'src-skill')
    await mkdir(dir, { recursive: true })
    await writeFile(join(dir, 'SKILL.md'), '---\nname: pub-only\ndescription: x\n---\n# pub-only')

    const pub = await runCli(['publish', dir, '--registry', registry.url], { HOME: env.home, USERPROFILE: env.home })
    expect(pub.exitCode).toBe(0)

    const list = await runCli(
      ['list', '--registry', registry.url, '--json'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(list.exitCode).toBe(0)
    expect(JSON.parse(list.stdout).items).toHaveLength(0)
  })
})

// ---------------------------------------------------------------------------
// 6. Cross-registry isolation in queries
// ---------------------------------------------------------------------------

describe('cross-command — cross-registry isolation', () => {
  test('list scoped to registry A does not show items installed from registry B', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_a',
      user: { handle: 'a', displayName: 'A' },
      skills: [{ namespace: 'global', slug: 'a-only', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    registryB = await startFakeRegistry({
      token: 'sk_b',
      user: { handle: 'b', displayName: 'B' },
      skills: [{ namespace: 'global', slug: 'b-only', version: '1.0.0', zipBytes: makeSkillZip() }]
    })

    await runCli(['login', '--registry', registry.url, '--token', 'sk_a'], { HOME: env.home, USERPROFILE: env.home })
    await runCli(['login', '--registry', registryB.url, '--token', 'sk_b'], { HOME: env.home, USERPROFILE: env.home })

    const dirA = join(env.cwd, 'A')
    const dirB = join(env.cwd, 'B')
    await mkdir(dirA, { recursive: true })
    await mkdir(dirB, { recursive: true })

    await runCli(['install', 'a-only', '--dir', dirA, '--registry', registry.url, '--token', 'sk_a'], { HOME: env.home, USERPROFILE: env.home })
    await runCli(['install', 'b-only', '--dir', dirB, '--registry', registryB.url, '--token', 'sk_b'], { HOME: env.home, USERPROFILE: env.home })

    const listA = await runCli(['list', '--registry', registry.url, '--json'], { HOME: env.home, USERPROFILE: env.home })
    const slugsA = (JSON.parse(listA.stdout).items as Array<{ slug: string }>).map(i => i.slug)
    expect(slugsA).toEqual(['a-only'])

    const listB = await runCli(['list', '--registry', registryB.url, '--json'], { HOME: env.home, USERPROFILE: env.home })
    const slugsB = (JSON.parse(listB.stdout).items as Array<{ slug: string }>).map(i => i.slug)
    expect(slugsB).toEqual(['b-only'])
  })
})

// ---------------------------------------------------------------------------
// 7. Auto-detect + list filter integration (project-level)
// ---------------------------------------------------------------------------

describe('cross-command — auto-detect + list', () => {
  test('install auto-detects project-level .codex; subsequent list --agent codex shows it', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    // Pre-create .codex/skills so auto-detect picks codex/project-level.
    await mkdir(join(env.cwd, '.codex', 'skills'), { recursive: true })

    const inst = await runCli(
      ['install', 'pdf-parser', '--registry', registry.url, '--token', 'sk_ok', '--json'],
      { HOME: env.home, USERPROFILE: env.home },
      { cwd: env.cwd }
    )
    expect(inst.exitCode).toBe(0)

    const list = await runCli(
      ['list', '--agent', 'codex', '--registry', registry.url, '--json'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(list.exitCode).toBe(0)
    const items = JSON.parse(list.stdout).items as Array<{ slug: string; agent: string }>
    expect(items.some(i => i.slug === 'pdf-parser' && i.agent === 'codex')).toBe(true)
  })
})

// ---------------------------------------------------------------------------
// 8. Inventory metadata corruption resilience after install
// ---------------------------------------------------------------------------

describe('cross-command — metadata.json drift', () => {
  test('install → manually corrupt metadata.json → list reports the row but with sane handling', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'meta-drift')
    await mkdir(installDir, { recursive: true })
    await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    // Corrupt the installed metadata. inventory.json (the authoritative
    // source for `list`) is untouched, so `list` should still work.
    await writeFile(
      join(installDir, 'pdf-parser', '.skillhub', 'metadata.json'),
      '{ truncated'
    )

    const list = await runCli(
      ['list', '--registry', registry.url, '--json'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(list.exitCode).toBe(0)
    const items = JSON.parse(list.stdout).items as Array<{ slug: string; status: string }>
    expect(items[0]?.slug).toBe('pdf-parser')
    // Status remains "ok" because list uses inventory.json, not metadata.json.
    expect(items[0]?.status).toBe('ok')
  })
})

// ---------------------------------------------------------------------------
// 9. Registry priority chain end-to-end
// ---------------------------------------------------------------------------

describe('cross-command — registry priority end-to-end', () => {
  test('search uses --registry over SKILLHUB_REGISTRY env over default', async () => {
    registry = await startFakeRegistry({
      searchItems: [{ namespace: 'global', slug: 'wins', latestVersion: '1.0.0', summary: 'right one' }]
    })
    registryB = await startFakeRegistry({
      searchItems: [{ namespace: 'global', slug: 'loses', latestVersion: '1.0.0', summary: 'wrong one' }]
    })

    const result = await runCli(
      ['search', '', '--registry', registry.url, '--json'],
      { SKILLHUB_REGISTRY: registryB.url }
    )
    expect(result.exitCode).toBe(0)
    const items = JSON.parse(result.stdout).items as Array<{ slug: string }>
    expect(items.map(i => i.slug)).toEqual(['wins'])
  })
})

// ---------------------------------------------------------------------------
// 10. Help / Version ergonomics across commands
// ---------------------------------------------------------------------------

describe('cross-command — help reaches every documented command', () => {
  test('every command listed in help responds to --help with non-empty body', async () => {
    const helpResult = await runCli(['help'])
    expect(helpResult.exitCode).toBe(0)

    const commandNames = [
      'help', 'version', 'login', 'logout', 'whoami',
      'search', 'install', 'list', 'remove', 'doctor',
      'publish', 'update'
    ]
    for (const cmd of commandNames) {
      expect(helpResult.stdout).toContain(cmd)
      const sub = await runCli([cmd, '--help'])
      // --help exits 0 for cac-style CLIs; we don't insist on that, just
      // that some informative output makes it to stdout.
      expect(sub.stdout.length).toBeGreaterThan(0)
    }
  })
})
