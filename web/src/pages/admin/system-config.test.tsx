/** @vitest-environment jsdom */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  getLocalAuth: vi.fn(),
  updateLocalAuth: vi.fn(),
  listRoles: vi.fn(),
  listRoleGrants: vi.fn(),
  createRoleGrant: vi.fn(),
  updateRoleGrant: vi.fn(),
  disableRoleGrant: vi.fn(),
}))

vi.mock('@/api/client', () => ({ systemConfigApi: api }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

import { SystemConfigPage } from './system-config'

describe('SystemConfigPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    Object.values(api).forEach((mock) => mock.mockReset())
  })

  it('warns before disabling login and saves with the observed version', async () => {
    api.getLocalAuth.mockResolvedValue({ passwordLoginEnabled: true, selfRegistrationEnabled: true, version: 3 })
    api.listRoles.mockResolvedValue([{ code: 'SUPER_ADMIN', name: 'Super Admin' }])
    api.listRoleGrants.mockResolvedValue([])
    api.updateLocalAuth.mockResolvedValue({ passwordLoginEnabled: false, selfRegistrationEnabled: true, version: 4 })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(<QueryClientProvider client={client}><SystemConfigPage /></QueryClientProvider>)
    const checkbox = await screen.findByRole('checkbox', { name: 'systemConfig.passwordLogin' })
    fireEvent.click(checkbox)

    expect(window.confirm).toHaveBeenCalledWith('systemConfig.lockoutWarning')
    await waitFor(() => expect(api.updateLocalAuth.mock.calls[0]?.[0]).toEqual({
      passwordLoginEnabled: false,
      selfRegistrationEnabled: true,
      version: 3,
    }))
    await waitFor(() => expect(client.getQueryData(['auth', 'local-capabilities'])).toEqual({
      passwordLoginEnabled: false,
      selfRegistrationEnabled: true,
      registrationAvailable: false,
    }))
  })
})
