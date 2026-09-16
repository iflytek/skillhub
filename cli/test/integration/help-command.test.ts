import { describe, expect, test } from 'bun:test'
import { runCli } from '../helpers/run-cli'

describe('help command', () => {
  test('prints detailed help for install', async () => {
    const result = await runCli(['help', 'install'])
    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('Usage: skillhub install <coordinate>')
    expect(result.stdout).toContain('--agent <profile>')
    expect(result.stdout).toContain('--version <v>')
    expect(result.stdout).toContain('--registry <url>')
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
    expect(result.stdout).toContain('--registry <url>')
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

  test('states that Suite commands require a compatible registry', async () => {
    const topic = await runCli(['help', 'suite'])
    expect(topic.exitCode).toBe(0)
    expect(topic.stdout).toContain('Manage Skill Suites on compatible registries')

    const root = await runCli(['--help'])
    expect(root.exitCode).toBe(0)
    expect(root.stdout).toContain('Manage Skill Suites on compatible registries')
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
    expect(sync.stdout).toContain('--namespace <slug>')
    expect(sync.stdout).toContain('--skill <slug>')

    const publish = await runCli(['help', 'publish'])
    expect(publish.exitCode).toBe(0)
    expect(publish.stdout).toContain('--dry-run')
    expect(publish.stdout).toContain('--registry <url>')
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

  test('help --json returns a parseable command directory', async () => {
    const result = await runCli(['help', '--json'])
    expect(result.exitCode).toBe(0)
    expect(result.stderr).toBe('')
    expect(JSON.parse(result.stdout)).toMatchObject({
      ok: true,
      commands: expect.arrayContaining([
        { name: 'install', description: 'Install a skill locally' }
      ])
    })
  })

  test('help <topic> --json returns parseable command detail', async () => {
    const result = await runCli(['help', 'install', '--json'])
    expect(result.exitCode).toBe(0)
    expect(result.stderr).toBe('')
    expect(JSON.parse(result.stdout)).toMatchObject({
      ok: true,
      command: 'install',
      summary: 'Install a skill locally'
    })
  })

  test('help <unknown-topic> returns a clear usage error', async () => {
    const result = await runCli(['help', 'definitely-not-a-command'])
    expect(result.exitCode).toBe(5)
    expect(result.stderr).toContain('unknown help topic: definitely-not-a-command')
    expect(result.stderr).not.toContain('TypeError')
  })

  test('help <unknown-topic> --json returns a structured error only on stderr', async () => {
    const result = await runCli(['help', 'definitely-not-a-command', '--json'])
    expect(result.exitCode).toBe(5)
    expect(result.stdout).toBe('')
    expect(JSON.parse(result.stderr)).toMatchObject({
      ok: false,
      message: 'unknown help topic: definitely-not-a-command',
      exitCode: 5
    })
  })
})
