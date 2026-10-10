import { describe, expect, test } from 'bun:test'
import { detectInstallMode } from '../../../src/platform/package-manager'

describe('detectInstallMode', () => {
  test('detects npx from _npx/ in argv', () => {
    const result = detectInstallMode(['/usr/bin/bun', '/tmp/_npx/abc/skillhub'], {})
    expect(result).toBe('npx')
  })

  test('detects npm-global from npm_config_prefix', () => {
    const result = detectInstallMode(
      ['/usr/bin/bun', '/usr/local/lib/node_modules/.bin/skillhub'],
      { npm_config_prefix: '/usr/local' }
    )
    expect(result).toBe('npm-global')
  })

  test('detects bun-global from BUN_INSTALL', () => {
    const result = detectInstallMode(
      ['/usr/bin/bun', '/home/user/.bun/bin/skillhub'],
      { BUN_INSTALL: '/home/user/.bun' }
    )
    expect(result).toBe('bun-global')
  })

  test('returns unknown as fallback', () => {
    const result = detectInstallMode(['/usr/bin/bun', '/random/path/skillhub'], {})
    expect(result).toBe('unknown')
  })
})
