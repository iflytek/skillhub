// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, createRootRoute, createRoute, createRouter, Outlet, RouterProvider } from '@tanstack/react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { recommendationApi, type RecommendationDisplay, type RecommendationTopicView, type RecommendedSkill } from '@/api/recommendations'
import { RecommendationPreview } from './recommendation-preview'
vi.mock('react-i18next', async original => ({ ...await original<typeof import('react-i18next')>(), useTranslation: () => ({ i18n: { language: 'en' }, t: (key: string, args?: { page?: number }) => args?.page ? `${key} ${args.page}` : key }) }))
vi.mock('@/api/recommendations', async original => ({ ...await original<typeof import('@/api/recommendations')>(), recommendationApi: { topic: vi.fn() } }))
const member: RecommendedSkill = { id: 1, displayName: 'Actual skill', slug: 'actual', namespace: 'global', summary: 'Factual work', labels: [{ slug: 'work', displayName: 'Work' }], downloadCount: 0, starCount: 0, ratingCount: 0, updatedAt: '', canSubmitPromotion: false }
let content: RecommendationDisplay
let y = 0
function setup(featureGate?: Promise<void>) {
  const root = createRootRoute({ component: () => <Outlet /> })
  const home = createRoute({ getParentRoute: () => root, path: '/', component: () => <RecommendationPreview content={content} entryPath="/" /> })
  const featured = createRoute({ getParentRoute: () => root, path: '/featured', beforeLoad: () => featureGate, component: () => <RecommendationPreview content={content} entryPath="/featured" /> })
  const detail = createRoute({ getParentRoute: () => root, path: '/space/$namespace/$slug', component: () => <h1>Skill detail</h1> })
  const history = createMemoryHistory({ initialEntries: ['/'] })
  const router = createRouter({ routeTree: root.addChildren([home, featured, detail]), history })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const view = render(<QueryClientProvider client={client}><RouterProvider router={router} /></QueryClientProvider>)
  return { history, router, client, view }
}
const browse = (index = 0) => fireEvent.click(screen.getAllByRole('button', { name: 'recommendations.browseTopic' })[index])
const topicResponse = (id: string): RecommendationTopicView => ({ id, title: `Topic ${id}`, description: 'Current description', skillCount: 1, skills: [{ id: member.id, displayName: member.displayName, slug: member.slug, namespace: member.namespace, summary: member.summary }] })
beforeEach(() => {
  sessionStorage.clear(); y = 0
  content = { featuredSkills: [member], topics: Array.from({ length: 6 }, (_, index) => ({ id: String(index), title: `Topic ${index}`, description: '', skillCount: 1, skills: [member] })) }
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} })
  vi.stubGlobal('matchMedia', () => ({ matches: true }))
  Object.defineProperty(window, 'scrollY', { configurable: true, get: () => y })
  window.scrollTo = vi.fn((options) => { if (typeof options === 'object') y = options.top ?? 0 })
  HTMLElement.prototype.scrollIntoView = vi.fn()
  HTMLElement.prototype.scrollTo = vi.fn()
  vi.mocked(recommendationApi.topic).mockImplementation(async id => topicResponse(id))
})
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.clearAllMocks(); sessionStorage.clear() })
describe('public recommendation history entry restoration', () => {
  it('returns from a skill using actual Back/Forward with topic, filters, carousel page and scroll intact', async () => {
    const { history } = setup()
    const input = await screen.findByRole('searchbox')
    fireEvent.change(input, { target: { value: 'Factual' } })
    fireEvent.click(screen.getByRole('button', { name: 'Work' }))
    fireEvent.click(screen.getByRole('button', { name: 'recommendations.next' }))
    browse(4)
    const panel = await screen.findByRole('region', { name: 'Topic 4' })
    const link = await within(panel).findByText('recommendations.viewSkill')
    fireEvent.click(link, { ctrlKey: true }); expect(screen.getByRole('region', { name: 'Topic 4' })).toBeTruthy()
    y = 640; fireEvent.scroll(window)
    fireEvent.click(link)
    await screen.findByRole('heading', { name: 'Skill detail' })
    y = 0
    await act(async () => history.back())
    const returned = await screen.findByRole('region', { name: 'Topic 4' })
    await within(returned).findByText('recommendations.viewSkill')
    await waitFor(() => expect(y).toBe(640))
    expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('Factual')
    expect(screen.getByRole('button', { name: 'Work' }).getAttribute('aria-pressed')).toBe('true')
    await act(async () => history.forward())
    await screen.findByRole('heading', { name: 'Skill detail' })
    await act(async () => history.back())
    const again = await screen.findByRole('region', { name: 'Topic 4' })
    await within(again).findByText('recommendations.viewSkill')
    fireEvent.click(within(again).getAllByRole('button', { name: 'recommendations.collapseTopic' })[0])
    expect(screen.getByText('2 / 2')).toBeTruthy()
  })
  it('keeps distinct homepage and featured history entries isolated', async () => {
    const { history, router } = setup()
    fireEvent.change(await screen.findByRole('searchbox'), { target: { value: 'home query' } })
    browse(0); await screen.findByRole('region', { name: 'Topic 0' })
    await act(async () => { await router.navigate({ to: '/featured' }) })
    expect((await screen.findByRole('searchbox') as HTMLInputElement).value).toBe('')
    expect(screen.queryByRole('region', { name: 'Topic 0' })).toBeNull()
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'featured query' } })
    browse(1); await screen.findByRole('region', { name: 'Topic 1' })
    await act(async () => history.back())
    expect(await screen.findByRole('region', { name: 'Topic 0' })).toBeTruthy()
    expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('home query')
    await act(async () => history.forward())
    expect(await screen.findByRole('region', { name: 'Topic 1' })).toBeTruthy()
    expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('featured query')
  })
  it('waits for a fresh delayed collection before restoring scroll and does not revive a removed topic', async () => {
    const { history } = setup()
    await screen.findByRole('searchbox'); browse(2)
    let panel = await screen.findByRole('region', { name: 'Topic 2' })
    y = 750; fireEvent.click(await within(panel).findByText('recommendations.viewSkill'))
    await screen.findByRole('heading', { name: 'Skill detail' })
    let resolve: (value: RecommendationTopicView) => void = () => {}
    vi.mocked(recommendationApi.topic).mockImplementation(() => new Promise(done => { resolve = done }))
    y = 0; await act(async () => history.back())
    panel = await screen.findByRole('region', { name: 'Topic 2' })
    expect(within(panel).getByRole('status')).toBeTruthy()
    expect(y).toBe(0)
    await act(async () => resolve(topicResponse('2')))
    await waitFor(() => expect(y).toBe(750))
    fireEvent.click(within(panel).getByText('recommendations.viewSkill'))
    await screen.findByRole('heading', { name: 'Skill detail' })
    content = { ...content, topics: content.topics.filter(topic => topic.id !== '2') }
    await act(async () => history.back())
    await screen.findByRole('searchbox')
    expect(screen.queryByRole('region', { name: 'Topic 2' })).toBeNull()
    expect(screen.queryByText('Current description')).toBeNull()
  })
  it('preserves a partial carousel offset when its page number did not change before skill navigation', async () => {
    const { history } = setup()
    await screen.findByRole('searchbox')
    fireEvent.click(screen.getByRole('button', { name: 'recommendations.next' }))
    const carousel = document.querySelector('.recommendation-carousel') as HTMLDivElement
    Object.defineProperties(carousel, { clientWidth: { value: 932 }, scrollWidth: { value: 1880 } })
    Object.defineProperty(carousel.firstElementChild, 'offsetWidth', { value: 300 })
    carousel.scrollLeft = 940; fireEvent.scroll(carousel)
    fireEvent.click(screen.getByRole('link', { name: 'Actual skill' }))
    await screen.findByRole('heading', { name: 'Skill detail' })
    await act(async () => history.back())
    await screen.findByRole('searchbox')
    await waitFor(() => expect((document.querySelector('.recommendation-carousel') as HTMLDivElement).scrollLeft).toBe(940))
    expect(screen.getByText('2 / 2')).toBeTruthy()
  })
  it('cancels queued scroll restoration when the old entry starts another real navigation', async () => {
    let release: () => void = () => {}
    const gate = new Promise<void>(resolve => { release = resolve })
    const { history, router } = setup(gate)
    await screen.findByRole('searchbox'); browse(0)
    let panel = await screen.findByRole('region', { name: 'Topic 0' })
    y = 620; fireEvent.click(await within(panel).findByText('recommendations.viewSkill'))
    await screen.findByRole('heading', { name: 'Skill detail' })
    const frames: FrameRequestCallback[] = []
    const raf = vi.spyOn(window, 'requestAnimationFrame').mockImplementation(callback => { frames.push(callback); return frames.length })
    const cancel = vi.spyOn(window, 'cancelAnimationFrame').mockImplementation(() => {})
    y = 0; await act(async () => history.back())
    panel = await screen.findByRole('region', { name: 'Topic 0' })
    await within(panel).findByText('recommendations.viewSkill')
    await waitFor(() => expect(frames.length).toBeGreaterThan(0))
    await act(async () => { frames[0](0) })
    const scroll = vi.mocked(window.scrollTo); scroll.mockClear()
    let navigation: ReturnType<typeof router.navigate> | undefined
    await act(async () => { navigation = router.navigate({ to: '/featured' }) })
    await waitFor(() => expect(router.state.location.pathname).toBe('/featured'))
    const lateFrames = frames.slice(1)
    await act(async () => { lateFrames.forEach(callback => callback(0)) })
    expect(scroll).not.toHaveBeenCalled()
    expect(cancel).toHaveBeenCalled()
    raf.mockRestore(); cancel.mockRestore()
    await act(async () => { release(); await navigation })
  })

})
