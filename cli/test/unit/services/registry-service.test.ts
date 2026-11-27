import { describe, expect, test } from 'bun:test'
import { resolveRegistry, resolveToken, resolveTokenWithSource } from '../../../src/services/registry-service'

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

  test('resolves the token source by cli arg, env, credentials order', () => {
    expect(resolveTokenWithSource({ token: 'arg' }, { SKILLHUB_TOKEN: 'env' }, 'stored'))
      .toEqual({ token: 'arg', source: 'argument' })
    expect(resolveTokenWithSource({}, { SKILLHUB_TOKEN: 'env' }, 'stored'))
      .toEqual({ token: 'env', source: 'environment' })
    expect(resolveTokenWithSource({}, {}, 'stored')).toEqual({ token: 'stored', source: 'stored' })
    expect(resolveTokenWithSource({}, {}, undefined)).toBeUndefined()
  })

  test('treats an empty token argument as absent, as resolveToken does', () => {
    expect(resolveTokenWithSource({ token: '' }, { SKILLHUB_TOKEN: 'env' }, 'stored'))
      .toEqual({ token: 'env', source: 'environment' })
    expect(resolveToken({ token: '' }, { SKILLHUB_TOKEN: 'env' }, 'stored')).toBe('env')
  })

  test('keeps resolveToken equivalent to the source-aware resolver', () => {
    const cases: Array<[{ token?: string }, NodeJS.ProcessEnv, string | undefined]> = [
      [{ token: 'arg' }, { SKILLHUB_TOKEN: 'env' }, 'stored'],
      [{}, { SKILLHUB_TOKEN: 'env' }, 'stored'],
      [{}, {}, 'stored'],
      [{}, {}, undefined]
    ]

    for (const [args, env, stored] of cases) {
      expect(resolveToken(args, env, stored)).toBe(resolveTokenWithSource(args, env, stored)?.token)
    }
  })
})
