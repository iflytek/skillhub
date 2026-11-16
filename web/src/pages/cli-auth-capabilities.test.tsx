// @vitest-environment jsdom
import { StrictMode } from 'react'
import { cleanup, render, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'
import en from '@/i18n/locales/en.json'
import zh from '@/i18n/locales/zh.json'
import ko from '@/i18n/locales/ko.json'
import ru from '@/i18n/locales/ru.json'

const api = vi.hoisted(() => ({
  getCurrentUser: vi.fn().mockResolvedValue({ userId: 'user', displayName: 'User' }),
  createToken: vi.fn().mockRejectedValue(new Error('Test issuance failure')),
}))
const translate = (key: string) => key
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => vi.fn() }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: translate }) }))
vi.mock('@/api/client', () => ({
  getCurrentUser: api.getCurrentUser,
  getAppBaseUrl: () => '',
  tokenApi: { createToken: api.createToken },
}))
vi.mock('@/app/router', () => ({
  ORIGINAL_URL_SEARCH: '?redirect_uri=http%3A%2F%2F127.0.0.1%3A4321%2Fcallback&state=test&label=CLI',
}))
import { CliAuthPage } from './cli-auth'

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

it('browser login requests only read+submit once even under StrictMode and does not retry issuance', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: true } } })
  render(<QueryClientProvider client={client}><StrictMode><CliAuthPage /></StrictMode></QueryClientProvider>)
  await waitFor(() => expect(api.createToken).toHaveBeenCalledTimes(1))
  expect(api.createToken.mock.calls[0]?.[0]).toEqual({ name: 'CLI', scopes: ['skill:read', 'skill:submit'] })
})

it('all existing locales provide presets, capability labels and legacy display text', () => {
  for (const locale of [en, zh, ko, ru]) {
    expect(Object.keys(locale.createToken.presets).sort()).toEqual(['candidate', 'custom', 'private', 'readonly'])
    expect(Object.values(locale.token.capabilityLabels).every(value => value.length > 0)).toBe(true)
    expect(locale.token.legacyCompat.length).toBeGreaterThan(0)
  }
})
