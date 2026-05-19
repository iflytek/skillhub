import { describe, expect, test } from 'bun:test'
import { CLI_VERSION } from '../../../src/shared/constants'
import { CliError } from '../../../src/shared/errors'
import { updateCommand, type UpdateCommandDeps } from '../../../src/commands/update'
import type { InstallMode } from '../../../src/platform/package-manager'
import type { UpdaterRunResult } from '../../../src/platform/updater'

// Branch coverage for `updateCommand`. Integration tests hit the live npm
// registry and accept any output, so they don't verify the four output
// branches of the command wrapper. We drive each branch by injecting fake
// deps via the command's optional `deps` parameter — no process-global
// module mocks, so tests cannot leak across files.
//
// Branches under test (see cli/src/commands/update.ts):
//   1. !result.available       -> "already up to date"
//   2. result.updated          -> "Updated skillhub X -> Y"
//   3. result.error            -> throws CliError
//   4. available && !updated   -> "Update available" + optional next hint

interface FakeState {
  latest: string
  mode: InstallMode
  runResult: UpdaterRunResult
}

function buildDeps(state: FakeState): Required<UpdateCommandDeps> {
  return {
    latestVersion: async () => state.latest,
    detectInstallMode: () => state.mode,
    run: async () => state.runResult
  }
}

describe('updateCommand branches', () => {
  test('up-to-date branch (human)', async () => {
    const deps = buildDeps({ latest: CLI_VERSION, mode: 'npm-global', runResult: { success: true, output: '' } })
    const out = await updateCommand({}, deps)
    expect(out).toContain('Already up to date')
    expect(out).toContain(CLI_VERSION)
  })

  test('up-to-date branch (--json)', async () => {
    const deps = buildDeps({ latest: CLI_VERSION, mode: 'npm-global', runResult: { success: true, output: '' } })
    const out = await updateCommand({ json: true }, deps)
    expect(JSON.parse(out)).toEqual({ ok: true, upToDate: true, version: CLI_VERSION })
  })

  test('updated success branch — npm-global with run() success (human)', async () => {
    const deps = buildDeps({ latest: '99.0.0', mode: 'npm-global', runResult: { success: true, output: '' } })
    const out = await updateCommand({}, deps)
    expect(out).toContain(`Updated skillhub ${CLI_VERSION} -> 99.0.0`)
  })

  test('updated success branch — npm-global with run() success (--json)', async () => {
    const deps = buildDeps({ latest: '99.0.0', mode: 'npm-global', runResult: { success: true, output: '' } })
    const out = await updateCommand({ json: true }, deps)
    expect(JSON.parse(out)).toEqual({ ok: true, updated: true, from: CLI_VERSION, to: '99.0.0' })
  })

  test('available-not-updated branch — npx mode emits next hint (human)', async () => {
    const deps = buildDeps({ latest: '99.0.0', mode: 'npx', runResult: { success: true, output: '' } })
    const out = await updateCommand({}, deps)
    expect(out).toContain(`Update available: ${CLI_VERSION} -> 99.0.0`)
    expect(out).toContain('npx @astron-team/skillhub')
  })

  test('available-not-updated branch — npx mode (--json)', async () => {
    const deps = buildDeps({ latest: '99.0.0', mode: 'npx', runResult: { success: true, output: '' } })
    const out = await updateCommand({ json: true }, deps)
    const parsed = JSON.parse(out)
    expect(parsed.ok).toBe(true)
    expect(parsed.available).toBe(true)
    expect(parsed.from).toBe(CLI_VERSION)
    expect(parsed.to).toBe('99.0.0')
    expect(typeof parsed.next).toBe('string')
    expect(parsed.next).toContain('npx @astron-team/skillhub')
  })

  test('error branch — npm-global with run() failure throws CliError', async () => {
    const deps = buildDeps({
      latest: '99.0.0',
      mode: 'npm-global',
      runResult: { success: false, output: 'install command failed: permission denied' }
    })
    let caught: unknown
    try {
      await updateCommand({}, deps)
    } catch (err) {
      caught = err
    }
    expect(caught).toBeInstanceOf(CliError)
    expect((caught as CliError).message).toContain('install command failed')
  })

  // -------------------------------------------------------------------------
  // bun-global success branch — symmetric to npm-global success but routes
  // through `bun add -g` rather than `npm install -g`. Command-level output
  // still uses the same "Updated skillhub X -> Y" copy, but the run dep
  // receives a different argv. We assert both: (a) the command captured the
  // bun argv on the dep and (b) the output formatting matches.
  // -------------------------------------------------------------------------
  test('updated success branch — bun-global with run() success captures bun argv (human + json)', async () => {
    const calls: string[][] = []
    const deps: Required<UpdateCommandDeps> = {
      latestVersion: async () => '99.0.0',
      detectInstallMode: () => 'bun-global',
      run: async (cmd) => {
        calls.push([...cmd])
        return { success: true, output: '' }
      }
    }

    const human = await updateCommand({}, deps)
    expect(human).toContain(`Updated skillhub ${CLI_VERSION} -> 99.0.0`)

    const json = await updateCommand({ json: true }, deps)
    expect(JSON.parse(json)).toEqual({ ok: true, updated: true, from: CLI_VERSION, to: '99.0.0' })

    // Both invocations must have routed through `bun add -g` — never `npm`.
    expect(calls).toHaveLength(2)
    for (const cmd of calls) {
      expect(cmd[0]).toBe('bun')
      expect(cmd[1]).toBe('add')
      expect(cmd[2]).toBe('-g')
      expect(cmd[3]).toMatch(/@latest$/)
    }
  })

  // -------------------------------------------------------------------------
  // unknown install mode — falls through to the manual-upgrade hint. The
  // command must NOT invoke run() (we'd be guessing the package manager),
  // and the human output must spell out both npm and bun fallbacks so the
  // user can pick whichever is on their system.
  // -------------------------------------------------------------------------
  test('available-not-updated branch — unknown mode emits manual upgrade hint and never calls run()', async () => {
    let runCalls = 0
    const deps: Required<UpdateCommandDeps> = {
      latestVersion: async () => '99.0.0',
      detectInstallMode: () => 'unknown',
      run: async () => {
        runCalls += 1
        return { success: true, output: '' }
      }
    }

    const human = await updateCommand({}, deps)
    expect(human).toContain(`Update available: ${CLI_VERSION} -> 99.0.0`)
    expect(human).toContain('npm install -g')
    expect(human).toContain('bun add -g')

    const json = await updateCommand({ json: true }, deps)
    const parsed = JSON.parse(json)
    expect(parsed.ok).toBe(true)
    expect(parsed.available).toBe(true)
    expect(typeof parsed.next).toBe('string')
    expect(parsed.next).toMatch(/npm install -g.*bun add -g|bun add -g.*npm install -g/)

    // Neither invocation should have attempted to spawn an installer.
    expect(runCalls).toBe(0)
  })

  // -------------------------------------------------------------------------
  // checkOnly short-circuit — even in install modes that WOULD execute an
  // upgrade (npm-global, bun-global), passing { check: true } must short
  // circuit the service before it reaches `run()`. Output is the bare
  // "Update available" line with no `next` hint (next is only populated
  // for npx / unknown branches, not on checkOnly's early return).
  // -------------------------------------------------------------------------
  test('checkOnly with npm-global short-circuits and never calls run()', async () => {
    let runCalls = 0
    const deps: Required<UpdateCommandDeps> = {
      latestVersion: async () => '99.0.0',
      detectInstallMode: () => 'npm-global',
      run: async () => {
        runCalls += 1
        return { success: true, output: '' }
      }
    }

    const human = await updateCommand({ check: true }, deps)
    expect(human.trim()).toBe(`Update available: ${CLI_VERSION} -> 99.0.0`)

    const json = await updateCommand({ check: true, json: true }, deps)
    const parsed = JSON.parse(json)
    expect(parsed.ok).toBe(true)
    expect(parsed.available).toBe(true)
    // No `next` hint on the checkOnly path.
    expect(parsed.next).toBeUndefined()

    expect(runCalls).toBe(0)
  })

  // -------------------------------------------------------------------------
  // P1 — Version comparison edge cases. semver.gt drives the available
  // gate; these tests pin the boundary where "no upgrade" must hold.
  // -------------------------------------------------------------------------
  test('latest version equal to current is reported as up-to-date', async () => {
    const deps = buildDeps({
      latest: CLI_VERSION,
      mode: 'npm-global',
      runResult: { success: true, output: '' }
    })
    const human = await updateCommand({}, deps)
    expect(human).toContain('Already up to date')
  })

  test('latest version older than current is treated as up-to-date (no downgrade)', async () => {
    const deps = buildDeps({
      latest: '0.0.1',
      mode: 'npm-global',
      runResult: { success: true, output: '' }
    })
    const human = await updateCommand({}, deps)
    expect(human).toContain('Already up to date')
    // Make sure we didn't accidentally invoke npm with an older version.
    const jsonOut = await updateCommand({ json: true }, deps)
    expect(JSON.parse(jsonOut).updated).toBeUndefined()
  })

  test('checkOnly + already up-to-date emits a non-available envelope', async () => {
    const deps = buildDeps({
      latest: CLI_VERSION,
      mode: 'unknown',
      runResult: { success: true, output: '' }
    })
    const json = await updateCommand({ check: true, json: true }, deps)
    const parsed = JSON.parse(json)
    expect(parsed).toMatchObject({ ok: true, upToDate: true, version: CLI_VERSION })
  })

  test('bun-global run() failure surfaces the failure output as a CliError message', async () => {
    const deps = buildDeps({
      latest: '99.0.0',
      mode: 'bun-global',
      runResult: { success: false, output: 'bun add: permission denied' }
    })
    let caught: unknown
    try {
      await updateCommand({}, deps)
    } catch (err) {
      caught = err
    }
    expect(caught).toBeInstanceOf(CliError)
    expect((caught as CliError).message).toContain('bun add')
  })

  test('checkOnly with npx emits Update-available envelope WITHOUT a next hint (next is mode-specific)', async () => {
    // Per service code, npx's `next` is added only when checkOnly is false.
    // checkOnly returns earlier and never enters the mode switch.
    const deps = buildDeps({
      latest: '99.0.0',
      mode: 'npx',
      runResult: { success: true, output: '' }
    })
    const json = await updateCommand({ check: true, json: true }, deps)
    const parsed = JSON.parse(json)
    expect(parsed.ok).toBe(true)
    expect(parsed.available).toBe(true)
    expect(parsed.next).toBeUndefined()
    expect(parsed.from).toBe(CLI_VERSION)
    expect(parsed.to).toBe('99.0.0')
  })
})
