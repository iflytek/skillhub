/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach } from 'vitest'
import { describe, expect, it, vi } from 'vitest'

const LOCAL_METHODS = vi.hoisted(() => [
  { id: 'local-password', methodType: 'PASSWORD' },
  { id: 'local-registration', methodType: 'PASSWORD_REGISTRATION' },
])

const authMethodsFixture = vi.hoisted(() => ({
  methods: LOCAL_METHODS as Array<{ id: string, methodType: string }>,
  bootstrapEnabled: false,
  directEnabled: false,
  isError: false,
  returnTo: '',
  navigate: vi.fn(),
  mutateAsync: vi.fn(),
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: unknown }) => children,
  useNavigate: () => authMethodsFixture.navigate,
  useSearch: () => ({ returnTo: authMethodsFixture.returnTo }),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { resolvedLanguage: 'en' },
    }),
  }
})

vi.mock('lucide-react', () => ({
  ArrowRight: () => null,
  Eye: () => null,
  EyeOff: () => null,
  LockKeyhole: () => null,
  UserRound: () => null,
}))

vi.mock('@/api/client', () => ({
  getDirectAuthRuntimeConfig: () => ({ enabled: authMethodsFixture.directEnabled, provider: 'local' }),
  getSessionBootstrapRuntimeConfig: () => ({ enabled: authMethodsFixture.bootstrapEnabled, provider: 'proxy' }),
}))

vi.mock('@/features/auth/auth-shell', () => ({
  AuthShell: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/features/auth/login-button', () => ({
  AuthMethodButtonList: () => null,
  LoginButton: () => null,
}))

vi.mock('@/features/auth/session-bootstrap-entry', () => ({
  SessionBootstrapEntry: () => <p>session bootstrap</p>,
}))

vi.mock('@/features/auth/use-auth-methods', () => ({
  useAuthMethods: () => ({ data: authMethodsFixture.methods, isError: authMethodsFixture.isError }),
}))

vi.mock('@/features/auth/use-password-login', () => ({
  usePasswordLogin: () => ({
    mutateAsync: authMethodsFixture.mutateAsync,
    isPending: false,
    error: null,
  }),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) => <button {...props}>{children}</button>,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: (props: React.InputHTMLAttributes<HTMLInputElement>) => <input {...props} />,
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { LoginPage } from './login'

describe('LoginPage', () => {
  afterEach(() => {
    cleanup()
    authMethodsFixture.methods = LOCAL_METHODS
    authMethodsFixture.bootstrapEnabled = false
    authMethodsFixture.directEnabled = false
    authMethodsFixture.isError = false
    authMethodsFixture.returnTo = ''
    authMethodsFixture.navigate.mockClear()
    authMethodsFixture.mutateAsync.mockClear()
  })

  it('exports a named component function', () => {
    expect(typeof LoginPage).toBe('function')
  })

  it('renders the login title and form elements', () => {
    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).toContain('login.title')
    expect(html).toContain('login.subtitle')
    expect(html).toContain('login.submit')
    expect(html).not.toContain('login.tabEnterprise')
    expect(html).toContain('login.register')
  })

  it('does not expose password routing details when direct login is configured', () => {
    authMethodsFixture.directEnabled = true
    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).toContain('login.submit')
    expect(html).not.toContain('login.passwordCompatHint')
  })

  it('returns to the home page after direct login without an explicit destination', async () => {
    render(<LoginPage />)
    fireEvent.change(screen.getByLabelText('login.username'), { target: { value: 'user1' } })
    fireEvent.change(screen.getByLabelText('login.password'), { target: { value: 'password' } })
    fireEvent.click(screen.getByRole('button', { name: 'login.submit' }))
    await waitFor(() => expect(authMethodsFixture.navigate).toHaveBeenCalledWith({ to: '/' }))
  })

  it('returns to the original local page after direct login', async () => {
    authMethodsFixture.returnTo = '/skills?tab=mine'
    render(<LoginPage />)
    fireEvent.change(screen.getByLabelText('login.username'), { target: { value: 'user1' } })
    fireEvent.change(screen.getByLabelText('login.password'), { target: { value: 'password' } })
    fireEvent.click(screen.getByRole('button', { name: 'login.submit' }))
    await waitFor(() => expect(authMethodsFixture.navigate).toHaveBeenCalledWith({ to: '/skills?tab=mine' }))
  })

  it('replaces the password form with configured session bootstrap and can switch back', () => {
    authMethodsFixture.bootstrapEnabled = true
    render(<LoginPage />)
    const personal = screen.getByRole('button', { name: 'login.tabPersonal' })
    const organization = screen.getByRole('button', { name: 'login.tabEnterprise' })

    expect(personal.getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByLabelText('login.password').closest('[hidden]')).toBeNull()

    fireEvent.click(organization)
    expect(organization.getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByLabelText('login.password').closest('[hidden]')).not.toBeNull()
    expect(screen.getByText('session bootstrap').closest('[hidden]')).toBeNull()

    fireEvent.click(personal)
    expect(screen.getByLabelText('login.password').closest('[hidden]')).toBeNull()
    expect(screen.getByText('session bootstrap').closest('[hidden]')).not.toBeNull()
  })

  it('does not show an organization entry when bootstrap is not configured', () => {
    render(<LoginPage />)

    expect(screen.queryByRole('button', { name: 'login.tabEnterprise' })).toBeNull()
    expect(screen.queryByRole('alert')).toBeNull()
    expect(screen.getByLabelText('login.password').closest('[hidden]')).toBeNull()
  })

  it('keeps password login available without a warning when the method catalog fails', () => {
    authMethodsFixture.methods = []
    authMethodsFixture.isError = true
    render(<LoginPage />)

    expect(screen.queryByRole('alert')).toBeNull()
    expect(screen.queryByRole('button', { name: 'login.tabEnterprise' })).toBeNull()
    expect(screen.getByRole('button', { name: 'login.submit' })).toBeTruthy()
  })

  it('hides an unusable session bootstrap entry when the web runtime is not configured', () => {
    authMethodsFixture.methods = [{ id: 'proxy', methodType: 'SESSION_BOOTSTRAP' }]
    render(<LoginPage />)

    expect(screen.queryByRole('button', { name: 'login.tabEnterprise' })).toBeNull()
    expect(screen.queryByText('session bootstrap')).toBeNull()
  })

  it('hides the password form and sign-up link when the server disables local accounts', () => {
    authMethodsFixture.methods = [{ id: 'oauth-oidc', methodType: 'OAUTH_REDIRECT' }]
    render(<LoginPage />)

    expect(screen.queryByLabelText('login.password')).toBeNull()
    expect(screen.queryByRole('button', { name: 'login.submit' })).toBeNull()
    expect(screen.queryByText('login.register')).toBeNull()
  })

  it('keeps password login but hides sign-up when registration is closed', () => {
    authMethodsFixture.methods = [{ id: 'local-password', methodType: 'PASSWORD' }]
    render(<LoginPage />)

    expect(screen.getByRole('button', { name: 'login.submit' })).toBeTruthy()
    expect(screen.queryByText('login.register')).toBeNull()
  })

  it('shows session bootstrap directly when it is the only configured entry', () => {
    authMethodsFixture.methods = [{ id: 'bootstrap-proxy', methodType: 'SESSION_BOOTSTRAP' }]
    authMethodsFixture.bootstrapEnabled = true
    render(<LoginPage />)

    expect(screen.queryByRole('button', { name: 'login.tabEnterprise' })).toBeNull()
    expect(screen.queryByLabelText('login.password')).toBeNull()
    expect(screen.getByText('session bootstrap').closest('[hidden]')).toBeNull()
  })

  it('preserves configured session bootstrap when the method catalog is unavailable', () => {
    authMethodsFixture.methods = []
    authMethodsFixture.bootstrapEnabled = true
    authMethodsFixture.isError = true
    render(<LoginPage />)

    fireEvent.click(screen.getByRole('button', { name: 'login.tabEnterprise' }))
    expect(screen.getByText('session bootstrap').closest('[hidden]')).toBeNull()
  })
})
