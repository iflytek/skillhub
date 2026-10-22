/** @vitest-environment jsdom */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
const api = vi.hoisted(() => ({ batch: vi.fn() }))
vi.mock('@/features/notification/notification-email-api', () => ({ notificationEmailApi: api }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: { userId: 'owner-a' } }) }))
vi.mock('@/api/client', () => ({ ApiError: class ApiError extends Error { constructor(message: string, public status: number) { super(message) } } }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }) }))
vi.mock('@tanstack/react-router', () => ({ useParams: () => ({ id: '23' }), Link: ({ children }: { children: React.ReactNode }) => <span>{children}</span> }))
import { EmailBatchDetail } from './notification-email-batch'
function setup() { const client = new QueryClient({ defaultOptions: { queries: { retry: false } } }); render(<QueryClientProvider client={client}><EmailBatchDetail id="23" /></QueryClientProvider>) }
describe('protected email batch details', () => {
  beforeEach(() => api.batch.mockResolvedValue({ id: 23, status: 'SENT', createdAt: '2026-10-22T00:00:00Z', items: [{ id: 1, category: 'PUBLISH', occurredAt: '2026-10-22T00:00:00Z', targetPath: '/space/team/test' }], totalItems: 22, unavailableCount: 2, nextCursor: '100' }))
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
    await screen.findByText('notificationEmail.expired')
    expect(screen.queryByText('notificationEmail.noItems')).toBeNull()
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

})
