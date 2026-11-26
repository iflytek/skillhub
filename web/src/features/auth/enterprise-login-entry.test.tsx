/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { EnterpriseLoginEntry } from './enterprise-login-entry'

const { discoverEnterprise } = vi.hoisted(() => ({ discoverEnterprise: vi.fn() }))

vi.mock('@/api/client', () => ({
  authApi: { discoverEnterprise },
  ApiError: class ApiError extends Error {},
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

describe('EnterpriseLoginEntry', () => {
  afterEach(() => {
    cleanup()
    discoverEnterprise.mockReset()
  })

  it('lists opaque organization actions after discovery', async () => {
    discoverEnterprise.mockResolvedValueOnce({
      publicMethods: [],
      organizations: [{
        slug: 'acme',
        displayName: 'Acme',
        loginOptions: [{ displayName: 'Acme SSO', actionUrl: '/api/v1/auth/enterprise/opaque/start' }],
      }],
    })

    render(<EnterpriseLoginEntry returnTo="/dashboard" />)
    fireEvent.change(screen.getByLabelText('login.enterpriseIdentifier'), { target: { value: 'acme' } })
    fireEvent.click(screen.getByRole('button', { name: 'login.enterpriseFind' }))

    await waitFor(() => expect(screen.getByRole('button', { name: 'Acme SSO' })).toBeTruthy())
    expect(discoverEnterprise).toHaveBeenCalledWith('acme', '/dashboard')
  })

  it('does not expose a backend error body when discovery fails', async () => {
    discoverEnterprise.mockRejectedValueOnce(new Error('internal detail'))
    render(<EnterpriseLoginEntry />)
    fireEvent.change(screen.getByLabelText('login.enterpriseIdentifier'), { target: { value: 'acme' } })
    fireEvent.click(screen.getByRole('button', { name: 'login.enterpriseFind' }))

    await waitFor(() => expect(screen.getByRole('alert').textContent).toBe('login.enterpriseDiscoveryUnavailable'))
    expect(screen.queryByText('internal detail')).toBeNull()
  })
})
