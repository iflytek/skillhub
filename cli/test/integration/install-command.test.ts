import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { zipSync, strToU8 } from 'fflate'
import { createTempHome } from '../helpers/temp-env'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Build a minimal valid zip containing a SKILL.md file. */
function makeSkillZip(extra: Record<string, string> = {}): Uint8Array {
  const entries: Record<string, Uint8Array> = {
    'SKILL.md': strToU8('# test skill'),
    ...Object.fromEntries(Object.entries(extra).map(([k, v]) => [k, strToU8(v)]))
  }
  return zipSync(entries)
}

let registry: Awaited<ReturnType<typeof startFakeRegistry>> | undefined

afterEach(() => {
  registry?.stop()
  registry = undefined
})

// ---------------------------------------------------------------------------
// P0 — Happy-path install: metadata.json + inventory.json
// ---------------------------------------------------------------------------

describe('install command — P0', () => {
  test('happy-path: exit 0, writes metadata.json and inventory.json', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u1', displayName: 'User One' },
      skills: [
        {
          namespace: 'global',
          slug: 'pdf-parser',
          version: '1.0.0',
          versionId: 1,
          fingerprint: 'abc123',
          zipBytes: makeSkillZip()
        }
      ]
    })

    // Login first so credentials are stored
    const loginResult = await runCli(
      ['login', '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(loginResult.exitCode).toBe(0)

    // The claude-code profile installs into <cwd>/.claude/skills
    // We use --dir to pin the install directory to a known temp path so we
    // can assert on it without depending on agent detection.
    const installDir = join(env.cwd, 'skills')
    await mkdir(installDir, { recursive: true })

    const result = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    expect(result.exitCode).toBe(0)

    // --- metadata.json ---
    const metaPath = join(installDir, 'pdf-parser', '.skillhub', 'metadata.json')
    const meta = JSON.parse(await readFile(metaPath, 'utf-8'))
    expect(meta).toMatchObject({
      registry: registry.url,
      namespace: 'global',
      slug: 'pdf-parser',
      version: '1.0.0'
    })
    expect(typeof meta.installedAt).toBe('string')

    // --- inventory.json ---
    const inventoryPath = join(env.home, '.skillhub', 'inventory.json')
    const inventory = JSON.parse(await readFile(inventoryPath, 'utf-8'))
    expect(inventory.items).toBeArray()
    const item = inventory.items.find(
      (i: { namespace: string; slug: string }) => i.namespace === 'global' && i.slug === 'pdf-parser'
    )
    expect(item).toBeDefined()
    expect(item.targets.length).toBeGreaterThan(0)
    const target = item.targets.find(
      (t: { installDir: string }) => t.installDir === join(installDir, 'pdf-parser')
    )
    expect(target).toBeDefined()
  })

  // -------------------------------------------------------------------------
  // P0 — --json output shape
  // -------------------------------------------------------------------------

  test('--json output matches { ok, namespace, slug, installed }', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u1', displayName: 'User One' },
      skills: [
        {
          namespace: 'global',
          slug: 'pdf-parser',
          version: '1.0.0',
          zipBytes: makeSkillZip()
        }
      ]
    })

    await runCli(
      ['login', '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    const installDir = join(env.cwd, 'skills-json')
    await mkdir(installDir, { recursive: true })

    const result = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok', '--json'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    expect(result.exitCode).toBe(0)
    const parsed = JSON.parse(result.stdout)
    expect(parsed).toMatchObject({
      ok: true,
      namespace: 'global',
      slug: 'pdf-parser'
    })
    expect(Array.isArray(parsed.installed)).toBe(true)
    expect(parsed.installed.length).toBeGreaterThan(0)
    expect(parsed.installed[0]).toHaveProperty('agent')
    expect(parsed.installed[0]).toHaveProperty('dir')
  })
})

// ---------------------------------------------------------------------------
// P1 — --version forwarding
// ---------------------------------------------------------------------------

describe('install command — P1', () => {
  test('--version forwards to resolve and installs the requested version', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u1', displayName: 'User One' },
      skills: [
        {
          namespace: 'global',
          slug: 'pdf-parser',
          // fake-registry returns this as the resolved version regardless of
          // the ?version= query param; we just verify the metadata records it.
          version: '1.0.0',
          zipBytes: makeSkillZip()
        }
      ]
    })

    await runCli(
      ['login', '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    const installDir = join(env.cwd, 'skills-ver')
    await mkdir(installDir, { recursive: true })

    const result = await runCli(
      [
        'install', 'pdf-parser',
        '--version', '1.0.0',
        '--dir', installDir,
        '--registry', registry.url,
        '--token', 'sk_ok'
      ],
      { HOME: env.home, USERPROFILE: env.home }
    )

    expect(result.exitCode).toBe(0)
    expect(registry.received.resolve?.version).toBe('1.0.0')

    const metaPath = join(installDir, 'pdf-parser', '.skillhub', 'metadata.json')
    const meta = JSON.parse(await readFile(metaPath, 'utf-8'))
    expect(meta.version).toBe('1.0.0')
  })

  // -------------------------------------------------------------------------
  // P1 — 401 on resolve → EXIT.auth (exit code 2)
  // -------------------------------------------------------------------------

  test('401 on resolve returns auth exit code and stderr message', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      failures: { resolve: 'auth' }
    })

    const installDir = join(env.cwd, 'skills-auth')
    await mkdir(installDir, { recursive: true })

    const result = await runCli(
      [
        'install', 'pdf-parser',
        '--dir', installDir,
        '--registry', registry.url,
        '--token', 'sk_bad'
      ],
      { HOME: env.home, USERPROFILE: env.home }
    )

    // EXIT.auth = 2
    expect(result.exitCode).toBe(2)
    expect(result.stderr.toLowerCase()).toMatch(/auth|unauthorized|401/)
  })

  // -------------------------------------------------------------------------
  // P1 — --namespace override
  // -------------------------------------------------------------------------

  test('--namespace override installs under the specified namespace', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u1', displayName: 'User One' },
      skills: [
        {
          namespace: 'myteam',
          slug: 'mything',
          version: '2.0.0',
          zipBytes: makeSkillZip()
        }
      ]
    })

    await runCli(
      ['login', '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    const installDir = join(env.cwd, 'skills-ns')
    await mkdir(installDir, { recursive: true })

    const result = await runCli(
      [
        'install', 'mything',
        '--namespace', 'myteam',
        '--dir', installDir,
        '--registry', registry.url,
        '--token', 'sk_ok'
      ],
      { HOME: env.home, USERPROFILE: env.home }
    )

    expect(result.exitCode).toBe(0)

    const metaPath = join(installDir, 'mything', '.skillhub', 'metadata.json')
    const meta = JSON.parse(await readFile(metaPath, 'utf-8'))
    expect(meta.namespace).toBe('myteam')
    expect(meta.slug).toBe('mything')
    expect(meta.version).toBe('2.0.0')
  })

  // -------------------------------------------------------------------------
  // NOTE: multi-target interactive selection (TTY branch) is not tested here
  // because Bun.spawn does not support PTY allocation. The interactive path
  // in resolveInstallTargets() is covered by the unit tests in
  // test/unit/agents/resolver.test.ts.
  // -------------------------------------------------------------------------
})

// ---------------------------------------------------------------------------
// P0/P1 — Conflict & --force handling
// ---------------------------------------------------------------------------

describe('install command — conflict and --force', () => {
  test('re-installing without --force into an existing dir errors with EXIT.filesystem', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u1', displayName: 'User One' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'skills-conflict')
    await mkdir(installDir, { recursive: true })

    const first = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(first.exitCode).toBe(0)

    const second = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(second.exitCode).toBe(4) // EXIT.filesystem
    expect(second.stderr).toContain('already installed')
    expect(second.stderr).toContain('--force')
  })

  test('--force overwrites stale files left in the install dir', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u1', displayName: 'User One' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'skills-force')
    await mkdir(installDir, { recursive: true })
    await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    // Tamper with SKILL.md to prove the second install replaces it.
    const skillFile = join(installDir, 'pdf-parser', 'SKILL.md')
    await writeFile(skillFile, '# tampered content')
    expect(await readFile(skillFile, 'utf-8')).toBe('# tampered content')

    const forced = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok', '--force'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(forced.exitCode).toBe(0)
    expect(await readFile(skillFile, 'utf-8')).toBe('# test skill')
  })
})

// ---------------------------------------------------------------------------
// P1 — Server-side error mapping during install
// ---------------------------------------------------------------------------

describe('install command — server errors', () => {
  test('resolve 404 surfaces an error and aborts install (no metadata.json written)', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      failures: { resolve: 'not_found' }
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'skills-resolve-404')
    await mkdir(installDir, { recursive: true })

    const result = await runCli(
      ['install', 'no-such-slug', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    expect(result.exitCode).not.toBe(0)
    expect(result.stderr).toMatch(/404|not found/i)

    // No metadata file should have been created at the install destination.
    const metaPath = join(installDir, 'no-such-slug', '.skillhub', 'metadata.json')
    expect(await Bun.file(metaPath).exists()).toBe(false)
  })

  // Regression test for the production bug observed on 2026-05-06: server
  // marks `bundle_ready=true` in DB but the bundle file is missing on disk.
  // /resolve returns 200 with a downloadUrl, then /download returns 404. The
  // CLI must surface a non-zero exit and a meaningful stderr — not silently
  // succeed with an empty install dir.
  test('download 404 (resolve OK) is reported as a download failure', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u1', displayName: 'User One' },
      // resolve succeeds (skill is present in fixture list) but the download
      // endpoint is forced to 404 to simulate a missing bundle on storage.
      skills: [{ namespace: 'global', slug: 'orphan-bundle', version: '1.0.0', zipBytes: makeSkillZip() }],
      failures: { download: 'not_found' }
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'skills-bundle-missing')
    await mkdir(installDir, { recursive: true })

    const result = await runCli(
      ['install', 'orphan-bundle', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    expect(result.exitCode).not.toBe(0)
    expect(result.stderr.toLowerCase()).toMatch(/download|404|not found/)
  })

  // -------------------------------------------------------------------------
  // P1 — Path safety: install only writes inside <dir>/<slug>/
  // -------------------------------------------------------------------------

  test('install only writes inside <dir>/<slug>/ — sibling files in <dir> are untouched', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'shared-dir')
    await mkdir(installDir, { recursive: true })
    // Place an unrelated file as a sibling of the future <slug>/ subdir.
    const sibling = join(installDir, 'IMPORTANT.txt')
    await writeFile(sibling, 'this file must survive install')

    const result = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(result.exitCode).toBe(0)

    // Sibling file must still exist with original content.
    expect(await readFile(sibling, 'utf-8')).toBe('this file must survive install')
    // <slug>/ subdir created.
    expect(await Bun.file(join(installDir, 'pdf-parser', 'SKILL.md')).exists()).toBe(true)
  })

  test('--force re-install does not touch sibling files in <dir>', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'shared-force')
    await mkdir(installDir, { recursive: true })
    await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    // After first install, drop a sibling file; --force should not delete it.
    const sibling = join(installDir, 'sibling-after-install.bin')
    await writeFile(sibling, 'sentinel')

    const r2 = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok', '--force'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(r2.exitCode).toBe(0)
    expect(await readFile(sibling, 'utf-8')).toBe('sentinel')
  })

  test('install --dir creates the <slug> subdir even when <dir> is empty', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'empty-dir')
    await mkdir(installDir, { recursive: true })

    const result = await runCli(
      ['install', 'pdf-parser', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(result.exitCode).toBe(0)
    expect(await Bun.file(join(installDir, 'pdf-parser', 'SKILL.md')).exists()).toBe(true)
    expect(await Bun.file(join(installDir, 'pdf-parser', '.skillhub', 'metadata.json')).exists()).toBe(true)
  })

  test('install --dir pointing at a regular file (not a directory) fails before download', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    // Create a file at the location --dir would otherwise treat as a directory.
    const filePath = join(env.cwd, 'not-a-dir')
    await writeFile(filePath, 'i am a file, not a dir')

    const result = await runCli(
      ['install', 'pdf-parser', '--dir', filePath, '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(result.exitCode).not.toBe(0)
    // Original file must still be unchanged (the install should not have
    // scribbled on it before bailing).
    expect(await readFile(filePath, 'utf-8')).toBe('i am a file, not a dir')
  })

  test('--json emits a parseable error envelope when install fails', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      failures: { resolve: 'not_found' }
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const installDir = join(env.cwd, 'skills-json-error')
    await mkdir(installDir, { recursive: true })

    const result = await runCli(
      ['install', 'no-such-slug', '--dir', installDir, '--registry', registry.url, '--token', 'sk_ok', '--json'],
      { HOME: env.home, USERPROFILE: env.home }
    )

    expect(result.exitCode).not.toBe(0)
    // JSON error envelope is printed to stdout (or stderr, depending on the
    // command); we accept either to keep the test resilient to that choice.
    const candidate = result.stdout || result.stderr
    const json = JSON.parse(candidate) as {
      ok: boolean
      message: string
      exitCode: number
    }
    expect(json.ok).toBe(false)
    expect(typeof json.message).toBe('string')
    expect(json.exitCode).toBe(result.exitCode)
  })
})

// ---------------------------------------------------------------------------
// P1 — Multi-agent and auto-detect targeting
// ---------------------------------------------------------------------------

describe('install command — multi-agent & auto-detect', () => {
  test('multi --agent installs the same skill into every specified user-level dir', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const result = await runCli(
      [
        'install', 'pdf-parser',
        '--agent', 'codex',
        '--agent', 'claude-code',
        '--registry', registry.url,
        '--token', 'sk_ok',
        '--json'
      ],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(result.exitCode).toBe(0)
    const parsed = JSON.parse(result.stdout) as { installed: Array<{ agent: string }> }
    const agents = parsed.installed.map(t => t.agent).sort()
    expect(agents).toEqual(['claude-code', 'codex'])

    // Both metadata files exist on disk under user-level <home>/.<agent>/skills.
    expect(await Bun.file(join(env.home, '.codex', 'skills', 'pdf-parser', '.skillhub', 'metadata.json')).exists()).toBe(true)
    expect(await Bun.file(join(env.home, '.claude', 'skills', 'pdf-parser', '.skillhub', 'metadata.json')).exists()).toBe(true)
  })

  test('duplicate --agent dedupes to one target', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const result = await runCli(
      [
        'install', 'pdf-parser',
        '--agent', 'codex',
        '--agent', 'codex',
        '--registry', registry.url,
        '--token', 'sk_ok',
        '--json'
      ],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(result.exitCode).toBe(0)
    const parsed = JSON.parse(result.stdout) as { installed: Array<{ agent: string }> }
    expect(parsed.installed).toHaveLength(1)
    expect(parsed.installed[0]?.agent).toBe('codex')
  })

  test('--agent unknown-id surfaces a usage error with hint', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const result = await runCli(
      ['install', 'pdf-parser', '--agent', 'totally-not-a-real-agent', '--registry', registry.url, '--token', 'sk_ok'],
      { HOME: env.home, USERPROFILE: env.home }
    )
    expect(result.exitCode).toBe(5) // EXIT.usage
    expect(result.stderr.toLowerCase()).toMatch(/unknown agent|--dir/)
  })

  test('auto-detect: cwd with only .codex/skills present installs project-level there', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    // Pre-create the codex skills dir so auto-detect picks project scope.
    await mkdir(join(env.cwd, '.codex', 'skills'), { recursive: true })

    const result = await runCli(
      ['install', 'pdf-parser', '--registry', registry.url, '--token', 'sk_ok', '--json'],
      { HOME: env.home, USERPROFILE: env.home },
      { cwd: env.cwd }
    )
    expect(result.exitCode).toBe(0)

    const parsed = JSON.parse(result.stdout) as { installed: Array<{ dir: string; agent: string }> }
    expect(parsed.installed[0]?.agent).toBe('codex')
    // On macOS env.cwd may resolve through /private/var/... symlinks; assert
    // against the structural part of the path instead of an exact prefix.
    // Use a regex that accepts both Unix (/) and Windows (\) path separators.
    expect(parsed.installed[0]?.dir).toMatch(/[/\\]\.codex[/\\]skills[/\\]pdf-parser/)
    expect(parsed.installed[0]?.dir).not.toContain(env.home) // not user-level
  })

  test('auto-detect: multiple agent dirs in cwd and non-interactive mode fails with hint', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    await mkdir(join(env.cwd, '.codex', 'skills'), { recursive: true })
    await mkdir(join(env.cwd, '.claude', 'skills'), { recursive: true })

    const result = await runCli(
      ['install', 'pdf-parser', '--registry', registry.url, '--token', 'sk_ok', '--json'],
      { HOME: env.home, USERPROFILE: env.home },
      { cwd: env.cwd }
    )
    expect(result.exitCode).toBe(5) // EXIT.usage
    expect(result.stderr.toLowerCase()).toMatch(/multiple install targets|--agent|--dir/)
  })

  test('auto-detect: cwd with no agent dirs falls back to .agents/skills', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({
      token: 'sk_ok',
      user: { handle: 'u', displayName: 'U' },
      skills: [{ namespace: 'global', slug: 'pdf-parser', version: '1.0.0', zipBytes: makeSkillZip() }]
    })
    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

    const result = await runCli(
      ['install', 'pdf-parser', '--registry', registry.url, '--token', 'sk_ok', '--json'],
      { HOME: env.home, USERPROFILE: env.home },
      { cwd: env.cwd }
    )
    expect(result.exitCode).toBe(0)
    const parsed = JSON.parse(result.stdout) as { installed: Array<{ dir: string; agent: string }> }
    expect(parsed.installed[0]?.agent).toBe('generic')
    expect(parsed.installed[0]?.dir).toContain('.agents')
  })

  // -------------------------------------------------------------------------
  // P1 — Bundle integrity: download body that's not a valid zip
  // -------------------------------------------------------------------------
  test('download body that is not a valid zip surfaces an extraction error', async () => {
    const env = await createTempHome()
    // Stand up a custom server that returns valid resolve JSON but plain
    // text on download.
    const server = Bun.serve({
      port: 0,
      fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/auth/whoami') {
          return Response.json({ code: 0, data: { handle: 'u', displayName: 'U' } })
        }
        const baseUrl = `${url.protocol}//${url.host}`
        const resolveMatch = url.pathname.match(/^\/api\/cli\/v1\/skills\/([^/]+)\/([^/]+)\/resolve$/)
        if (resolveMatch && req.method === 'GET') {
          return Response.json({
            code: 0,
            data: {
              namespace: resolveMatch[1],
              slug: resolveMatch[2],
              version: '1.0.0',
              versionId: 1,
              fingerprint: 'deadbeef',
              downloadUrl: `${baseUrl}/api/cli/v1/skills/${resolveMatch[1]}/${resolveMatch[2]}/versions/1.0.0/download`
            }
          })
        }
        if (url.pathname.includes('/download')) {
          // NOT a zip — plain text.
          return new Response('this is plain text, not a zip', {
            status: 200, headers: { 'Content-Type': 'application/zip' }
          })
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    try {
      const url = `http://localhost:${server.port}`
      await runCli(['login', '--registry', url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

      const installDir = join(env.cwd, 'bad-bundle')
      await mkdir(installDir, { recursive: true })

      const result = await runCli(
        ['install', 'pdf-parser', '--dir', installDir, '--registry', url, '--token', 'sk_ok'],
        { HOME: env.home, USERPROFILE: env.home }
      )
      expect(result.exitCode).not.toBe(0)
      // No metadata should have been written.
      expect(await Bun.file(join(installDir, 'pdf-parser', '.skillhub', 'metadata.json')).exists()).toBe(false)
    } finally {
      server.stop()
    }
  })

  // -------------------------------------------------------------------------
  // P2 — Slug edge cases (Unicode, very long)
  // -------------------------------------------------------------------------
  test('slug with non-ASCII characters round-trips through resolve URL (encoded)', async () => {
    const env = await createTempHome()
    let resolveUrl = ''
    const server = Bun.serve({
      port: 0,
      fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/auth/whoami') {
          return Response.json({ code: 0, data: { handle: 'u', displayName: 'U' } })
        }
        if (url.pathname.includes('/resolve')) {
          resolveUrl = req.url
          // Return 404 — we only care that the URL was constructed correctly.
          return Response.json({ code: 404, message: 'not found' }, { status: 404 })
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    try {
      const url = `http://localhost:${server.port}`
      await runCli(['login', '--registry', url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

      const installDir = join(env.cwd, 'unicode-slug')
      await mkdir(installDir, { recursive: true })

      const result = await runCli(
        ['install', '中文-技能', '--dir', installDir, '--registry', url, '--token', 'sk_ok'],
        { HOME: env.home, USERPROFILE: env.home }
      )
      // Server returns 404 — install fails. Just confirm CLI didn't crash
      // before hitting the server.
      expect(result.exitCode).not.toBe(0)
      // The slug must appear URL-percent-encoded in the resolve URL.
      expect(resolveUrl).toMatch(/%E4%B8%AD%E6%96%87/)
    } finally {
      server.stop()
    }
  })

  test('slug 200+ characters is forwarded as-is to /resolve (server is authoritative)', async () => {
    const env = await createTempHome()
    let resolveUrl = ''
    const server = Bun.serve({
      port: 0,
      fetch(req) {
        const url = new URL(req.url)
        if (url.pathname === '/api/cli/v1/auth/whoami') {
          return Response.json({ code: 0, data: { handle: 'u', displayName: 'U' } })
        }
        if (url.pathname.includes('/resolve')) {
          resolveUrl = req.url
          return Response.json({ code: 404, message: 'not found' }, { status: 404 })
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    try {
      const url = `http://localhost:${server.port}`
      await runCli(['login', '--registry', url, '--token', 'sk_ok'], { HOME: env.home, USERPROFILE: env.home })

      const installDir = join(env.cwd, 'long-slug')
      await mkdir(installDir, { recursive: true })

      const longSlug = 'a'.repeat(220)
      const result = await runCli(
        ['install', longSlug, '--dir', installDir, '--registry', url, '--token', 'sk_ok'],
        { HOME: env.home, USERPROFILE: env.home }
      )
      expect(result.exitCode).not.toBe(0)
      expect(resolveUrl).toContain(longSlug)
    } finally {
      server.stop()
    }
  })
})
