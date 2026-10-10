import type { InputHTMLAttributes, ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const useAuthMock = vi.hoisted(() => vi.fn())

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ setQueryData: vi.fn() }),
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
  ApiError: class ApiError extends Error {
    status?: number
  },
  authApi: {
    changePassword: vi.fn(),
    logout: vi.fn(),
  },
}))

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: useAuthMock,
}))

vi.mock('@/shared/lib/error-display', () => ({
  truncateErrorMessage: (v: string) => v,
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({
    children,
    disabled,
    type,
  }: {
    children: ReactNode
    disabled?: boolean
    type?: 'button' | 'submit' | 'reset'
  }) => (
    <button type={type} disabled={disabled}>
      {children}
    </button>
  ),
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: ReactNode }) => children,
  CardContent: ({ children }: { children: ReactNode }) => children,
  CardDescription: ({ children }: { children: ReactNode }) => children,
  CardHeader: ({ children }: { children: ReactNode }) => children,
  CardTitle: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: (props: InputHTMLAttributes<HTMLInputElement>) => <input {...props} />,
}))

import { SecuritySettingsPage } from './security'

beforeEach(() => {
  useAuthMock.mockReturnValue({
    user: {
      userId: 'user-1',
      displayName: 'Local User',
      platformRoles: ['USER'],
      canChangePassword: true,
    },
  })
})

describe('SecuritySettingsPage', () => {
  it('exports a named component function', () => {
    expect(typeof SecuritySettingsPage).toBe('function')
  })

  it('renders the password form when password changes are allowed', () => {
    const html = renderToStaticMarkup(<SecuritySettingsPage />)

    expect(html).toContain('security.currentPassword')
    expect(html).toContain('security.newPassword')
    expect(html).toContain('security.submit')
  })

  it('renders a read-only unavailable state when password changes are not allowed', () => {
    useAuthMock.mockReturnValue({
      user: {
        userId: 'oauth-user',
        displayName: 'OAuth User',
        oauthProvider: 'github',
        platformRoles: ['USER'],
        canChangePassword: false,
      },
    })

    const html = renderToStaticMarkup(<SecuritySettingsPage />)

    expect(html).toContain('security.unavailableTitle')
    expect(html).toContain('security.unavailableDescription')
    expect(html).not.toContain('security.currentPassword')
    expect(html).not.toContain('security.submit')
  })

  it('defaults to the unavailable state while the user capability is unknown', () => {
    useAuthMock.mockReturnValue({ user: null })

    const html = renderToStaticMarkup(<SecuritySettingsPage />)

    expect(html).toContain('security.unavailableTitle')
    expect(html).not.toContain('security.currentPassword')
    expect(html).not.toContain('security.submit')
  })
})
