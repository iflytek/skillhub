// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider, useQueryClient } from '@tanstack/react-query'
import { createInstance } from 'i18next'
import { I18nextProvider, useTranslation } from 'react-i18next'
import { defaultSitePresentation, presentationText, SITE_PRESENTATION_QUERY_KEY, useSitePresentation } from '@/shared/hooks/use-site-presentation'
import { HomepageRecommendations } from '@/features/recommendation/recommendation-preview'
import { RECOMMENDATION_KEY } from '@/api/recommendations'
import appI18n from '@/i18n/config'
import { sitePresentationApi } from '@/api/site-presentation'
import { SitePresentationPreview } from './site-presentation-preview'

const events = vi.hoisted(() => ({ activate: vi.fn(), navigate: vi.fn(), languageCache: vi.fn(), includeRecommendations: false, client: null as QueryClient | null }))
vi.mock('@/api/site-presentation', async original => ({ ...await original<typeof import('@/api/site-presentation')>(), sitePresentationApi: { get: vi.fn() } }))
vi.mock('@tanstack/react-router', () => ({ useRouter: () => undefined, useNavigate: () => events.navigate, Link: ({ children }: { children: React.ReactNode }) => <a>{children}</a> }))
vi.mock('@/shared/components/brand-mark', () => ({ BrandMark: () => <span>brand</span> }))
vi.mock('@/pages/landing', () => ({ LandingPage: function PreviewLanding() {
  events.client = useQueryClient()
  const { settings } = useSitePresentation()
  const { t, i18n } = useTranslation()
  return <><h1>{presentationText(settings, 'hero', 'title', i18n.resolvedLanguage, t)}</h1><a href="/dashboard/publish" onClick={events.activate}>publish</a><button onClick={events.activate}>action</button>{events.includeRecommendations && <HomepageRecommendations />}</>
} }))
afterEach(() => { cleanup(); vi.clearAllMocks(); vi.unstubAllGlobals(); events.includeRecommendations = false; events.client = null })

describe('homepage draft preview isolation', () => {
  it('overrides only the preview subtree and locale without changing saved cache or global language', async () => {
    const i18n = createInstance().use({ type: 'languageDetector', init: () => undefined, detect: () => 'en', cacheUserLanguage: events.languageCache })
    await i18n.init({ lng: 'en', fallbackLng: 'en', resources: { en: { translation: { nav: { landing: 'Home' } } }, zh: { translation: { nav: { landing: '首页' } } } } })
    events.languageCache.mockClear()
    const saved = defaultSitePresentation()
    saved.siteName = 'Public brand'
    const draft = structuredClone(saved)
    draft.siteName = 'Draft brand'
    draft.sections.hero.texts.title = { en: 'English draft', zh: '中文草稿' }
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    client.setQueryData(SITE_PRESENTATION_QUERY_KEY, { settings: saved, version: 7 })
    render(<I18nextProvider i18n={i18n}><QueryClientProvider client={client}><SitePresentationPreview settings={draft} locale="zh" /></QueryClientProvider></I18nextProvider>)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('中文草稿')
    expect(screen.getAllByText('Draft brand', { selector: 'strong' })).toHaveLength(2)
    expect(i18n.language).toBe('en')
    expect(events.languageCache).not.toHaveBeenCalled()
    expect(client.getQueryData(SITE_PRESENTATION_QUERY_KEY)).toEqual({ settings: saved, version: 7 })
    expect(sitePresentationApi.get).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('link', { name: 'publish' }))
    fireEvent.click(screen.getByRole('button', { name: 'action' }))
    expect(events.activate).not.toHaveBeenCalled()
  })
  it('isolates real recommendation query results from the public locale cache and clears them on close', async () => {
    await appI18n.changeLanguage('en')
    const i18n = createInstance()
    await i18n.init({ lng: 'en', fallbackLng: 'en', resources: { en: { translation: {} }, zh: { translation: {} } } })
    const response = { topics: [], featuredSkills: [{ id: 71, slug: 'response-skill', namespace: 'global', displayName: 'English response skill' }] }
    const fetch = vi.fn<typeof globalThis.fetch>().mockImplementation(async (_input, init) => {
      expect(new Headers(init?.headers).get('Accept-Language')).toBe('en')
      return new Response(JSON.stringify({ code: 0, data: response }), { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetch)
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const publicData = { topics: [], featuredSkills: [] }
    client.setQueryData([...RECOMMENDATION_KEY, 'en'], publicData)
    events.includeRecommendations = true
    const view = render(<I18nextProvider i18n={i18n}><QueryClientProvider client={client}><SitePresentationPreview settings={defaultSitePresentation()} locale="zh" /></QueryClientProvider></I18nextProvider>)
    expect(await screen.findByRole('link', { name: 'English response skill' })).toBeTruthy()
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(client.getQueryData([...RECOMMENDATION_KEY, 'zh'])).toBeUndefined()
    expect(client.getQueryData([...RECOMMENDATION_KEY, 'en'])).toEqual(publicData)
    const privateClient = events.client
    expect(privateClient).not.toBe(client)
    expect(privateClient?.getQueryData([...RECOMMENDATION_KEY, 'zh'])).toEqual(response)
    view.unmount()
    await waitFor(() => expect(privateClient?.getQueryCache().getAll()).toHaveLength(0))
  })

  it.each(['Enter', ' '])('blocks %s activation of the actual editorial skill card in a read-only homepage preview', async key => {
    await appI18n.changeLanguage('en')
    const i18n = createInstance()
    await i18n.init({ lng: 'en', fallbackLng: 'en', resources: { en: { translation: {} } } })
    const response = { topics: [], featuredSkills: [{ id: 72, slug: 'read-only-skill', namespace: 'global', displayName: 'Read-only skill' }] }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 0, data: response }), { status: 200, headers: { 'Content-Type': 'application/json' } })))
    events.includeRecommendations = true
    render(<I18nextProvider i18n={i18n}><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><SitePresentationPreview settings={defaultSitePresentation()} locale="en" /></QueryClientProvider></I18nextProvider>)
    const card = await screen.findByRole('link', { name: 'Read-only skill' })
    card.focus()
    fireEvent.keyDown(card, { key })
    expect(events.navigate).not.toHaveBeenCalled()
  })

})
