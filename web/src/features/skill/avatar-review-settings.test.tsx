/** @vitest-environment jsdom */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ getAvatarReview: vi.fn(), updateAvatarReview: vi.fn() }))
vi.mock('@/api/client', () => ({ systemConfigApi: api }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
import { AvatarReviewSettingsPanel } from './avatar-review-settings'

describe('AvatarReviewSettingsPanel', () => {
  afterEach(() => { cleanup(); api.getAvatarReview.mockReset(); api.updateAvatarReview.mockReset() })

  it('submits both switches with the observed version', async () => {
    api.getAvatarReview.mockResolvedValue({ machineEnabled: false, manualEnabled: true, version: 3 })
    api.updateAvatarReview.mockResolvedValue({ machineEnabled: true, manualEnabled: false, version: 4 })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><AvatarReviewSettingsPanel /></QueryClientProvider>)
    fireEvent.click(await screen.findByRole('switch', { name: 'skillAvatar.machineEnabled' }))
    fireEvent.click(screen.getByRole('switch', { name: 'skillAvatar.manualEnabled' }))
    fireEvent.click(screen.getByRole('button', { name: 'skillAvatar.save' }))
    await waitFor(() => expect(api.updateAvatarReview.mock.calls[0]?.[0]).toEqual({
      machineEnabled: true, manualEnabled: false, version: 3,
    }))
  })

  it('warns before saving when both review switches are off', async () => {
    api.getAvatarReview.mockResolvedValue({ machineEnabled: false, manualEnabled: true, version: 3 })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><AvatarReviewSettingsPanel /></QueryClientProvider>)
    fireEvent.click(await screen.findByRole('switch', { name: 'skillAvatar.manualEnabled' }))
    expect(screen.getByRole('alert').textContent).toBe('skillAvatar.noReviewRisk')
  })
})
