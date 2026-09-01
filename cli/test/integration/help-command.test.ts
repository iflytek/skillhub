import { describe, expect, test } from 'bun:test'
import { runCli } from '../helpers/run-cli'

describe('help command', () => {
  test('prints detailed help for install', async () => {
    const result = await runCli(['help', 'install'])
    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('Usage: skillhub install <coordinate>')
    expect(result.stdout).toContain('--agent <profile>')
    expect(result.stdout).toContain('@team/my-skill')
    expect(result.stdout).toContain('team/my-skill')
    expect(result.stdout).toContain('team--my-skill')
  })

  test('prints namespaced local remove contract in command help', async () => {
    const result = await runCli(['help', 'remove'])
    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('Usage: skillhub remove <coordinate>')
    expect(result.stdout).toContain('skillhub remove team/my-skill')
    expect(result.stdout).toContain('skillhub remove my-skill --namespace team')
  })

  test('prints namespaced local remove contract in --help', async () => {
    const result = await runCli(['remove', '--help'])
    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('remove <coordinate>')
    expect(result.stdout).toContain('Namespace for local or remote delete')
  })

  test('prints search help with optional query', async () => {
    const result = await runCli(['help', 'search'])
    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('Usage: skillhub search [query]')
    expect(result.stdout).toContain('skillhub search')
  })

  test('distinguishes skill upgrade from CLI self-update and namespace sync', async () => {
    const upgrade = await runCli(['help', 'upgrade'])
    expect(upgrade.exitCode).toBe(0)
    expect(upgrade.stdout).toContain('Upgrade explicitly selected installed skills')
    expect(upgrade.stdout).toContain('skillhub upgrade <coordinate...>')
    expect(upgrade.stdout).toContain('--check')
    expect(upgrade.stdout).toContain('--force')

    const update = await runCli(['help', 'update'])
    expect(update.exitCode).toBe(0)
    expect(update.stdout).toContain('Check or update CLI itself')

    const sync = await runCli(['help', 'sync'])
    expect(sync.exitCode).toBe(0)
    expect(sync.stdout).toContain('namespace workspaces')
  })

  // P1: bare `skillhub help` (no topic) prints the directory of all commands
  test('bare help lists all commands in human format', async () => {
    const result = await runCli(['help'])
    expect(result.exitCode).toBe(0)
    // Sample at least 6 of the 12 known commands appear in the output
    for (const name of ['login', 'logout', 'search', 'install', 'list', 'publish']) {
      expect(result.stdout).toContain(name)
    }
  })

  // P1: `skillhub help --json` is wired in cac but the --json flag is consumed
  // by the action wrapper and never reaches helpCommand's args. Today this
  // makes the JSON branch unreachable from the CLI surface (helpCommand always
  // sees [] or [topic] without --json). We document the current human-only
  // behavior here so a future source fix that re-routes --json into
  // helpCommand will fail this test loudly and we can convert it into a
  // positive JSON assertion at that time.
  // TODO source bug: cli/src/index.ts:178 should forward --json into helpCommand args.
  test('help --json currently returns human directory (documents source bug)', async () => {
    const result = await runCli(['help', '--json'])
    expect(result.exitCode).toBe(0)
    // Output is NOT valid JSON today.
    let isJson = true
    try { JSON.parse(result.stdout) } catch { isJson = false }
    expect(isJson).toBe(false)
    // Sanity: human output still mentions some commands
    expect(result.stdout).toContain('install')
  })

  test('help <topic> --json currently returns human topic detail (documents source bug)', async () => {
    const result = await runCli(['help', 'install', '--json'])
    expect(result.exitCode).toBe(0)
    let isJson = true
    try { JSON.parse(result.stdout) } catch { isJson = false }
    expect(isJson).toBe(false)
    expect(result.stdout).toContain('Usage: skillhub install')
  })

  // P1: `skillhub help <unknown>` currently crashes inside helpCommand because
  // `commands[topic]` is undefined and `detail.usage` dereferences undefined.
  // We assert non-zero exit so that a future fix to graceful handling does not
  // regress silently. TODO source bug: cli/src/commands/help.ts:75 should
  // surface a friendlier "unknown command" message instead of crashing.
  test('help <unknown-topic> exits non-zero (documents current crashy behavior)', async () => {
    const result = await runCli(['help', 'definitely-not-a-command'])
    expect(result.exitCode).not.toBe(0)
  })
})
