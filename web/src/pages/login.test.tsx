import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthMethod } from '@/api/types'

const useAuthMethodsMock = vi.fn<(returnTo?: string) => { data: AuthMethod[] }>(() => ({ data: [] }))
const useSearchMock = vi.fn(() => ({ returnTo: '' }))

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: ReactNode }) => children,
  useNavigate: () => vi.fn(),
  useSearch: () => useSearchMock(),
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
  Eye: () => null,
  EyeOff: () => null,
}))

vi.mock('@/api/client', () => ({
  authApi: {
    getMe: vi.fn(),
  },
  buildAuthRedirectUrl: (url: string) => url,
  getDirectAuthRuntimeConfig: () => ({ enabled: false }),
}))

vi.mock('@/features/auth/login-button', () => ({
  LoginButton: () => null,
}))

vi.mock('@/features/auth/session-bootstrap-entry', () => ({
  SessionBootstrapEntry: () => null,
}))

vi.mock('@/features/auth/use-auth-methods', () => ({
  useAuthMethods: (returnTo?: string) => useAuthMethodsMock(returnTo),
}))

vi.mock('@/features/auth/use-password-login', () => ({
  usePasswordLogin: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
    error: null,
  }),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: ReactNode }) => <button type="button">{children}</button>,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: () => <input />,
}))

vi.mock('@/shared/ui/tabs', () => ({
  Tabs: ({ children }: { children: ReactNode }) => children,
  TabsContent: ({ children }: { children: ReactNode }) => children,
  TabsList: ({ children }: { children: ReactNode }) => children,
  TabsTrigger: ({ children }: { children: ReactNode }) => children,
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { LoginPage } from './login'

describe('LoginPage', () => {
  beforeEach(() => {
    useAuthMethodsMock.mockReset()
    useAuthMethodsMock.mockReturnValue({ data: [] })
    useSearchMock.mockReset()
    useSearchMock.mockReturnValue({ returnTo: '' })
  })

  it('exports a named component function', () => {
    expect(typeof LoginPage).toBe('function')
  })

  it('renders the login title and form elements', () => {
    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).toContain('login.title')
    expect(html).toContain('login.subtitle')
    expect(html).toContain('login.submit')
  })

  it('renders the enterprise redirect entry when UASS login is available', () => {
    useSearchMock.mockReturnValue({ returnTo: '/dashboard' })
    useAuthMethodsMock.mockReturnValue({
      data: [{
        id: 'uass-enterprise',
        methodType: 'UASS_REDIRECT',
        provider: 'uass',
        displayName: 'Enterprise SSO',
        actionUrl: '/api/v1/auth/uass?returnTo=%2Fdashboard',
      }],
    })

    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).toContain('Enterprise SSO')
    expect(html).toContain('login.enterpriseRedirectHint')
    expect(html).toContain('login.enterpriseRedirectAction')
    expect(html).toContain('login.tabPassword')
    expect(html).toContain('login.tabOAuth')
    expect(useAuthMethodsMock).toHaveBeenCalledWith('/dashboard')
  })
})
