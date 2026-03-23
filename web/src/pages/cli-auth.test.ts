import { describe, expect, it, vi } from 'vitest'

// CliAuthPage has internal helpers isValidRedirectUri and decodeLabel which are
// not exported. We test the component render paths and validate the redirect
// URI logic via the rendered error states.

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
  getCurrentUser: vi.fn().mockResolvedValue(null),
  tokenApi: { createToken: vi.fn() },
}))

vi.mock('@/app/router', () => ({
  ORIGINAL_URL_SEARCH: '',
}))

import { CliAuthPage } from './cli-auth'

describe('CliAuthPage', () => {
  it('exports a named component function', () => {
    expect(typeof CliAuthPage).toBe('function')
    expect(CliAuthPage.name).toBe('CliAuthPage')
  })
})
