// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import type { LabelItem, SkillSummary } from '@/api/types'
import type { SitePresentationSettings } from '@/api/site-presentation'
import { defaultSitePresentation } from '@/shared/hooks/use-site-presentation'
import { CatalogHomePage } from './catalog-home'

interface QueryState { items: SkillSummary[]; loading: boolean; error: boolean }
const state = vi.hoisted((): {
  settings: SitePresentationSettings; labels: LabelItem[]; popular: QueryState; latest: QueryState;
  navigate: (target: unknown) => void; queries: (params: unknown, enabled: boolean) => void; retryPopular: () => void; retryLatest: () => void
} => ({ settings: { siteName: null, logoUrl: null, faviconUrl: null, homepageMode: 'CATALOG', heroTitle: {}, heroDescription: {}, showPopular: true, showLatest: true, showQuickStart: true, footerVisible: true }, labels: [], popular: { items: [], loading: false, error: false }, latest: { items: [], loading: false, error: false }, navigate: vi.fn(), queries: vi.fn(), retryPopular: vi.fn(), retryLatest: vi.fn() }))
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => state.navigate }))
vi.mock('react-i18next', async original => ({ ...await original<typeof import('react-i18next')>(), useTranslation: () => ({ t: (key: string) => key, i18n: { resolvedLanguage: 'zh' } }) }))
vi.mock('@/shared/hooks/use-site-presentation', async original => ({ ...await original<typeof import('@/shared/hooks/use-site-presentation')>(), useSitePresentation: () => ({ settings: state.settings }) }))
vi.mock('@/shared/hooks/use-label-queries', () => ({ useVisibleLabels: () => ({ data: state.labels }) }))
vi.mock('@/shared/hooks/use-skill-queries', () => ({ useSearchSkills: (params: { sort: string }, enabled: boolean) => {
  state.queries(params, enabled)
  const source = params.sort === 'downloads' ? state.popular : state.latest
  return { data: { items: source.items }, isLoading: source.loading, isError: source.error, refetch: params.sort === 'downloads' ? state.retryPopular : state.retryLatest }
} }))
vi.mock('@/shared/components/brand-mark', () => ({ BrandMark: () => <div data-testid="brand-mark" /> }))
vi.mock('@/shared/components/quick-start', () => ({ QuickStartSection: ({ hideHeading }: { hideHeading?: boolean }) => <div data-testid="quick-guide" data-heading-hidden={hideHeading}>Existing installation commands</div> }))
vi.mock('@/features/skill/skill-card', () => ({ SkillCard: ({ skill, onClick }: { skill: SkillSummary; onClick: () => void }) => <button onClick={onClick}>{skill.displayName}</button> }))

afterEach(() => {
  cleanup(); vi.clearAllMocks(); state.settings = defaultSitePresentation(); state.labels = []
  state.popular = { items: [], loading: false, error: false }; state.latest = { items: [], loading: false, error: false }
})
const skill = (id: number, displayName: string): SkillSummary => ({ id, displayName, slug: `skill-${id}`, namespace: 'team', downloadCount: 10, starCount: 0, ratingCount: 0, updatedAt: '2026-11-10T00:00:00Z', canSubmitPromotion: false })

describe('catalog homepage discovery', () => {
  it('shows the configured identity as plain text and provides accessible search and browsing', () => {
    const longTitle = 'A'.repeat(160)
    state.settings = { ...defaultSitePresentation(), siteName: 'Team Library', heroTitle: { zh: longTitle }, heroDescription: { zh: '<script>plain text</script>' } }
    render(<CatalogHomePage />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe(longTitle)
    expect(screen.getByRole('heading', { level: 1 }).className).toContain('[overflow-wrap:anywhere]')
    expect(screen.getByText('<script>plain text</script>')).toBeTruthy()
    expect(document.querySelector('script')).toBeNull()
    const input = screen.getByRole('textbox', { name: 'catalog.searchLabel' })
    fireEvent.change(input, { target: { value: '  meeting notes  ' } })
    fireEvent.submit(input.closest('form')!)
    expect(state.navigate).toHaveBeenCalledWith({ to: '/search', search: { q: 'meeting notes', sort: 'relevance', page: 0, starredOnly: false } })
    fireEvent.click(screen.getByRole('button', { name: 'catalog.browseAll' }))
    expect(state.navigate).toHaveBeenLastCalledWith({ to: '/search', search: { q: '', label: '', sort: 'relevance', page: 0, starredOnly: false } })
  })
  it('uses actual visible label values and never fabricates a category list when none exist', () => {
    state.labels = [{ slug: 'team-tools', type: 'RECOMMENDED', displayName: 'Team tools' }, { slug: '', type: 'RECOMMENDED', displayName: 'Invalid label' }]
    render(<CatalogHomePage />)
    expect(screen.getByRole('navigation', { name: 'catalog.labelsTitle' })).toBeTruthy()
    expect(screen.queryByText('Invalid label')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Team tools' }))
    expect(state.navigate).toHaveBeenCalledWith({ to: '/search', search: { q: '', label: 'team-tools', sort: 'relevance', page: 0, starredOnly: false } })
  })
  it('keeps many labels compact until the visitor expands them', () => {
    state.labels = Array.from({ length: 12 }, (_, index) => ({ slug: `label-${index}`, type: 'RECOMMENDED', displayName: `Label ${index}` }))
    render(<CatalogHomePage />)
    expect(screen.getByRole('button', { name: 'Label 7' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Label 8' })).toBeNull()
    const expand = screen.getByRole('button', { name: 'catalog.expandLabels' })
    expect(expand.getAttribute('aria-expanded')).toBe('false')
    fireEvent.click(expand)
    expect(screen.getByRole('button', { name: 'Label 11' })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'catalog.collapseLabels' }))
    expect(screen.queryByRole('button', { name: 'Label 8' })).toBeNull()
  })
  it('keeps search and a meaningful entry when all configured modules are disabled', () => {
    state.settings = { ...defaultSitePresentation(), showPopular: false, showLatest: false, showQuickStart: false }
    render(<CatalogHomePage />)
    expect(screen.getByRole('textbox', { name: 'catalog.searchLabel' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'catalog.browseAll' })).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'catalog.searchStartTitle' })).toBeTruthy()
    expect(screen.queryByRole('navigation', { name: 'catalog.labelsTitle' })).toBeNull()
    expect(screen.queryByRole('heading', { name: 'home.popularTitle' })).toBeNull()
    expect(screen.queryByRole('heading', { name: 'home.latestTitle' })).toBeNull()
    expect(screen.queryByTestId('quick-guide')).toBeNull()
    expect(state.queries).toHaveBeenCalledWith({ sort: 'downloads', size: 6 }, false)
    expect(state.queries).toHaveBeenCalledWith({ sort: 'newest', size: 6 }, false)
  })
  it('distinguishes empty visible results from unavailable sections', () => {
    render(<CatalogHomePage />)
    expect(screen.getByText('catalog.popularEmpty')).toBeTruthy()
    expect(screen.getByText('catalog.latestEmpty')).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
    expect(screen.getAllByRole('button', { name: 'catalog.viewAll' })).toHaveLength(2)
  })
  it('announces initial loading without showing an empty result', () => {
    state.popular.loading = true; state.latest.loading = true
    render(<CatalogHomePage />)
    expect(screen.getAllByRole('status')).toHaveLength(2)
    expect(screen.queryByText('catalog.popularEmpty')).toBeNull()
    expect(screen.queryByText('catalog.latestEmpty')).toBeNull()
  })
  it('allows retrying a failed section while the other real results remain usable', () => {
    state.popular.error = true; state.latest.items = [skill(2, 'Recent team skill')]
    render(<CatalogHomePage />)
    expect(screen.getByRole('alert').textContent).toContain('catalog.loadFailed')
    fireEvent.click(screen.getByRole('button', { name: 'catalog.retry' }))
    expect(state.retryPopular).toHaveBeenCalledTimes(1)
    expect(state.retryLatest).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Recent team skill' }))
    expect(state.navigate).toHaveBeenCalledWith({ to: '/space/team/skill-2' })
  })
  it('keeps popular and latest results grouped and routes each view-all to its existing sort', () => {
    state.popular.items = [skill(1, 'Popular team skill')]; state.latest.items = [skill(2, 'Recent team skill')]
    render(<CatalogHomePage />)
    const popular = screen.getByRole('region', { name: 'home.popularTitle' })
    const latest = screen.getByRole('region', { name: 'home.latestTitle' })
    expect(within(popular).getByText('Popular team skill')).toBeTruthy()
    expect(within(latest).getByText('Recent team skill')).toBeTruthy()
    fireEvent.click(within(popular).getByRole('button', { name: 'catalog.viewAll' }))
    expect(state.navigate).toHaveBeenLastCalledWith({ to: '/search', search: { q: '', label: '', sort: 'downloads', page: 0, starredOnly: false } })
    fireEvent.click(within(latest).getByRole('button', { name: 'catalog.viewAll' }))
    expect(state.navigate).toHaveBeenLastCalledWith({ to: '/search', search: { q: '', label: '', sort: 'newest', page: 0, starredOnly: false } })
  })
  it('keeps the existing command guide in a collapsed accessible disclosure', () => {
    render(<CatalogHomePage />)
    const details = screen.getByText('home.quickStart.title').closest('details')!
    expect(details.open).toBe(false)
    expect(screen.getByTestId('quick-guide').getAttribute('data-heading-hidden')).toBe('true')
    fireEvent.click(details.querySelector('summary')!)
    expect(details.open).toBe(true)
  })
})
