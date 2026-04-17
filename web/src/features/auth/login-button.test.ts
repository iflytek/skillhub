// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { createElement } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthMethod } from '@/api/types'
import { LoginButton } from './login-button'

const mockUseAuthMethods = vi.fn<(returnTo?: string) => { data?: AuthMethod[], isLoading?: boolean }>()

vi.mock('./use-auth-methods', () => ({
  useAuthMethods: (returnTo?: string) => mockUseAuthMethods(returnTo),
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
        if (key === 'loginButton.loading') {
          return 'Loading providers...'
        }
        if (key === 'loginButton.providerUnavailable') {
          return 'Provider unavailable'
        }
        return key
      },
    }),
  }
})

const oauthMethods: AuthMethod[] = [
  {
    id: 'oauth-google',
    methodType: 'OAUTH_REDIRECT',
    provider: 'google',
    displayName: 'Google',
    actionUrl: '/oauth2/authorization/google?returnTo=%2Fworkbench',
  },
  {
    id: 'oauth-github',
    methodType: 'OAUTH_REDIRECT',
    provider: 'github',
    displayName: 'GitHub',
    actionUrl: '/oauth2/authorization/github?returnTo=%2Fworkbench',
  },
  {
    id: 'password',
    methodType: 'PASSWORD',
    provider: 'local',
    displayName: 'Password',
    actionUrl: '/api/v1/auth/login',
  },
]

describe('LoginButton', () => {
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

  it('test_render_multiple_oauth_providers_when_methods_include_google_and_github', () => {
    mockUseAuthMethods.mockReturnValue({
      data: oauthMethods,
      isLoading: false,
    })

    render(createElement(LoginButton, { returnTo: '/workbench' }))

    const googleButton = screen.getByRole('button', { name: 'Continue with Google' })
    const githubButton = screen.getByRole('button', { name: 'Continue with GitHub' })

    expect(googleButton).toBeTruthy()
    expect(githubButton).toBeTruthy()
    expect(googleButton.getAttribute('data-provider-id')).toBe('oauth-google')
    expect(githubButton.getAttribute('data-provider-id')).toBe('oauth-github')
  })

  it('test_redirect_to_provider_action_url_when_click_google_button', () => {
    mockUseAuthMethods.mockReturnValue({
      data: oauthMethods,
      isLoading: false,
    })

    render(createElement(LoginButton, { returnTo: '/workbench' }))

    fireEvent.click(screen.getByRole('button', { name: 'Continue with Google' }))

    expect(window.location.href).toBe('/oauth2/authorization/google?returnTo=%2Fworkbench')
  })

  it('test_hide_non_oauth_methods_when_methods_include_password', () => {
    mockUseAuthMethods.mockReturnValue({
      data: oauthMethods,
      isLoading: false,
    })

    render(createElement(LoginButton, { returnTo: '/workbench' }))

    expect(screen.queryByRole('button', { name: 'Continue with Password' })).toBeNull()
  })

  it('test_report_contract_drift_when_google_id_does_not_target_google_registration', () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    mockUseAuthMethods.mockReturnValue({
      data: [
        {
          id: 'oauth-google',
          methodType: 'OAUTH_REDIRECT',
          provider: 'google',
          displayName: 'Google',
          actionUrl: '/oauth2/authorization/github?returnTo=%2Fworkbench',
        },
      ],
      isLoading: false,
    })

    render(createElement(LoginButton, { returnTo: '/workbench' }))

    expect(errorSpy).toHaveBeenCalledWith(expect.stringContaining('oauth_google_contract_drift'), expect.any(Object))
    expect(screen.getByRole('button', { name: 'Provider unavailable' })).toBeTruthy()

    errorSpy.mockRestore()
  })
})
