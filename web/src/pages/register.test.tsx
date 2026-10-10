import { describe, expect, it, vi } from 'vitest'

const authMethodsFixture = vi.hoisted(() => ({
  methods: [] as Array<{ id: string, methodType: string }>,
  isLoading: false,
  registrationAvailable: true,
}))

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
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/features/auth/auth-shell', () => ({
  AuthShell: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/features/auth/login-button', () => ({
  AuthMethodButtonList: () => <span>OAuth buttons</span>,
}))

vi.mock('@/features/auth/use-auth-methods', () => ({
  useAuthMethods: () => ({ data: authMethodsFixture.methods, isLoading: authMethodsFixture.isLoading }),
}))

vi.mock('@/features/auth/use-local-auth-capabilities', () => ({
  useLocalAuthCapabilities: () => ({
    data: { passwordLoginEnabled: true, selfRegistrationEnabled: authMethodsFixture.registrationAvailable,
      registrationAvailable: authMethodsFixture.registrationAvailable },
    isError: false,
  }),
}))

vi.mock('@/features/auth/use-local-auth', () => ({
  useLocalRegister: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
    error: null,
  }),
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

vi.mock('@/shared/ui/tabs', () => ({
  Tabs: ({ children }: { children: unknown }) => children,
  TabsContent: ({ children }: { children: unknown }) => children,
  TabsList: ({ children }: { children: unknown }) => children,
  TabsTrigger: ({ children }: { children: unknown }) => children,
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { RegisterPage } from './register'

describe('RegisterPage', () => {
  it('exports a named component function', () => {
    expect(typeof RegisterPage).toBe('function')
  })

  it('renders the registration title and form fields', () => {
    const html = renderToStaticMarkup(<RegisterPage />)

    expect(html).toContain('register.title')
    expect(html).toContain('register.subtitle')
    expect(html).toContain('register.submit')
    expect(html).not.toContain('register.oauthHint')
  })

  it('shows OAuth entry only when providers are advertised', () => {
    authMethodsFixture.methods = [{ id: 'github', methodType: 'OAUTH_REDIRECT' }]
    const html = renderToStaticMarkup(<RegisterPage />)

    expect(html).toContain('register.oauthHint')
    expect(html).toContain('OAuth buttons')
    authMethodsFixture.methods = []
  })

  it('hides local registration when disabled', () => {
    authMethodsFixture.registrationAvailable = false
    const html = renderToStaticMarkup(<RegisterPage />)
    expect(html).not.toContain('register.submit')
    expect(html).toContain('systemConfig.registrationDisabled')
    authMethodsFixture.registrationAvailable = true
  })
})
