import { describe, expect, it, vi } from 'vitest'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: unknown }) => children,
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

vi.mock('@/api/client', () => ({
  authApi: {
    requestPasswordReset: vi.fn(),
    confirmPasswordReset: vi.fn(),
  },
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

import { renderToStaticMarkup } from 'react-dom/server'
import { ResetPasswordPage } from './reset-password'

describe('ResetPasswordPage', () => {
  it('exports a named component function', () => {
    expect(typeof ResetPasswordPage).toBe('function')
  })

  it('renders reset-password title and submit action', () => {
    const html = renderToStaticMarkup(<ResetPasswordPage />)
    expect(html).toContain('resetPassword.title')
    expect(html).toContain('resetPassword.sendCode')
    expect(html).toContain('resetPassword.submit')
  })
})
