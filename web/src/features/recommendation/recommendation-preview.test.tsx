// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import type { RecommendationDisplay, RecommendedSkill } from '@/api/recommendations'
import { RecommendationPreview, recommendationCarouselPage } from './recommendation-preview'
import { RecommendationCover, recommendationCoverStyle } from './recommendation-cover'

const navigate = vi.fn()
vi.mock('@tanstack/react-router', () => ({ useRouter: () => undefined,
  useNavigate: () => navigate,
  Link: ({ children }: { children: ReactNode }) => <a>{children}</a>,
}))
vi.mock('react-i18next', async original => ({ ...await original<typeof import('react-i18next')>(), useTranslation: () => ({ i18n: { language: 'en' }, t: (key: string, values?: { page?: number }) => values?.page ? `${key} ${values.page}` : key }) }))
vi.mock('./recommendation-skill-card', () => ({ RecommendationSkillCard: ({ skill, onClick }: { skill: RecommendedSkill; onClick: () => void }) => <button onClick={onClick}>{skill.displayName}</button> }))

const skill = (id: number, name: string, labels: RecommendedSkill['labels'], summary = ''): RecommendedSkill => ({
  id, displayName: name, slug: `skill-${id}`, namespace: 'global', summary, labels, downloadCount: 0, starCount: 0, ratingCount: 0, updatedAt: '', canSubmitPromotion: false,
})
const skills = [
  skill(1, 'Meeting notes', [{ slug: 'work', displayName: 'Office' }], 'Extract decisions and owners'),
  skill(2, 'Code review', [{ slug: 'dev', displayName: 'Development' }], 'Check changes'),
  skill(3, 'No label', []),
]
const content: RecommendationDisplay = {
  featuredSkills: skills,
  topics: Array.from({ length: 6 }, (_, index) => ({ id: `topic-${index}`, title: `Topic ${index}`, description: 'Tasks', skillCount: 1, skills: [skills[0]] })),
}
beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} })
  vi.stubGlobal('matchMedia', () => ({ matches: true }))
  HTMLElement.prototype.scrollTo = vi.fn()
})
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.clearAllMocks() })

describe('public recommendations', () => {
  it('combines existing label filters with purpose search and clears an empty result', () => {
    render(<RecommendationPreview content={content} />)
    fireEvent.click(screen.getByRole('button', { name: 'Development' }))
    expect(screen.queryByRole('button', { name: 'Meeting notes' })).toBeNull()
    expect(screen.getByRole('button', { name: 'Code review' })).toBeTruthy()
    const input = screen.getByRole('searchbox')
    input.focus()
    fireEvent.change(input, { target: { value: 'decisions' } })
    expect(document.activeElement).toBe(input)
    expect(screen.getByText('recommendations.noFeaturedResults')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'recommendations.clearFilters' }))
    expect(screen.getByRole('button', { name: 'Meeting notes' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'No label' })).toBeTruthy()
    fireEvent.change(input, { target: { value: 'owners' } })
    expect(screen.getByRole('button', { name: 'Meeting notes' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Code review' })).toBeNull()
  })
  it('updates page dots with arrows and keeps the selected dot keyboard reachable', () => {
    render(<RecommendationPreview content={content} />)
    const next = screen.getByRole('button', { name: 'recommendations.next' })
    fireEvent.click(next)
    const dot = screen.getByRole('button', { name: 'recommendations.topicPage 2' })
    expect(dot.getAttribute('aria-current')).toBe('true')
    expect((next as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: 'recommendations.topicPage 1' }))
    expect(screen.getByRole('button', { name: 'recommendations.topicPage 1' }).getAttribute('aria-current')).toBe('true')
  })
  it('keeps skill detail routing with the original namespace and slug', () => {
    render(<RecommendationPreview content={content} />)
    fireEvent.click(screen.getByRole('button', { name: 'Meeting notes' }))
    expect(navigate).toHaveBeenCalledWith({ to: '/space/$namespace/$slug', params: { namespace: 'global', slug: 'skill-1' } })
  })
  it('falls back to all labels if updated content removes the selected label', () => {
    const view = render(<RecommendationPreview content={content} />)
    fireEvent.click(screen.getByRole('button', { name: 'Development' }))
    view.rerender(<RecommendationPreview content={{ topics: [], featuredSkills: [skills[0]] }} />)
    expect(screen.getByRole('button', { name: 'Meeting notes' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'recommendations.allLabels' }).getAttribute('aria-pressed')).toBe('true')
  })
})

describe('editorial covers', () => {
  it('keeps old topic identities stable and honors an explicit style', () => {
    expect(recommendationCoverStyle('daily-work')).toBe('office')
    expect(recommendationCoverStyle('custom-topic')).toBe(recommendationCoverStyle('custom-topic'))
    expect(recommendationCoverStyle('daily-work', 'security')).toBe('security')
    expect(recommendationCoverStyle('daily-work', 'unsupported')).toBe('office')
    expect(recommendationCoverStyle('constructor')).toMatch(/office|design|learning|content|research|security/)
  })
  it('recovers from a failed uploaded cover and tries a changed URL', () => {
    const view = render(<RecommendationCover topicId="daily-work" title="Real topic" skillNames={["Actual skill"]} coverUrl="/api/v1/site-presentation/assets/first" />)
    const image = view.container.querySelector('img')!
    expect(image).toBeTruthy()
    fireEvent.error(image)
    expect(view.container.querySelector('img')).toBeNull()
    expect(screen.getByText('Real topic')).toBeTruthy()
    view.rerender(<RecommendationCover topicId="daily-work" title="Real topic" skillNames={["Actual skill"]} coverUrl="/api/v1/site-presentation/assets/second" />)
    expect(view.container.querySelector('img')).toBeTruthy()
  })
})

describe('partial carousel pages', () => {
  it.each([4, 5, 7])('keeps the last page selected for %i topics with three cards per viewport', count => {
    const width = 300, viewport = 932, max = count * 316 - 16 - viewport
    expect(recommendationCarouselPage(max, width, 3, count, max)).toBe(Math.ceil(count / 3) - 1)
    expect(recommendationCarouselPage(0, width, 3, count, max)).toBe(0)
  })
  it.each([4, 5, 7])('reflects a clamped browser scroll in page dots for %i topics', count => {
    const topics = Array.from({ length: count }, (_, index) => ({ ...content.topics[0], id: `partial-${index}` }))
    const view = render(<RecommendationPreview content={{ ...content, topics }} />)
    const carousel = view.container.querySelector('.recommendation-carousel') as HTMLDivElement
    const max = count * 316 - 16 - 932
    Object.defineProperties(carousel, { clientWidth: { value: 932 }, scrollWidth: { value: max + 932 }, scrollLeft: { value: max, writable: true } })
    Object.defineProperty(carousel.firstElementChild, 'offsetWidth', { value: 300 })
    fireEvent.scroll(carousel)
    expect(screen.getByRole('button', { name: `recommendations.topicPage ${Math.ceil(count / 3)}` }).getAttribute('aria-current')).toBe('true')
    expect((screen.getByRole('button', { name: 'recommendations.next' }) as HTMLButtonElement).disabled).toBe(true)
  })
  it('recalculates the selected page when viewport capacity changes', () => {
    expect(recommendationCarouselPage(316, 300, 3, 4, 316)).toBe(1)
    expect(recommendationCarouselPage(316, 300, 1, 4, 948)).toBe(1)
    expect(recommendationCarouselPage(948, 300, 1, 4, 948)).toBe(3)
    expect(recommendationCarouselPage(0, 300, 4, 4, 0)).toBe(0)
  })
})
describe('real topic cover text', () => {
  it.each(['office', 'design', 'learning', 'content', 'research', 'security'])('uses live topic and skill data for %s', coverStyle => {
    const view = render(<RecommendationCover topicId="one" coverStyle={coverStyle} title="First topic" skillNames={['First skill', 'Second skill', 'Third skill']} />)
    expect(screen.getByText('First topic')).toBeTruthy()
    expect(screen.getByText('First skill')).toBeTruthy()
    view.rerender(<RecommendationCover topicId="two" coverStyle={coverStyle} title="Changed topic" skillNames={['Changed skill']} />)
    expect(screen.queryByText('First topic')).toBeNull()
    expect(screen.queryByText('First skill')).toBeNull()
    expect(screen.getByText('Changed topic')).toBeTruthy()
    expect(screen.getByText('Changed skill')).toBeTruthy()
    view.rerender(<RecommendationCover topicId="swatch" coverStyle={coverStyle} />)
    expect(view.container.textContent).toBe('')
  })
})
