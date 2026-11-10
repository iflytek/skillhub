// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import type { SitePresentationSettings } from '@/api/site-presentation'
import { defaultSitePresentation } from '@/shared/hooks/use-site-presentation'
import { LandingPage } from './landing'
import { HomePage } from './home'

const state = vi.hoisted((): { settings: SitePresentationSettings; search: (params: unknown, enabled: boolean) => void } => ({ settings: { siteName: null, logoUrl: null, faviconUrl: null, homepageMode: 'LANDING', heroTitle: {}, heroDescription: {}, showPopular: true, showLatest: true, showQuickStart: true, footerVisible: true }, search: vi.fn() }))
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => vi.fn(), Link: ({ children }: { children: React.ReactNode }) => <a>{children}</a> }))
vi.mock('react-i18next', async (original) => ({ ...await original<typeof import('react-i18next')>(), useTranslation: () => ({ t: (key: string) => key, i18n: { resolvedLanguage: 'zh' } }) }))
vi.mock('@/shared/hooks/use-site-presentation', async (original) => ({ ...await original<typeof import('@/shared/hooks/use-site-presentation')>(), useSitePresentation: () => ({ settings: state.settings }) }))
vi.mock('@/shared/hooks/use-skill-queries', () => ({ useSearchSkills: (params: unknown, enabled: boolean) => { state.search(params, enabled); return { data: { items: [] }, isLoading: false } } }))
vi.mock('@/shared/hooks/use-in-view', () => ({ useInView: () => ({ ref: null, inView: true }) }))
vi.mock('@/features/search/search-bar', () => ({ SearchBar: () => <input aria-label="catalog-search" /> }))
vi.mock('@/shared/components/brand-mark', () => ({ BrandMark: () => <div /> }))
vi.mock('@/shared/components/landing-quick-start', () => ({ LandingQuickStartSection: () => <div>landing-quick-start</div> }))
vi.mock('@/shared/components/quick-start', () => ({ QuickStartSection: () => <div>catalog-quick-start</div> }))
vi.mock('@/features/skill/skill-card', () => ({ SkillCard: () => <div /> }))
afterEach(() => { cleanup(); vi.clearAllMocks(); state.settings = defaultSitePresentation() })

describe('root homepage presentation and market boundary', () => {
  it('preserves the actual root landing introduction and live sections by default', () => {
    render(<LandingPage />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('landing.experience.heroTitle')
    expect(screen.getByText('landing-quick-start')).toBeTruthy()
    expect(screen.getByText('home.popularTitle')).toBeTruthy()
    expect(screen.getByText('home.latestTitle')).toBeTruthy()
    expect(state.search).toHaveBeenCalledWith({ sort: 'downloads', size: 6 }, true)
  })
  it('applies custom text and disables root landing sections and their requests', () => {
    state.settings = { ...defaultSitePresentation(), heroTitle: { zh: '<b>团队技能</b>' }, heroDescription: { en: 'Fallback description' }, showPopular: false, showLatest: false, showQuickStart: false }
    render(<LandingPage />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('<b>团队技能</b>')
    expect(screen.getByText('Fallback description')).toBeTruthy()
    expect(document.querySelector('h1 b')).toBeNull()
    expect(screen.queryByText('landing-quick-start')).toBeNull()
    expect(screen.queryByText('home.popularTitle')).toBeNull()
    expect(screen.queryByText('home.latestTitle')).toBeNull()
    expect(state.search).toHaveBeenCalledWith({ sort: 'downloads', size: 6 }, false)
    expect(state.search).toHaveBeenCalledWith({ sort: 'newest', size: 6 }, false)
  })
  it('selects the compact catalog at root without mounting the marketing page queries', () => {
    state.settings = { ...defaultSitePresentation(), homepageMode: 'CATALOG', heroTitle: { en: 'Our catalog' }, showPopular: false, showLatest: false, showQuickStart: false }
    render(<LandingPage />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Our catalog')
    expect(screen.getByRole('textbox', { name: 'catalog-search' })).toBeTruthy()
    expect(screen.queryByText('landing.experience.heroTitle')).toBeNull()
    expect(state.search).toHaveBeenCalledTimes(2)
  })
  it('keeps the existing skills page sections when root homepage settings hide them', () => {
    state.settings = { ...defaultSitePresentation(), siteName: 'Team Library', homepageMode: 'CATALOG', heroTitle: { en: 'Homepage only' }, showPopular: false, showLatest: false, showQuickStart: false }
    render(<HomePage />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Team Library')
    expect(screen.getByText('home.popularTitle')).toBeTruthy()
    expect(screen.getByText('home.latestTitle')).toBeTruthy()
    expect(screen.getByText('catalog-quick-start')).toBeTruthy()
    expect(state.search).toHaveBeenCalledWith({ sort: 'downloads', size: 6 }, true)
    expect(state.search).toHaveBeenCalledWith({ sort: 'newest', size: 6 }, true)
  })
})
