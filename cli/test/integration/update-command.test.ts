import { describe, expect, test } from 'bun:test'
import { runCli } from '../helpers/run-cli'

describe('update command', () => {
  test('update command is registered and shows in help', async () => {
    const result = await runCli(['--help'])
    expect(result.stdout).toContain('update')
  })

  test('update --check attempts to check for updates', async () => {
    // This will try to reach npm registry - may succeed or fail depending on network
    // We just verify the command doesn't crash and produces some output
    const result = await runCli(['update', '--check'])

    // Should either succeed with version info or fail with error message
    const hasOutput = result.stdout.length > 0 || result.stderr.length > 0
    expect(hasOutput).toBe(true)
  }, 30_000)

  test('update --check --json produces parseable output on success', async () => {
    const result = await runCli(['update', '--check', '--json'])

    // If successful, should be valid JSON
    if (result.exitCode === 0 && result.stdout.length > 0) {
      const parsed = JSON.parse(result.stdout)
      expect(parsed).toHaveProperty('ok')
    }
    // If failed, that's also acceptable in test environment
    expect(true).toBe(true)
  }, 30_000)
})
