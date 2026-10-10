import { describe, expect, test } from 'bun:test'
import { runUpdateCommand } from '../../../src/platform/updater'

describe('runUpdateCommand', () => {
  test('succeeds with node --version', async () => {
    const result = await runUpdateCommand(['node', '--version'])
    expect(result.success).toBe(true)
    expect(result.output).toMatch(/v\d+\.\d+/)
  })

  test('fails on invalid node flag', async () => {
    const result = await runUpdateCommand(['node', '--invalid-flag-xyz-12345'])
    expect(result.success).toBe(false)
  })

  test('returns failure for empty command', async () => {
    const result = await runUpdateCommand([])
    expect(result.success).toBe(false)
    expect(result.output).toBe('empty update command')
  })

  test('handles spawn error for missing binary', async () => {
    const result = await runUpdateCommand(['nonexistent-binary-xyz-12345'])
    expect(result.success).toBe(false)
  })
})
