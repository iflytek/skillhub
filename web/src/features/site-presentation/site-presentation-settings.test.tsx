// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiError } from '@/api/client'
import { defaultSitePresentation, SITE_PRESENTATION_QUERY_KEY } from '@/shared/hooks/use-site-presentation'
import { sitePresentationApi, type SitePresentationSettings } from '@/api/site-presentation'
import { SitePresentationSettingsPanel } from './site-presentation-settings'

const state = vi.hoisted(() => ({ blocker: vi.fn<(options: { shouldBlockFn: () => boolean; enableBeforeUnload: boolean }) => void>() }))
vi.mock('@tanstack/react-router', () => ({ useBlocker: (options: { shouldBlockFn: () => boolean; enableBeforeUnload: boolean }) => { state.blocker(options); return { status: 'idle' } } }))
vi.mock('react-i18next', async (original) => ({ ...await original<typeof import('react-i18next')>(), useTranslation: () => ({ t: (key: string) => key, i18n: { resolvedLanguage: 'en' } }) }))
vi.mock('@/api/site-presentation', () => ({ sitePresentationApi: { getAdmin: vi.fn(), update: vi.fn(), upload: vi.fn() }, presentationAssetUrl: () => undefined }))
afterEach(() => { cleanup(); vi.resetAllMocks() })
async function setup(settings = defaultSitePresentation()) {
  const response = { settings, version: 7 }
  vi.mocked(sitePresentationApi.getAdmin).mockResolvedValue(response)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={client}><SitePresentationSettingsPanel /></QueryClientProvider>)
  const input = await screen.findByRole('textbox', { name: 'sitePresentation.siteName' })
  return { input, client, response }
}

function preservedSettings(): SitePresentationSettings {
  const value = defaultSitePresentation()
  value.siteName = 'Existing brand'
  value.logoUrl = '/api/v1/site-presentation/assets/existing-logo'
  value.faviconUrl = '/api/v1/site-presentation/assets/existing-icon'
  value.sections.hero.texts = { title: { en: 'Original title', zh: '团队简介' }, description: { en: 'Original description', ru: 'Описание' }, badge: { en: 'Preserve badge' } }
  value.sections.hero.items = { preview: false, highlights: null }
  value.sections.popular.enabled = false
  value.sections.capabilities.texts = { secureTitle: { en: 'Existing privacy title' } }
  value.sections.capabilities.items = { community: false }
  value.sections.footer.texts = { copyright: { en: 'Company copyright' } }
  value.sections.footer.items = { project: false }
  value.links = { documentation: 'https://team.example/docs', repository: 'https://team.example/source', cliPackage: 'https://team.example/cli', community: '/community' }
  return value
}

describe('site presentation settings', () => {
  it('shows only the basic controls with no detailed editor or preview', async () => {
    await setup()
    expect(screen.getAllByRole('textbox')).toHaveLength(5)
    expect(screen.getAllByRole('checkbox')).toHaveLength(8)
    expect(screen.getAllByRole('combobox')).toHaveLength(1)
    expect(document.querySelectorAll('input[type="file"]')).toHaveLength(2)
    expect(document.querySelector('details')).toBeNull()
    expect(screen.queryByRole('button', { name: 'sitePresentation.preview' })).toBeNull()
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getAllByRole('button', { name: 'sitePresentation.restorePage' })).toHaveLength(1)
    expect(screen.getByRole('link', { name: 'sitePresentation.viewHomepage' }).getAttribute('href')).toBe('/')
    expect(state.blocker.mock.lastCall?.[0].shouldBlockFn()).toBe(false)
  })
  it('saves edited basic settings and retains all supported content not exposed by the form', async () => {
    const settings = preservedSettings()
    const { input, client } = await setup(settings)
    vi.mocked(sitePresentationApi.update).mockImplementation(async request => ({ ...request, version: 8 }))
    fireEvent.change(input, { target: { value: 'Team Library' } })
    fireEvent.change(screen.getByRole('textbox', { name: 'sitePresentation.introductionHeading' }), { target: { value: 'Our team skills' } })
    fireEvent.change(screen.getByRole('textbox', { name: 'sitePresentation.linkLabels.documentation' }), { target: { value: '/guide' } })
    fireEvent.click(screen.getByRole('checkbox', { name: 'sitePresentation.regions.popular' }))
    expect(sitePresentationApi.update).not.toHaveBeenCalled()
    expect(client.getQueryData(SITE_PRESENTATION_QUERY_KEY)).toBeUndefined()
    expect(screen.queryByRole('link', { name: 'sitePresentation.viewHomepage' })).toBeNull()
    expect(state.blocker.mock.lastCall?.[0].shouldBlockFn()).toBe(true)
    expect(state.blocker.mock.lastCall?.[0].enableBeforeUnload).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: 'sitePresentation.save' }))
    const expected = { ...settings, siteName: 'Team Library', sections: { ...settings.sections, hero: { ...settings.sections.hero, texts: { ...settings.sections.hero.texts, title: { en: 'Our team skills', zh: '团队简介' } } }, popular: { ...settings.sections.popular, enabled: true } }, links: { ...settings.links, documentation: '/guide' } }
    await waitFor(() => expect(sitePresentationApi.update).toHaveBeenCalledWith({ settings: expected, version: 7 }))
    await waitFor(() => expect(client.getQueryData(SITE_PRESENTATION_QUERY_KEY)).toEqual({ settings: expected, version: 8 }))
    expect(screen.getByText('sitePresentation.saved')).toBeTruthy()
    expect(screen.getByRole('link', { name: 'sitePresentation.viewHomepage' })).toBeTruthy()
  })
  it('restores only this page fields after confirmation and preserves hidden values', async () => {
    const settings = preservedSettings()
    await setup(settings)
    fireEvent.click(screen.getByRole('button', { name: 'sitePresentation.restorePage' }))
    expect(screen.getByText('sitePresentation.restorePageHelp')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'dialog.cancel' }))
    expect((screen.getByRole('textbox', { name: 'sitePresentation.siteName' }) as HTMLInputElement).value).toBe('Existing brand')
    fireEvent.click(screen.getByRole('button', { name: 'sitePresentation.restorePage' }))
    fireEvent.click(screen.getByRole('button', { name: 'dialog.confirm' }))
    await waitFor(() => expect((screen.getByRole('textbox', { name: 'sitePresentation.siteName' }) as HTMLInputElement).value).toBe(''))
    expect(sitePresentationApi.update).not.toHaveBeenCalled()
    vi.mocked(sitePresentationApi.update).mockImplementation(async request => ({ ...request, version: 8 }))
    fireEvent.click(screen.getByRole('button', { name: 'sitePresentation.save' }))
    const sections = Object.fromEntries(Object.entries(settings.sections).map(([id, config]) => [id, { ...config, enabled: true }]))
    sections.hero = { ...sections.hero, texts: { badge: { en: 'Preserve badge' } } }
    await waitFor(() => expect(sitePresentationApi.update).toHaveBeenCalledWith({ settings: { ...settings, siteName: null, logoUrl: null, faviconUrl: null, sections, links: { cliPackage: 'https://team.example/cli', community: '/community' } }, version: 7 }))
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
