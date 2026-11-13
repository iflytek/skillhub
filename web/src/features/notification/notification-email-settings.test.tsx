/** @vitest-environment jsdom */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
const api = vi.hoisted(() => ({ settings: vi.fn(), updateSettings: vi.fn(), deliveries: vi.fn(), retry: vi.fn() }))
vi.mock('./notification-email-api', () => ({ notificationEmailApi: api }))
vi.mock('@/api/client', () => ({ ApiError: class ApiError extends Error { constructor(message: string, public status: number) { super(message) } } }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }) }))
import { NotificationEmailSettingsPanel } from './notification-email-settings'
Element.prototype.scrollIntoView = vi.fn()
Element.prototype.hasPointerCapture = () => false
Element.prototype.releasePointerCapture = vi.fn()
function setup() { const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); render(<QueryClientProvider client={client}><NotificationEmailSettingsPanel /></QueryClientProvider>); return client }
describe('business email administration', () => {
  beforeEach(() => {
    api.settings.mockResolvedValue({ enabled: false, digestWindowMinutes: 15, publicBaseUrl: 'https://example.test/sub', version: 2, smtpConfigured: true })
    api.deliveries.mockResolvedValue({ items: [{ id: 18, recipientId: 'owner-a', status: 'UNKNOWN', kind: 'DIGEST', attempts: 1, createdAt: '2026-10-22T00:00:00Z' }], totalPages: 2, page: 0 })
    api.retry.mockResolvedValue(null); api.updateSettings.mockResolvedValue({})
  })
  afterEach(() => { cleanup(); vi.resetAllMocks() })
  it('requires explicit UNKNOWN duplicate-risk confirmation before retrying', async () => {
    setup(); fireEvent.click(await screen.findByRole('button', { name: 'notificationEmail.retry' }))
    await screen.findByText('notificationEmail.duplicateRisk')
    expect(api.retry).not.toHaveBeenCalled()
    const dialog = screen.getByRole('dialog')
    const button = Array.from(dialog.querySelectorAll('button')).find((item) => item.textContent === 'notificationEmail.retry')
    if (!button) throw new Error('missing retry confirmation')
    fireEvent.click(button)
    await waitFor(() => expect(api.retry).toHaveBeenCalledWith(18, true))
  })
  it('filters and paginates without reusing records for a previous filter', async () => {
    setup(); await screen.findByText('#18')
    fireEvent.click(screen.getByRole('button', { name: 'pagination.next' }))
    await waitFor(() => expect(api.deliveries).toHaveBeenCalledWith(1, ''))
    api.deliveries.mockResolvedValue({ items: [], totalPages: 0 })
    fireEvent.keyDown(screen.getByRole('combobox', { name: 'notificationEmail.filter' }), { key: 'Enter' })
    fireEvent.click(await screen.findByRole('option', { name: 'notificationEmail.status.FAILED' }))
    await waitFor(() => expect(api.deliveries).toHaveBeenCalledWith(0, 'FAILED'))
    await screen.findByText('notificationEmail.noDeliveries')
    expect(screen.queryByText('#18')).toBeNull()
  })
  it('does not expose secrets and saves ordinary settings with their version', async () => {
    setup(); const toggle = await screen.findByRole('switch', { name: 'notificationEmail.enabled' })
    expect(toggle.getAttribute('aria-checked')).toBe('false')
    fireEvent.click(toggle)
    expect(toggle.getAttribute('aria-checked')).toBe('true')
    expect(api.updateSettings).not.toHaveBeenCalled()
    fireEvent.change(screen.getByRole('spinbutton', { name: 'notificationEmail.window' }), { target: { value: '30' } })
    fireEvent.click(screen.getByRole('button', { name: 'notificationEmail.save' }))
    await waitFor(() => expect(api.updateSettings.mock.calls[0]?.[0]).toEqual({ enabled: true, digestWindowMinutes: 30, publicBaseUrl: 'https://example.test/sub', version: 2 }))
    expect(screen.queryByLabelText(/password|secret/i)).toBeNull()
  })
  it('keeps UNKNOWN retry errors in the dialog and never shows false success', async () => {
    api.retry.mockRejectedValue(new Error('failure')); setup()
    fireEvent.click(await screen.findByRole('button', { name: 'notificationEmail.retry' }))
    const dialog = screen.getByRole('dialog'); const button = Array.from(dialog.querySelectorAll('button')).find((item) => item.textContent === 'notificationEmail.retry')
    if (!button) throw new Error('missing retry confirmation')
    fireEvent.click(button)
    await screen.findByRole('alert')
    expect(screen.getByRole('dialog')).toBeTruthy()
  })
  it('shows delivery metadata without email content and does not retry SENT batches', async () => {
    api.deliveries.mockResolvedValue({ items: [{ id: 27, recipientId: 'owner-sent', status: 'SENT', attempts: 1, resultCode: 'SMTP_ACCEPTED', createdAt: '2026-10-22T00:00:00Z' }], totalPages: 1 })
    setup()
    fireEvent.click(await screen.findByRole('button', { name: 'notificationEmail.details' }))
    expect(screen.getByRole('dialog').textContent).toContain('notificationEmail.deliveryMeaning')
    expect(screen.getByRole('dialog').textContent).toContain('SMTP_ACCEPTED')
    expect(screen.queryByRole('button', { name: 'notificationEmail.retry' })).toBeNull()
    expect(api.retry).not.toHaveBeenCalled()
  })

  it('discards an unsaved email change and reports dirty state to the settings shell', async () => {
    const onDirtyChange = vi.fn()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><NotificationEmailSettingsPanel view="mail" onDirtyChange={onDirtyChange} /></QueryClientProvider>)
    const field = await screen.findByRole('spinbutton', { name: 'notificationEmail.window' })
    fireEvent.change(field, { target: { value: '35' } })
    await waitFor(() => expect(onDirtyChange).toHaveBeenLastCalledWith(true))
    fireEvent.click(screen.getByRole('button', { name: 'systemConfig.discard' }))
    expect((field as HTMLInputElement).value).toBe('15')
    await waitFor(() => expect(onDirtyChange).toHaveBeenLastCalledWith(false))
    expect(api.updateSettings).not.toHaveBeenCalled()
  })

})
