// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthMethod } from '@/api/types'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: unknown }) => children,
  useNavigate: () => vi.fn(),
  useSearch: () => ({ returnTo: '' }),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, options?: { name?: string }) => {
        if (key === 'loginButton.loginWith') {
          return `Continue with ${options?.name ?? 'provider'}`
        }
        return key
      },
      i18n: { resolvedLanguage: 'en' },
    }),
  }
})

vi.mock('lucide-react', () => ({
  Eye: () => null,
  EyeOff: () => null,
}))

vi.mock('@/api/client', () => ({
  getDirectAuthRuntimeConfig: () => ({ enabled: false }),
}))

vi.mock('@/features/auth/session-bootstrap-entry', () => ({
  SessionBootstrapEntry: () => null,
}))

const authMethods: AuthMethod[] = [
  {
    id: 'oauth-google',
    methodType: 'OAUTH_REDIRECT',
    provider: 'google',
    displayName: 'Google',
    actionUrl: '/oauth2/authorization/google?returnTo=%2F',
  },
  {
    id: 'oauth-github',
    methodType: 'OAUTH_REDIRECT',
    provider: 'github',
    displayName: 'GitHub',
    actionUrl: '/oauth2/authorization/github?returnTo=%2F',
  },
]

vi.mock('@/features/auth/use-auth-methods', () => ({
  useAuthMethods: () => ({ data: authMethods, isLoading: false }),
}))

vi.mock('@/features/auth/use-password-login', () => ({
  usePasswordLogin: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
    error: null,
  }),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children, ...props }: { children: unknown } & Record<string, unknown>) => <button {...props}>{children}</button>,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: (props: Record<string, unknown>) => <input {...props} />,
}))

vi.mock('@/shared/ui/tabs', () => ({
  Tabs: ({ children }: { children: unknown }) => <div>{children}</div>,
  TabsContent: ({ children }: { children: unknown }) => <div>{children}</div>,
  TabsList: ({ children }: { children: unknown }) => <div>{children}</div>,
  TabsTrigger: ({ children }: { children: unknown }) => <button type="button">{children}</button>,
}))

import { LoginPage } from './login'

describe('LoginPage', () => {
  const originalLocation = window.location

  beforeEach(() => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { href: 'http://localhost/login' },
    })
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation,
    })
  })

  it('exports a named component function', () => {
    expect(typeof LoginPage).toBe('function')
  })

  it('renders the login title and form elements', () => {
    render(<LoginPage />)

    expect(screen.getByText('login.title')).toBeTruthy()
    expect(screen.getByText('login.subtitle')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'login.submit' })).toBeTruthy()
  })

  it('renders_google_provider_and_redirects_to_google_action_on_oauth_tab', () => {
    render(<LoginPage />)

    const googleButton = screen.getByRole('button', { name: 'Continue with Google' })
    fireEvent.click(googleButton)

    expect(googleButton).toBeTruthy()
    expect(window.location.href).toBe('/oauth2/authorization/google?returnTo=%2F')
  })
})
