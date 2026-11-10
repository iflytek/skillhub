// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import type { SitePresentationSettings } from '@/api/site-presentation'
import { defaultSitePresentation } from '@/shared/hooks/use-site-presentation'
import { PRESENTATION_CONTENT, PRESENTATION_ITEMS } from '@/shared/lib/presentation-content'
import { LandingPage } from './landing'
import { HomePage } from './home'

const state = vi.hoisted((): { settings: SitePresentationSettings | null; search: (params: unknown, enabled: boolean) => void } => ({ settings: null, search: vi.fn() }))
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => vi.fn(), Link: ({ children }: { children: React.ReactNode }) => <a>{children}</a> }))
vi.mock('react-i18next', async (original) => ({ ...await original<typeof import('react-i18next')>(), useTranslation: () => ({ t: (key: string) => key, i18n: { resolvedLanguage: 'zh' } }) }))
vi.mock('@/shared/hooks/use-site-presentation', async (original) => ({ ...await original<typeof import('@/shared/hooks/use-site-presentation')>(), useSitePresentation: () => ({ settings: state.settings ?? defaultSitePresentation() }) }))
vi.mock('@/shared/hooks/use-skill-queries', () => ({ useSearchSkills: (params: unknown, enabled = true) => { state.search(params, enabled); return { data: { items: [] }, isLoading: false } } }))
vi.mock('@/shared/hooks/use-in-view', () => ({ useInView: () => ({ ref: null, inView: true }) }))
vi.mock('@/features/search/search-bar', () => ({ SearchBar: () => <input aria-label="market-search" /> }))
vi.mock('@/shared/components/brand-mark', () => ({ BrandMark: () => <div /> }))
vi.mock('@/shared/components/landing-quick-start', () => ({ LandingQuickStartSection: () => <div>landing-quick-start</div> }))
vi.mock('@/shared/components/quick-start', () => ({ QuickStartSection: () => <div>market-quick-start</div> }))
vi.mock('@/features/skill/skill-card', () => ({ SkillCard: () => <div /> }))
afterEach(() => { cleanup(); vi.clearAllMocks(); state.settings = null })

describe('configurable original homepage and market boundary', () => {
  it('preserves the original introduction and live sections by default', () => {
    render(<LandingPage />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('landing.experience.heroTitle')
    expect(screen.getByText('landing-quick-start')).toBeTruthy()
    expect(screen.getByText('home.popularTitle')).toBeTruthy()
    expect(screen.getByText('home.latestTitle')).toBeTruthy()
    expect(state.search).toHaveBeenCalledWith({ sort: 'downloads', size: 6 }, true)
  })
  it('retains the fixed example notice when the preview caption is customized', () => {
    state.settings = defaultSitePresentation()
    state.settings.sections.hero.texts.previewCaption = { zh: '我们的团队工具' }
    render(<LandingPage />)
    expect(screen.getByText('我们的团队工具')).toBeTruthy()
    expect(screen.getByText('landing.experience.demoSkills.caption')).toBeTruthy()
  })
  it('renders custom text safely and disables hidden sections and requests', () => {
    const settings = defaultSitePresentation()
    settings.sections.hero.texts = { title: { zh: '<b>团队技能</b>' }, description: { en: 'Fallback description' } }
    for (const id of ['popular', 'latest', 'quickStart'] as const) settings.sections[id].enabled = false
    state.settings = settings
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
  it('can hide every homepage section without changing the skills marketplace', () => {
    state.settings = defaultSitePresentation()
    state.settings.siteName = 'Team Library'
    Object.values(state.settings.sections).forEach(section => { section.enabled = false })
    const landing = render(<LandingPage />)
    expect(screen.queryByRole('heading')).toBeNull()
    landing.unmount()
    render(<HomePage />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Team Library')
    expect(screen.getByText('home.popularTitle')).toBeTruthy()
    expect(screen.getByText('home.latestTitle')).toBeTruthy()
    expect(screen.getByText('market-quick-start')).toBeTruthy()
    expect(state.search).toHaveBeenLastCalledWith({ sort: 'newest', size: 6 }, true)
  })
  it('binds all landing text slots, item switches and shared external links', () => {
    const settings = defaultSitePresentation()
    for (const section of ['hero', 'capabilities', 'popular', 'latest', 'enterprise', 'cta'] as const) {
      for (const slot of Object.keys(PRESENTATION_CONTENT[section])) settings.sections[section].texts[slot] = { zh: `${section}-${slot}` }
    }
    settings.links = { repository: 'https://team.example/source', documentation: 'https://team.example/docs' }
    state.settings = settings
    const { container } = render(<LandingPage />)
    for (const section of ['hero', 'capabilities', 'popular', 'latest', 'enterprise', 'cta'] as const) {
      for (const slot of Object.keys(PRESENTATION_CONTENT[section])) {
        if (slot === 'searchPlaceholder') expect(screen.getByPlaceholderText(`${section}-${slot}`)).toBeTruthy()
        else expect(container.textContent).toContain(`${section}-${slot}`)
      }
    }
    expect(container.querySelector('a[href="https://team.example/source"]')).toBeTruthy()
    expect(container.querySelector('a[href="https://team.example/docs"]')).toBeTruthy()
    cleanup()
    for (const section of ['hero', 'capabilities', 'enterprise', 'cta'] as const) {
      settings.sections[section].items = Object.fromEntries(PRESENTATION_ITEMS[section].map(item => [item, false]))
    }
    render(<LandingPage />)
    expect(screen.queryByText('hero-previewTitle')).toBeNull()
    expect(screen.queryByText('hero-primaryActionLabel')).toBeNull()
    expect(screen.queryByText('hero-highlightSelfHosted')).toBeNull()
    expect(screen.queryByText('capabilities-secureTitle')).toBeNull()
    expect(screen.queryByText('enterprise-deploymentTitle')).toBeNull()
    expect(screen.queryByText('cta-primaryActionLabel')).toBeNull()
  })
})
