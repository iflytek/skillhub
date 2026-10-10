import { describe, expect, test } from 'bun:test'
import { resolveRegistry, resolveToken } from '../../../src/services/registry-service'

describe('registry-service', () => {
  test('resolves registry by cli arg, env, config, default order', () => {
    expect(resolveRegistry({ registry: 'https://arg.example.com' }, {}, { registry: 'https://config.example.com' }))
      .toBe('https://arg.example.com')
    expect(resolveRegistry({}, { SKILLHUB_REGISTRY: 'https://env.example.com' }, { registry: 'https://config.example.com' }))
      .toBe('https://env.example.com')
    expect(resolveRegistry({}, {}, { registry: 'https://config.example.com' })).toBe('https://config.example.com')
    expect(resolveRegistry({}, {}, {})).toBe('https://skill.xfyun.cn')
  })

  test('resolves token by cli arg, env, credentials order', () => {
    expect(resolveToken({ token: 'arg' }, {}, 'stored')).toBe('arg')
    expect(resolveToken({}, { SKILLHUB_TOKEN: 'env' }, 'stored')).toBe('env')
    expect(resolveToken({}, {}, 'stored')).toBe('stored')
  })
})
