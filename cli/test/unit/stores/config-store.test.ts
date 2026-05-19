import { describe, expect, test } from 'bun:test'
import { mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { ConfigStore } from '../../../src/stores/config-store'

function makeTempHome() {
  return mkdtemp(join(tmpdir(), 'skillhub-store-test-'))
}

describe('ConfigStore', () => {
  test('read() returns empty object when file missing', async () => {
    const home = await makeTempHome()
    const store = new ConfigStore(home)

    const config = await store.read()
    expect(config).toEqual({})
  })

  test('write() then read() round-trips', async () => {
    const home = await makeTempHome()
    const store = new ConfigStore(home)

    const original = { registry: 'https://example.com', defaultAgent: 'codex' }
    await store.write(original)

    const config = await store.read()
    expect(config).toEqual(original)
  })

  test('setRegistry() merges into existing config', async () => {
    const home = await makeTempHome()
    const store = new ConfigStore(home)

    // Write initial config with defaultAgent only
    await store.write({ defaultAgent: 'codex' })

    // setRegistry should merge, not overwrite
    await store.setRegistry('https://new.com')

    const config = await store.read()
    expect(config.registry).toBe('https://new.com')
    expect(config.defaultAgent).toBe('codex')
  })
})
