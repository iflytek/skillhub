import { describe, expect, test } from 'bun:test'
import { stat } from 'node:fs/promises'
import { mkdtemp, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { applyCredentialPermissions, userStateDir } from '../../../src/platform/paths'

describe('userStateDir', () => {
  test('throws when home is empty string', () => {
    expect(() => userStateDir('')).toThrow('Cannot resolve user home directory')
  })
})

describe('applyCredentialPermissions', () => {
  test('sets 0o600 on unix', async () => {
    const tempDir = await mkdtemp(join(tmpdir(), 'skillhub-test-'))
    const tempFile = join(tempDir, 'credential.json')
    await writeFile(tempFile, '{}')

    await applyCredentialPermissions(tempFile)

    if (process.platform !== 'win32') {
      const stats = await stat(tempFile)
      expect(stats.mode & 0o777).toBe(0o600)
    }
  })
})
