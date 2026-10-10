import { describe, expect, it, vi } from 'vitest'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/api/client', () => ({
  getAppBaseUrl: vi.fn().mockReturnValue(''),
  getCurrentUser: vi.fn().mockResolvedValue(null),
  tokenApi: { createToken: vi.fn() },
}))

vi.mock('@/app/router', () => ({
  ORIGINAL_URL_SEARCH: '',
}))

import { CliAuthPage, resolveCliRegistryUrl, resolveLoopbackRedirectUri } from './cli-auth'

describe('resolveLoopbackRedirectUri', () => {
  it.each([
    'http://localhost:4312/callback?source=cli',
    'http://127.0.0.1:4312/callback',
    'http://[::1]:4312/callback',
  ])('accepts an HTTP loopback callback: %s', (uri) => {
    expect(resolveLoopbackRedirectUri(uri)?.href).toBe(uri)
  })

  it.each([
    'https://localhost:4312/callback',
    'http://localhost.example.com/callback',
    'http://example.com/callback',
    'http://user:password@localhost:4312/callback',
    'javascript:alert(1)',
    'not-a-url',
  ])('rejects a non-loopback or unsafe callback: %s', (uri) => {
    expect(resolveLoopbackRedirectUri(uri)).toBeNull()
  })

  it('removes an attacker-provided fragment before adding CLI credentials', () => {
    expect(resolveLoopbackRedirectUri('http://localhost:4312/callback#attacker')?.hash).toBe('')
  })
})

describe('resolveCliRegistryUrl', () => {
  it('uses the configured public base URL for the CLI registry', () => {
    expect(resolveCliRegistryUrl('https://example.com/skillhub', 'https://example.com', '/skillhub/'))
      .toBe('https://example.com/skillhub')
  })

  it('falls back to the browser origin plus the Vite base path', () => {
    expect(resolveCliRegistryUrl('', 'https://example.com', '/skillhub/'))
      .toBe('https://example.com/skillhub')
  })
})

describe('CliAuthPage', () => {
  it('exports a named component function', () => {
    expect(typeof CliAuthPage).toBe('function')
    expect(CliAuthPage.name).toBe('CliAuthPage')
  })
})
