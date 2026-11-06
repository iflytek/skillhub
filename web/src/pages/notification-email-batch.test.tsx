/** @vitest-environment jsdom */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
const api = vi.hoisted(() => ({ batch: vi.fn(), logout: vi.fn(), navigate: vi.fn() }))
const session = vi.hoisted(() => ({ user: { userId: 'owner-a', displayName: 'Current reader' } as { userId: string; displayName: string } | null, isLoading: false }))
vi.mock('@/features/notification/notification-email-api', () => ({ notificationEmailApi: api }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => session }))
vi.mock('@/api/client', () => ({ authApi: { logout: api.logout }, ApiError: class ApiError extends Error { constructor(message: string, public status: number) { super(message) } } }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string, options?: { defaultValue?: string }) => key === 'notificationEvents.REVIEW_APPROVED.title' ? 'Review approved' : key.startsWith('notificationEvents.') ? options?.defaultValue ?? key : key, i18n: { language: 'en' } }) }))
vi.mock('@tanstack/react-router', () => ({ useParams: () => ({ id: '23' }), useNavigate: () => api.navigate, Link: ({ children, to, search }: { children: React.ReactNode; to: string; search?: { returnTo?: string } }) => <a href={to + (search?.returnTo ? `?returnTo=${encodeURIComponent(search.returnTo)}` : '')}>{children}</a> }))
import { EmailBatchDetail } from './notification-email-batch'
function setup() { const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryDelay: 0 } } }); render(<QueryClientProvider client={client}><EmailBatchDetail id="23" /></QueryClientProvider>); return client }
describe('protected email batch details', () => {
  beforeEach(() => { session.user = { userId: 'owner-a', displayName: 'Current reader' }; session.isLoading = false; api.logout.mockResolvedValue(undefined); api.batch.mockResolvedValue({ id: 23, status: 'SENT', createdAt: '2026-10-22T00:00:00Z', items: [{ id: 1, category: 'PUBLISH', occurredAt: '2026-10-22T00:00:00Z', targetPath: '/space/team/test' }], totalItems: 22, unavailableCount: 2, nextCursor: '100' }) })
  afterEach(() => { cleanup(); vi.resetAllMocks() })
  it('shows independent batch items, unavailable count and fetches the next page', async () => {
    setup(); await screen.findByText('notificationEmail.unavailableItems')
    expect(screen.getByRole('link', { name: 'notificationEmail.openItem' }).getAttribute('href')).toBe('/space/team/test')
    api.batch.mockResolvedValue({ id: 23, status: 'SENT', items: [], nextCursor: null })
    fireEvent.click(screen.getByRole('button', { name: 'pagination.next' }))
    await waitFor(() => expect(api.batch).toHaveBeenCalledWith('23', '100'))
    await screen.findByText('notificationEmail.noItems')
    expect(screen.queryByRole('link', { name: 'notificationEmail.openItem' })).toBeNull()
  })
  it('distinguishes expired or inaccessible batches from an empty batch', async () => {
    api.batch.mockRejectedValue(new ApiError('missing', 404)); setup()
    await screen.findByRole('heading', { name: 'notificationEmail.batchState.unavailableTitle' })
    expect(screen.queryByText('notificationEmail.noItems')).toBeNull()
  })
  it('shows the translated action title with the notification category', async () => {
    api.batch.mockResolvedValue({ id: 23, status: 'SENT', items: [{ id: 1, category: 'REVIEW', eventType: 'REVIEW_APPROVED' }], nextCursor: null })
    setup()
    await screen.findByText('Review approved')
    expect(screen.getByText('notification.preferences.review')).toBeTruthy()
    expect(screen.queryByText('REVIEW_APPROVED')).toBeNull()
  })
  it.each(['UNTRANSLATED_EVENT', '<img src=x onerror=alert(1)>', undefined])('falls back to the category without exposing unknown eventType %s', async (eventType) => {
    api.batch.mockResolvedValue({ id: 23, status: 'SENT', items: [{ id: 1, category: 'REVIEW', eventType }], nextCursor: null })
    setup()
    await screen.findByText('notification.preferences.review')
    expect(screen.getAllByText('notification.preferences.review')).toHaveLength(1)
    if (eventType) expect(screen.queryByText(eventType)).toBeNull()
    expect(document.querySelector('img')).toBeNull()
    expect(document.body.textContent).not.toContain('notificationEvents.')
  })
  it('does not turn an external target into a clickable link', async () => {
    api.batch.mockResolvedValue({ id: 23, status: 'SENT', items: [{ id: 1, category: 'PUBLISH', targetPath: '//evil.example' }], nextCursor: null }); setup()
    await screen.findByText('notification.preferences.publish')
    expect(screen.queryByRole('link', { name: 'notificationEmail.openItem' })).toBeNull()
  })
  it('can continue past a raw page with no currently visible items', async () => {
    api.batch.mockResolvedValue({ id: 23, status: 'SENT', items: [], totalItems: 80, unavailableCount: 20, nextCursor: '200' })
    setup(); await screen.findByText('notificationEmail.noItems')
    const next = screen.getByRole('button', { name: 'pagination.next' })
    expect(next.hasAttribute('disabled')).toBe(false)
    fireEvent.click(next)
    await waitFor(() => expect(api.batch).toHaveBeenCalledWith('23', '200'))
  })
  it('shows the root delivery when an older batch has been merged', async () => {
    api.batch.mockResolvedValue({ id: 23, status: 'MERGED', items: [], totalItems: 0, nextCursor: null, mergedIntoDeliveryId: 31 })
    setup(); await screen.findByText('notificationEmail.mergedHelp')
    expect(screen.getByText('notificationEmail.viewMergedBatch')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'pagination.next' }).hasAttribute('disabled')).toBe(true)
  })

  it('shows sign-in guidance instead of an endless loader without a session', async () => {
    session.user = null
    setup()
    expect(screen.getByRole('heading', { name: 'notificationEmail.batchState.signInTitle' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'notificationEmail.batchState.signIn' }).getAttribute('href')).toBe('/login?returnTo=%2Fdashboard%2Fnotifications%2Femail%2F23')
    expect(api.batch).not.toHaveBeenCalled()
    expect(screen.queryByText('notificationEmail.loading')).toBeNull()
  })
  it('waits for session loading before offering sign in', () => {
    session.user = null; session.isLoading = true
    setup()
    expect(screen.getByRole('status').textContent).toBe('notificationEmail.loading')
    expect(screen.queryByText('notificationEmail.batchState.signInTitle')).toBeNull()
    expect(api.batch).not.toHaveBeenCalled()
  })
  it.each([403, 404])('uses the same private unavailable state for HTTP %i and suppresses the global toast', async (status) => {
    api.batch.mockRejectedValue(new ApiError('sensitive failure detail', status))
    const client = setup()
    await screen.findByRole('heading', { name: 'notificationEmail.batchState.unavailableTitle' })
    expect(screen.getByText('notificationEmail.batchState.currentAccount')).toBeTruthy()
    expect(screen.getByText('notificationEmail.batchState.accountHint')).toBeTruthy()
    expect(screen.queryByText('sensitive failure detail')).toBeNull()
    expect(client.getQueryCache().getAll().find(query => query.queryKey.includes('email-batch'))?.meta?.skipGlobalErrorHandler).toBe(true)
    expect(api.batch).toHaveBeenCalledTimes(1)
  })
  it('hides formerly loaded details after access is rejected on refresh', async () => {
    const client = setup()
    await screen.findByRole('link', { name: 'notificationEmail.openItem' })
    api.batch.mockRejectedValue(new ApiError('missing', 404))
    await act(async () => { await client.refetchQueries({ queryKey: ['notifications', 'owner-a', 'email-batch'] }) })
    await screen.findByRole('heading', { name: 'notificationEmail.batchState.unavailableTitle' })
    expect(screen.queryByRole('link', { name: 'notificationEmail.openItem' })).toBeNull()
    expect(screen.queryByText('notificationEmail.itemCount')).toBeNull()
  })
  it('logs out, clears private data and keeps the batch return path when switching accounts', async () => {
    api.batch.mockRejectedValue(new ApiError('missing', 404))
    const client = setup(); client.setQueryData(['notifications', 'old-reader', 'email-batch', '11'], { private: true })
    fireEvent.click(await screen.findByRole('button', { name: 'notificationEmail.batchState.switchAccount' }))
    await waitFor(() => expect(api.navigate).toHaveBeenCalledWith({ to: '/login', search: { returnTo: '/dashboard/notifications/email/23' } }))
    expect(api.logout).toHaveBeenCalledTimes(1)
    expect(client.getQueryData(['auth', 'me'])).toBeNull()
    expect(client.getQueryData(['notifications', 'old-reader', 'email-batch', '11'])).toBeUndefined()
  })
  it('keeps the account unchanged if logout fails', async () => {
    api.batch.mockRejectedValue(new ApiError('missing', 404)); api.logout.mockRejectedValue(new Error('logout unavailable'))
    setup()
    fireEvent.click(await screen.findByRole('button', { name: 'notificationEmail.batchState.switchAccount' }))
    await screen.findByText('notificationEmail.batchState.switchFailed')
    expect(api.navigate).not.toHaveBeenCalled()
  })
  it('offers sign-in recovery when a cached session expires', async () => {
    api.batch.mockRejectedValue(new ApiError('session expired', 401))
    setup()
    await screen.findByRole('heading', { name: 'notificationEmail.batchState.signInTitle' })
    expect(screen.getByRole('link', { name: 'notificationEmail.batchState.signIn' })).toBeTruthy()
    expect(api.logout).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'notificationEmail.refresh' })).toBeNull()
  })
  it('recovers a transient load failure through the local retry action', async () => {
    api.batch.mockRejectedValue(new ApiError('unavailable', 503))
    setup()
    await screen.findByRole('heading', { name: 'notificationEmail.batchState.failureTitle' })
    api.batch.mockResolvedValue({ id: 23, status: 'SENT', items: [], nextCursor: null })
    fireEvent.click(screen.getByRole('button', { name: 'notificationEmail.refresh' }))
    await screen.findByText('notificationEmail.noItems')
    expect(screen.queryByRole('alert')).toBeNull()
  })

})
