// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { SearchPage } from './search'

const state = vi.hoisted(() => ({
  query: {} as Record<string, unknown>, favorites: [] as Array<Record<string, unknown>>, starredOnly: false,
  next: vi.fn(), retry: vi.fn(), navigate: vi.fn(), trim: vi.fn(),
}))
vi.mock('@/api/client', () => ({ fetchJson: vi.fn(), WEB_API_PREFIX: '/api/web' }))
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => state.navigate, useSearch: () => ({ q: '', source: 'platform', sort: 'newest', starredOnly: state.starredOnly }) }))
vi.mock('@tanstack/react-query', async original => ({ ...await original<typeof import('@tanstack/react-query')>(), useQueryClient: () => ({ setQueryData: state.trim }) }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'zh' } }) }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ isAuthenticated: true, user: { userId: 'viewer' }, isLoading: false }) }))
vi.mock('@/shared/hooks/use-label-queries', () => ({ useVisibleLabels: () => ({ data: [] }) }))
vi.mock('@/shared/hooks/use-user-queries', () => ({ useMyStars: () => ({ data: state.favorites, isLoading: false, isFetching: false }) }))
vi.mock('@/features/search/use-market-skills', async original => ({ ...await original<typeof import('@/features/search/use-market-skills')>(), useMarketSkills: () => state.query }))
vi.mock('@/features/search/market-search-controls', () => ({ MarketSearchControls: ({ onSourceChange }: { onSourceChange: (source: string) => void }) => <button onClick={() => onSourceChange('github')}>change source</button> }))
vi.mock('@/features/skill/skill-card', () => ({ SkillCard: ({ skill }: { skill: { displayName: string } }) => <article>{skill.displayName}</article> }))

let intersect: IntersectionObserverCallback
class Observer {
  constructor(callback: IntersectionObserverCallback) { intersect = callback }
  observe() {}
  disconnect() {}
}
function items(count: number) { return Array.from({ length: count }, (_, index) => ({ id: index, displayName: `Skill ${index}`, slug: `skill-${index}`, namespace: 'global', updatedAt: '', downloadCount: 0, starCount: 0, ratingCount: 0 })) }
function enterBottom() { act(() => intersect([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver)) }

beforeEach(() => {
  state.starredOnly = false
  state.favorites = []
  state.query = { data: { pages: [{ items: items(12), total: 26, page: 0, size: 12 }] }, hasNextPage: true, isLoading: false, isFetching: false, isError: false, fetchNextPage: state.next, refetch: state.retry }
  vi.stubGlobal('IntersectionObserver', Observer)
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
})
afterEach(() => { cleanup(); vi.clearAllMocks(); vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('market incremental list states', () => {
  it('appends near the bottom without rendering pagination or moving scroll', () => {
    render(<SearchPage />)
    expect(screen.getAllByRole('article')).toHaveLength(12)
    enterBottom()
    expect(state.next).toHaveBeenCalledWith({ cancelRefetch: false })
    expect(window.scrollTo).not.toHaveBeenCalled()
    expect(screen.queryByRole('navigation')).toBeNull()
  })

  it('keeps existing cards after an append failure and offers a manual retry', () => {
    Object.assign(state.query, { isError: true, isFetchNextPageError: true })
    render(<SearchPage />)
    expect(screen.getAllByRole('article')).toHaveLength(12)
    expect(screen.getByRole('alert').textContent).toContain('search.market.appendFailed')
    fireEvent.click(screen.getByRole('button', { name: 'search.source.retry' }))
    expect(state.next).toHaveBeenCalledOnce()
  })

  it('uses bounded increments for favorites and displays the end state', () => {
    state.starredOnly = true
    state.favorites = items(26)
    render(<SearchPage />)
    expect(screen.getAllByRole('article')).toHaveLength(12)
    enterBottom()
    expect(screen.getAllByRole('article')).toHaveLength(24)
    enterBottom()
    expect(screen.getAllByRole('article')).toHaveLength(26)
    expect(screen.getByRole('status').textContent).toBe('search.market.allLoaded')
    expect(state.next).not.toHaveBeenCalled()
  })

  it('trims a destination cache to one batch and resets paging on filter changes', () => {
    render(<SearchPage />)
    fireEvent.click(screen.getByRole('button', { name: 'change source' }))
    const trim = state.trim.mock.calls[0][1]
    expect(trim({ pages: ['first', 'second'], pageParams: [0, 1] })).toEqual({ pages: ['first'], pageParams: [0] })
    expect(state.navigate).toHaveBeenCalledWith({ to: '/search', search: { q: '', namespace: '', source: 'github', label: '', sort: 'newest', starredOnly: false, page: 0 } })
  })
})
