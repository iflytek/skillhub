/** @vitest-environment jsdom */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
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

vi.mock('@/api/client', () => ({
  systemConfigApi: api,
  ApiError: class ApiError extends Error {
    constructor(message: string, public status: number) { super(message) }
  },
}))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

import { SystemConfigPage } from './system-config'

describe('SystemConfigPage', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
    Object.values(api).forEach((mock) => mock.mockReset())
  })

  function setup(ruleList: unknown[] = []) {
    api.getLocalAuth.mockResolvedValue({ passwordLoginEnabled: true, selfRegistrationEnabled: true, version: 3 })
    api.listRoles.mockResolvedValue([{ code: 'SUPER_ADMIN', name: 'Super Admin' }, { code: 'USER', name: 'User' }])
    api.listRoleGrants.mockResolvedValue(ruleList)
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><SystemConfigPage /></QueryClientProvider>)
    return client
  }

  it('stages changes, allows discard, and confirms lockout before saving the observed version', async () => {
    api.updateLocalAuth.mockResolvedValue({ passwordLoginEnabled: false, selfRegistrationEnabled: true, version: 4 })
    const client = setup()
    const checkbox = await screen.findByRole('checkbox', { name: 'systemConfig.passwordLogin' })
    fireEvent.click(checkbox)
    expect(api.updateLocalAuth).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'systemConfig.discard' }))
    expect((checkbox as HTMLInputElement).checked).toBe(true)
    fireEvent.click(checkbox)
    fireEvent.click(screen.getByRole('button', { name: 'systemConfig.saveChanges' }))
    const dialog = await screen.findByRole('dialog', { name: 'systemConfig.lockoutTitle' })
    expect(api.updateLocalAuth).not.toHaveBeenCalled()
    fireEvent.click(within(dialog).getByRole('button', { name: 'dialog.cancel' }))
    expect((checkbox as HTMLInputElement).checked).toBe(false)
    fireEvent.click(screen.getByRole('button', { name: 'systemConfig.saveChanges' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'systemConfig.confirmDisableLogin' }))
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

  it('keeps the confirmation open when saving fails so the administrator can retry', async () => {
    api.updateLocalAuth.mockRejectedValueOnce(new Error('Version conflict'))
      .mockResolvedValueOnce({ passwordLoginEnabled: false, selfRegistrationEnabled: true, version: 4 })
    setup()
    const checkbox = await screen.findByRole('checkbox', { name: 'systemConfig.passwordLogin' })
    fireEvent.click(checkbox)
    fireEvent.click(screen.getByRole('button', { name: 'systemConfig.saveChanges' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'systemConfig.confirmDisableLogin' }))
    expect((await within(screen.getByRole('dialog')).findByRole('alert')).textContent).toBe('Version conflict')
    expect(screen.getByRole('dialog')).not.toBeNull()
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'systemConfig.confirmDisableLogin' }))
    await waitFor(() => expect(api.updateLocalAuth).toHaveBeenCalledTimes(2))
  })

  it('saves a registration change only after Save, without a lockout confirmation', async () => {
    api.updateLocalAuth.mockResolvedValue({ passwordLoginEnabled: true, selfRegistrationEnabled: false, version: 4 })
    setup()
    fireEvent.click(await screen.findByRole('checkbox', { name: 'systemConfig.selfRegistration' }))
    expect(api.updateLocalAuth).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'systemConfig.saveChanges' }))
    await waitFor(() => expect(api.updateLocalAuth.mock.calls[0]?.[0]).toEqual({
      passwordLoginEnabled: true, selfRegistrationEnabled: false, version: 3,
    }))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('locks the confirmation while a change is being saved', async () => {
    let resolveSave!: (value: unknown) => void
    api.updateLocalAuth.mockImplementation(() => new Promise((resolve) => { resolveSave = resolve }))
    setup()
    fireEvent.click(await screen.findByRole('checkbox', { name: 'systemConfig.passwordLogin' }))
    fireEvent.click(screen.getByRole('button', { name: 'systemConfig.saveChanges' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'systemConfig.confirmDisableLogin' }))
    const processing = within(dialog).getByRole('button', { name: 'dialog.processing' })
    expect((processing as HTMLButtonElement).disabled).toBe(true)
    expect((within(dialog).getByRole('button', { name: 'dialog.cancel' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(processing)
    await waitFor(() => expect(api.updateLocalAuth).toHaveBeenCalledTimes(1))
    resolveSave({ passwordLoginEnabled: false, selfRegistrationEnabled: true, version: 4 })
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  it('requires an explicit save after changing an active rule role', async () => {
    setup([{ id: 1, providerCode: 'github', email: 'admin@example.com', roleCode: 'SUPER_ADMIN', status: 'ACTIVE', version: 2 }])
    await screen.findByText('admin@example.com')
    fireEvent.change(screen.getAllByRole('combobox', { name: 'systemConfig.role' })[1], { target: { value: 'USER' } })
    expect(api.updateRoleGrant).not.toHaveBeenCalled()
    fireEvent.click(screen.getAllByRole('button', { name: 'systemConfig.saveChanges' })[1])
    await waitFor(() => expect(api.updateRoleGrant).toHaveBeenCalledWith(1, { version: 2, roleCode: 'USER' }))
  })

  it('requires an in-page confirmation before disabling a rule', async () => {
    api.disableRoleGrant.mockResolvedValue({})
    setup([{ id: 1, providerCode: 'github', email: 'admin@example.com', roleCode: 'SUPER_ADMIN', status: 'ACTIVE', version: 2 }])
    await screen.findByText('admin@example.com')
    fireEvent.click(screen.getByRole('button', { name: 'systemConfig.disable' }))
    const dialog = await screen.findByRole('dialog', { name: 'systemConfig.disableTitle' })
    fireEvent.click(within(dialog).getByRole('button', { name: 'dialog.cancel' }))
    expect(api.disableRoleGrant).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'systemConfig.disable' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'systemConfig.disable' }))
    await waitFor(() => expect(api.disableRoleGrant).toHaveBeenCalledWith(1, 2))
  })
})
