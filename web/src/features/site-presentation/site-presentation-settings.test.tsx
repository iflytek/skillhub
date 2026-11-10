// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiError } from '@/api/client'
import { defaultSitePresentation, SITE_PRESENTATION_QUERY_KEY } from '@/shared/hooks/use-site-presentation'
import { sitePresentationApi } from '@/api/site-presentation'
import { SitePresentationSettingsPanel } from './site-presentation-settings'

vi.mock('@tanstack/react-router', () => ({ useBlocker: () => ({ status: 'idle' }) }))
vi.mock('react-i18next', async (original) => ({ ...await original<typeof import('react-i18next')>(), useTranslation: () => ({ t: (key: string) => key, i18n: { resolvedLanguage: 'en' } }) }))
vi.mock('@/api/site-presentation', () => ({ sitePresentationApi: { getAdmin: vi.fn(), update: vi.fn(), upload: vi.fn() }, presentationAssetUrl: () => undefined }))
afterEach(() => { cleanup(); vi.resetAllMocks() })
async function setup() {
  const response = { settings: defaultSitePresentation(), version: 7 }
  vi.mocked(sitePresentationApi.getAdmin).mockResolvedValue(response)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={client}><SitePresentationSettingsPanel /></QueryClientProvider>)
  const input = await screen.findByRole('textbox', { name: 'sitePresentation.siteName' })
  return { input, client, response }
}

describe('site presentation settings', () => {
  it('previews without saving, then saves the exact draft and version and refreshes the shared brand', async () => {
    const { input, client, response } = await setup()
    const saved = { settings: { ...response.settings, siteName: 'Team Library' }, version: 8 }
    vi.mocked(sitePresentationApi.update).mockResolvedValue(saved)
    fireEvent.change(input, { target: { value: 'Team Library' } })
    fireEvent.click(screen.getByRole('button', { name: 'sitePresentation.preview' }))
    expect(await screen.findByRole('dialog')).toBeTruthy()
    expect(sitePresentationApi.update).not.toHaveBeenCalled()
    // The local preview has no save action; close it using the existing Dialog keyboard behavior.
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    fireEvent.click(screen.getByRole('button', { name: 'sitePresentation.save' }))
    await waitFor(() => expect(sitePresentationApi.update).toHaveBeenCalledWith({ settings: saved.settings, version: 7 }))
    await waitFor(() => expect(client.getQueryData(SITE_PRESENTATION_QUERY_KEY)).toEqual(saved))
    expect(screen.getByText('sitePresentation.saved')).toBeTruthy()
  })
  it('retains entered content on a failed save', async () => {
    const { input } = await setup()
    vi.mocked(sitePresentationApi.update).mockRejectedValue(new Error('Storage unavailable'))
    fireEvent.change(input, { target: { value: 'Unsaved brand' } })
    fireEvent.click(screen.getByRole('button', { name: 'sitePresentation.save' }))
    await screen.findByText('Storage unavailable')
    expect((input as HTMLInputElement).value).toBe('Unsaved brand')
    expect(screen.getByText('sitePresentation.unsaved')).toBeTruthy()
  })
  it('keeps edits on a version conflict and requires explicit discard before loading a fresh form', async () => {
    const { input, response } = await setup()
    vi.mocked(sitePresentationApi.update).mockRejectedValue(new ApiError('Conflict', 409))
    fireEvent.change(input, { target: { value: 'My edits' } })
    fireEvent.click(screen.getByRole('button', { name: 'sitePresentation.save' }))
    await screen.findByText('sitePresentation.conflict')
    expect((input as HTMLInputElement).value).toBe('My edits')
    expect((screen.getByRole('button', { name: 'sitePresentation.save' }) as HTMLButtonElement).disabled).toBe(true)
    vi.mocked(sitePresentationApi.getAdmin).mockResolvedValue({ settings: { ...response.settings, siteName: 'Other administrator' }, version: 8 })
    fireEvent.click(screen.getByRole('button', { name: 'sitePresentation.discardReload' }))
    await waitFor(() => expect((screen.getByRole('textbox', { name: 'sitePresentation.siteName' }) as HTMLInputElement).value).toBe('Other administrator'))
  })
})
