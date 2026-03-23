import { describe, expect, it, vi } from 'vitest'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/features/auth/use-account-merge', () => ({
  useInitiateAccountMerge: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useVerifyAccountMerge: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useConfirmAccountMerge: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('@/shared/lib/error-display', () => ({
  truncateErrorMessage: (v: string) => v,
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: unknown }) => children,
  CardContent: ({ children }: { children: unknown }) => children,
  CardDescription: ({ children }: { children: unknown }) => children,
  CardHeader: ({ children }: { children: unknown }) => children,
  CardTitle: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: () => null,
}))

import { AccountSettingsPage } from './accounts'

describe('AccountSettingsPage', () => {
  it('exports a named component function', () => {
    expect(typeof AccountSettingsPage).toBe('function')
  })
})
