/** @vitest-environment jsdom */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ contact: vi.fn(), preferences: vi.fn(), requestCode: vi.fn(), verify: vi.fn(), updatePreferences: vi.fn() }))
const session = vi.hoisted(() => ({ userId: 'owner-a' }))
vi.mock('./notification-email-api', () => ({ notificationEmailApi: api, EMAIL_CATEGORIES: ['PUBLISH', 'REVIEW', 'PROMOTION', 'REPORT'] }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: { userId: session.userId } }) }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }) }))
vi.mock('@tanstack/react-router', () => ({ Link: ({ children }: { children: React.ReactNode }) => <span>{children}</span> }))
import { NotificationEmailForm } from './notification-email-form'

function contact(verified = false) { return { email: 'a***@example.test', verified, available: verified, reason: verified ? null : 'UNVERIFIED', locale: 'en', resendAvailableAt: null } }
function setup() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const view = render(<QueryClientProvider client={client}><NotificationEmailForm /></QueryClientProvider>)
  return { client, ...view }
}
describe('email notification preferences', () => {
  beforeEach(() => { session.userId = 'owner-a'; api.contact.mockResolvedValue(contact()); api.preferences.mockResolvedValue([{ category: 'PUBLISH', mode: 'OFF' }]); api.requestCode.mockResolvedValue(contact()); api.verify.mockResolvedValue(contact(true)); api.updatePreferences.mockResolvedValue([{ category: 'PUBLISH', mode: 'DIGEST' }]) })
  afterEach(() => { cleanup(); vi.resetAllMocks() })
  it('lets an unverified recipient request a code and blocks non-OFF subscriptions', async () => {
    setup()
    await screen.findByText('a***@example.test')
    const send = screen.getByRole('button', { name: 'notificationEmail.sendCode' })
    expect(send.hasAttribute('disabled')).toBe(false)
    const select = screen.getByLabelText('notification.preferences.publish')
    expect(select.querySelector('option[value="DIGEST"]')?.hasAttribute('disabled')).toBe(true)
    fireEvent.click(send)
    await waitFor(() => expect(api.requestCode.mock.calls[0]?.[0]).toBe('en'))
    fireEvent.change(screen.getByLabelText('notificationEmail.code'), { target: { value: '123456' } })
    fireEvent.click(screen.getByRole('button', { name: 'notificationEmail.verify' }))
    await waitFor(() => expect(api.verify.mock.calls[0]?.[0]).toBe('123456'))
  })
  it('honors the server resend deadline and explains unavailable platform state', async () => {
    api.contact.mockResolvedValue({ ...contact(), reason: 'DISABLED', resendAvailableAt: new Date(Date.now() + 60000).toISOString() })
    setup(); await screen.findByText('notificationEmail.unavailable')
    expect(screen.getByRole('button', { name: 'notificationEmail.resendIn' }).hasAttribute('disabled')).toBe(true)
    expect(screen.getByRole('button', { name: 'notificationEmail.verify' }).hasAttribute('disabled')).toBe(true)
  })
  it('sends only the changed category, leaving in-app preferences untouched', async () => {
    api.contact.mockResolvedValue(contact(true)); setup(); await screen.findByText('notificationEmail.verified')
    fireEvent.change(screen.getByLabelText('notification.preferences.publish'), { target: { value: 'DIGEST' } })
    await waitFor(() => expect(api.updatePreferences.mock.calls[0]?.[0]).toEqual([{ category: 'PUBLISH', mode: 'DIGEST' }]))
    await screen.findByText('notificationEmail.saved')
  })
  it('keeps the prior selection and reports save failure without false success', async () => {
    api.contact.mockResolvedValue(contact(true)); api.updatePreferences.mockRejectedValue(new Error('temporary'))
    setup(); await screen.findByText('notificationEmail.verified')
    fireEvent.change(screen.getByLabelText('notification.preferences.publish'), { target: { value: 'IMMEDIATE' } })
    await screen.findByRole('alert')
    expect(screen.getByLabelText('notification.preferences.publish').getAttribute('disabled')).toBeNull()
    const select = screen.getByLabelText('notification.preferences.publish')
    expect(select instanceof HTMLSelectElement && select.value).toBe('OFF')
    expect(screen.queryByText('notificationEmail.saved')).toBeNull()
  })
  it('does not show a previous account contact when identity changes', async () => {
    const view = setup(); await screen.findByText('a***@example.test')
    session.userId = 'owner-b'; api.contact.mockResolvedValue({ ...contact(true), email: 'b***@example.test' })
    view.rerender(<QueryClientProvider client={view.client}><NotificationEmailForm /></QueryClientProvider>)
    expect(screen.queryByText('a***@example.test')).toBeNull()
    await screen.findByText('b***@example.test')
  })
  it('ignores an old account response that completes after switching accounts', async () => {
    let finishOld: ((value: ReturnType<typeof contact>) => void) | undefined
    api.contact.mockImplementationOnce(() => new Promise<ReturnType<typeof contact>>((resolve) => { finishOld = resolve }))
    const view = setup()
    await waitFor(() => expect(api.contact).toHaveBeenCalledTimes(1))
    session.userId = 'owner-b'
    api.contact.mockResolvedValue({ ...contact(true), email: 'b***@example.test' })
    view.rerender(<QueryClientProvider client={view.client}><NotificationEmailForm /></QueryClientProvider>)
    await screen.findByText('b***@example.test')
    finishOld?.(contact())
    await waitFor(() => expect(screen.queryByText('a***@example.test')).toBeNull())
    expect(screen.getByText('b***@example.test')).toBeTruthy()
  })

})
