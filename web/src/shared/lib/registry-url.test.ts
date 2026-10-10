import { describe, expect, it } from 'vitest'
import { resolvePublicRegistryUrl } from './registry-url'

describe('resolvePublicRegistryUrl', () => {
  it('keeps the deployment base path when the runtime public URL is unavailable', () => {
    expect(resolvePublicRegistryUrl('', 'https://registry.example.com', '/skillhub/'))
      .toBe('https://registry.example.com/skillhub')
  })

  it('uses the browser origin plus base path when the configured URL is localhost', () => {
    expect(resolvePublicRegistryUrl('http://localhost:3000', 'https://registry.example.com', '/skillhub/'))
      .toBe('https://registry.example.com/skillhub')
  })

  it('uses a configured non-localhost public URL without a trailing slash', () => {
    expect(resolvePublicRegistryUrl('https://registry.example.com/skillhub/', 'https://ignored.example.com', '/skillhub/'))
      .toBe('https://registry.example.com/skillhub')
  })
})
