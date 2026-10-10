import { describe, expect, test } from 'bun:test'
import { readFile } from 'node:fs/promises'
import { normalize } from 'node:path'
import { createTempHome } from '../../helpers/temp-env'
import { CredentialsStore } from '../../../src/stores/credentials-store'

describe('CredentialsStore', () => {
  test('stores tokens under user home .skillhub only', async () => {
    const env = await createTempHome()
    const store = new CredentialsStore(env.home)
    await store.setToken('https://registry.example.com', 'sk_test')
    expect(await store.getToken('https://registry.example.com')).toBe('sk_test')
    expect(normalize(store.path)).toBe(normalize(`${env.home}/.skillhub/credentials.json`))
    expect(await Bun.file(`${env.cwd}/credentials.json`).exists()).toBe(false)
  })

  test('treats a third-party credentials file without tokens as logged out', async () => {
    const env = await createTempHome()
    const store = new CredentialsStore(env.home)
    await Bun.write(store.path, JSON.stringify({
      user: { token: 'third-party-token', host: 'https://api.skillhub.cn' }
    }))

    expect(await store.getToken('https://registry.example.com')).toBeUndefined()
  })

  test('setToken preserves third-party and unknown fields', async () => {
    const env = await createTempHome()
    const store = new CredentialsStore(env.home)
    await Bun.write(store.path, JSON.stringify({
      user: { token: 'third-party-token', host: 'https://api.skillhub.cn' },
      futureField: { enabled: true }
    }))

    await store.setToken('https://registry.example.com', 'sk_test')

    const saved = JSON.parse(await readFile(store.path, 'utf-8'))
    expect(saved).toEqual({
      user: { token: 'third-party-token', host: 'https://api.skillhub.cn' },
      futureField: { enabled: true },
      tokens: { 'https://registry.example.com': 'sk_test' }
    })
  })

  test('deleteToken removes only the selected first-party token', async () => {
    const env = await createTempHome()
    const store = new CredentialsStore(env.home)
    await Bun.write(store.path, JSON.stringify({
      user: { token: 'third-party-token', host: 'https://api.skillhub.cn' },
      tokens: {
        'https://registry-a.example.com': 'sk_a',
        'https://registry-b.example.com': 'sk_b'
      }
    }))

    await store.deleteToken('https://registry-a.example.com')

    const saved = JSON.parse(await readFile(store.path, 'utf-8'))
    expect(saved).toEqual({
      user: { token: 'third-party-token', host: 'https://api.skillhub.cn' },
      tokens: { 'https://registry-b.example.com': 'sk_b' }
    })
  })
})
