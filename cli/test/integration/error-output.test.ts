import { describe, expect, test } from 'bun:test'
import { runCli } from '../helpers/run-cli'

describe('cli error output', () => {
  test('prints gh-style help for unknown commands', async () => {
    const result = await runCli(['foo'])

    expect(result.exitCode).toBe(5)
    expect(result.stderr).toContain('unknown command "foo" for "skillhub"')
    expect(result.stderr).toContain('Usage:  skillhub <command> [flags]')
    expect(result.stderr).toContain('Available commands:')
    expect(result.stderr).toContain('help       Show available commands')
    expect(result.stderr).toContain('publish    Publish a local skill package')
  })

  test('prefers unknown command output when the command is followed by a bad flag', async () => {
    const result = await runCli(['foo', '--bad'])

    expect(result.exitCode).toBe(5)
    expect(result.stderr).toContain('unknown command "foo" for "skillhub"')
    expect(result.stderr).not.toContain('unknown flag: --bad')
  })

  test('prints unknown command as json when --json is requested', async () => {
    const result = await runCli(['foo', '--json'])

    expect(result.exitCode).toBe(5)
    expect(JSON.parse(result.stderr)).toEqual({
      ok: false,
      message: 'unknown command "foo" for "skillhub"',
      exitCode: 5
    })
  })

  test('suggests close matches for mistyped commands', async () => {
    const result = await runCli(['serch'])

    expect(result.exitCode).toBe(5)
    expect(result.stderr).toContain('unknown command "serch" for "skillhub"')
    expect(result.stderr).toContain('Did you mean this?')
    expect(result.stderr).toContain('    search')
  })

  test('prints unknown flag message and command directory', async () => {
    const result = await runCli(['version', '--badflag'])

    expect(result.exitCode).toBe(5)
    expect(result.stderr).toContain('unknown flag: --badflag')
    expect(result.stderr).toContain('Usage:  skillhub <command> [flags]')
    expect(result.stderr).toContain('Available commands:')
    expect(result.stderr).toContain('version    Show installed CLI version')
  })

  test('prints unknown flag as json when --json is requested', async () => {
    const result = await runCli(['version', '--badflag', '--json'])

    expect(result.exitCode).toBe(5)
    expect(JSON.parse(result.stderr)).toEqual({
      ok: false,
      message: 'unknown flag: --badflag',
      exitCode: 5
    })
  })

  test('prints missing argument usage for install', async () => {
    const result = await runCli(['install'])

    expect(result.exitCode).toBe(5)
    expect(result.stderr).toContain('Error: missing required argument')
    expect(result.stderr).toContain('Usage:  skillhub install <slug>')
    expect(result.stderr).toContain('Run "skillhub help install" for more information.')
  })

  test('prints parse errors as json when --json is requested', async () => {
    const result = await runCli(['install', '--json'])

    expect(result.exitCode).toBe(5)
    expect(JSON.parse(result.stderr)).toEqual({
      ok: false,
      message: 'missing required argument',
      exitCode: 5
    })
  })
})
