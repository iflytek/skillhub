// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { defaultSitePresentation } from '@/shared/hooks/use-site-presentation'
import type { SitePresentationSettings } from '@/api/site-presentation'
import { HomePage } from './home'
const state = vi.hoisted((): { settings: SitePresentationSettings; search: (params: unknown, enabled: boolean) => void } => ({ settings: { homepageMode: 'LANDING', heroTitle: {}, heroDescription: {}, siteName: null, logoUrl: null, faviconUrl: null, footerVisible: true, showPopular: true, showLatest: true, showQuickStart: true }, search: vi.fn() }))
vi.mock('react-i18next', async (original) => ({ ...await original<typeof import('react-i18next')>(), useTranslation: () => ({ t: (key: string) => key, i18n: { resolvedLanguage: 'zh' } }) }))
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => vi.fn() }))
vi.mock('@/shared/hooks/use-site-presentation', async (original) => ({ ...await original<typeof import('@/shared/hooks/use-site-presentation')>(), useSitePresentation: () => ({ settings: state.settings }) }))
vi.mock('@/shared/hooks/use-skill-queries', () => ({ useSearchSkills: (params: unknown, enabled: boolean) => { state.search(params, enabled); return { data: { items: [] }, isLoading: false } } }))
vi.mock('@/features/search/search-bar', () => ({ SearchBar: () => <input aria-label="skill-search" /> }))
vi.mock('@/shared/components/brand-mark', () => ({ BrandMark: () => <div /> }))
vi.mock('@/shared/components/quick-start', () => ({ QuickStartSection: () => <div>quick-start</div> }))
vi.mock('@/features/skill/skill-card', () => ({ SkillCard: () => <div /> }))
afterEach(() => { cleanup(); vi.clearAllMocks(); state.settings = defaultSitePresentation() })
describe('homepage presentation', () => {
  it('preserves default landing content and section queries', () => {
    state.settings = defaultSitePresentation()
    render(<HomePage />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('SkillHub')
    expect(screen.getByText('home.popularTitle')).toBeTruthy()
    expect(screen.getByText('home.latestTitle')).toBeTruthy()
    expect(screen.getByText('quick-start')).toBeTruthy()
    expect(state.search).toHaveBeenCalledWith({ sort: 'downloads', size: 6 }, true)
    expect(state.search).toHaveBeenCalledWith({ sort: 'newest', size: 6 }, true)
  })
  it('hides disabled sections and disables their queries while keeping search accessible', () => {
    state.settings = { ...defaultSitePresentation(), homepageMode: 'CATALOG', showPopular: false, showLatest: false, showQuickStart: false }
    render(<HomePage />)
    expect(screen.queryByText('home.popularTitle')).toBeNull()
    expect(screen.queryByText('home.latestTitle')).toBeNull()
    expect(screen.queryByText('quick-start')).toBeNull()
    expect(screen.getByRole('textbox', { name: 'skill-search' })).toBeTruthy()
    expect(state.search).toHaveBeenCalledWith({ sort: 'downloads', size: 6 }, false)
    expect(state.search).toHaveBeenCalledWith({ sort: 'newest', size: 6 }, false)
  })
  it('renders localized customization as plain text', () => {
    state.settings = { ...defaultSitePresentation(), heroTitle: { zh: '<b>团队技能</b>' }, heroDescription: { en: 'English fallback' } }
    render(<HomePage />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('<b>团队技能</b>')
    expect(screen.getByText('English fallback')).toBeTruthy()
    expect(document.querySelector('h1 b')).toBeNull()
  })
})
