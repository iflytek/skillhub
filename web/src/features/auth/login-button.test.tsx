/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AuthMethod } from '@/api/types'
import { AuthMethodButtonList } from './login-button'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, values?: Record<string, string>) => (
        key === 'loginButton.loginWith' && values?.name ? `Login with ${values.name}` : key
      ),
    }),
  }
})

vi.mock('@/shared/lib/base-path', () => ({
  BASE_PATH: '/',
  withBasePath: (path: string) => path,
}))

vi.mock('./use-auth-methods', () => ({
  useAuthMethods: () => ({ data: [], isLoading: false }),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) => (
    <button type="button" {...props}>{children}</button>
  ),
}))

const methods: AuthMethod[] = [
  {
    id: 'github',
    methodType: 'OAUTH_REDIRECT',
    provider: 'github',
    displayName: 'GitHub',
    actionUrl: '/oauth2/authorization/github',
  },
  {
    id: 'feishu',
    methodType: 'OAUTH_REDIRECT',
    provider: 'feishu',
    displayName: 'Feishu',
    actionUrl: '/oauth2/authorization/feishu',
  },
  {
    id: 'password',
    methodType: 'PASSWORD',
    provider: 'local',
    displayName: 'Password',
    actionUrl: '/login',
  },
]

describe('AuthMethodButtonList', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('renders OAuth redirect methods from backend configuration only', () => {
    render(<AuthMethodButtonList methods={methods} />)

    expect(screen.getByText('Login with GitHub')).toBeTruthy()
    expect(screen.getByText('Login with Feishu')).toBeTruthy()
    expect(screen.queryByText('Login with Password')).toBeNull()
  })

  it('renders the supplied empty fallback when no OAuth methods are available', () => {
    render(<AuthMethodButtonList methods={methods.filter((method) => method.methodType !== 'OAUTH_REDIRECT')} emptyFallback={<p>No methods</p>} />)

    expect(screen.getByText('No methods')).toBeTruthy()
  })

  it('navigates to the configured OAuth action URL', () => {
    vi.stubGlobal('location', { href: 'http://localhost/login' })

    render(<AuthMethodButtonList methods={methods.slice(0, 1)} />)
    fireEvent.click(screen.getByText('Login with GitHub'))

    expect(window.location.href).toBe('/oauth2/authorization/github')
  })
})
