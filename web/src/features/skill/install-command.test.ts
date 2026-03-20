import { afterEach, describe, expect, it } from 'vitest'
import { buildInstallCommand, buildInstallTarget, getRegistryUrl } from './install-command'

describe('install-command', () => {
  const originalWindow = globalThis.window

  afterEach(() => {
    if (originalWindow) {
      globalThis.window = originalWindow
      return
    }
    Reflect.deleteProperty(globalThis, 'window')
  })

  it('uses the plain slug for the global namespace', () => {
    expect(buildInstallTarget('global', 'my-skill')).toBe('my-skill')
    expect(buildInstallCommand('global', 'my-skill', 'https://skill.xfyun.cn')).toBe(
      'npx clawhub install my-skill --registry https://skill.xfyun.cn',
    )
  })

  it('prefixes non-global namespaces in the install target', () => {
    expect(buildInstallTarget('team-alpha', 'my-skill')).toBe('team-alpha--my-skill')
    expect(buildInstallCommand('team-alpha', 'my-skill', 'https://skill.xfyun.cn')).toBe(
      'npx clawhub install team-alpha--my-skill --registry https://skill.xfyun.cn',
    )
  })

  it('prefers the runtime registry url when available', () => {
    globalThis.window = {
      __SKILLHUB_RUNTIME_CONFIG__: {
        registryUrl: 'https://registry.example.com',
        appBaseUrl: 'https://app.example.com',
      },
      location: {
        protocol: 'https:',
        host: 'fallback.example.com',
      },
    } as Window

    expect(getRegistryUrl()).toBe('https://registry.example.com')
  })

  it('falls back to the app base url and then the browser origin', () => {
    globalThis.window = {
      __SKILLHUB_RUNTIME_CONFIG__: {
        appBaseUrl: 'https://app.example.com',
      },
      location: {
        protocol: 'https:',
        host: 'fallback.example.com',
      },
    } as Window
    expect(getRegistryUrl()).toBe('https://app.example.com')

    globalThis.window = {
      __SKILLHUB_RUNTIME_CONFIG__: {},
      location: {
        protocol: 'https:',
        host: 'fallback.example.com',
      },
    } as Window
    expect(getRegistryUrl()).toBe('https://fallback.example.com')
  })
})
