// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { recommendationApi, RECOMMENDATION_KEY, type RecommendationDisplay, type RecommendationTopicView, type RecommendedSkill } from '@/api/recommendations'
import { RecommendationPreview } from './recommendation-preview'

vi.mock('@tanstack/react-router', () => ({ useRouter: () => undefined, useNavigate: () => vi.fn(), Link: ({ children, ...props }: { children: ReactNode }) => <a {...props}>{children}</a> }))
vi.mock('react-i18next', async original => ({ ...await original<typeof import('react-i18next')>(), useTranslation: () => ({ i18n: { language: 'en' }, t: (key: string) => key }) }))
vi.mock('@/api/recommendations', async original => ({ ...await original<typeof import('@/api/recommendations')>(), recommendationApi: { topic: vi.fn() } }))
const skill = (id: number, displayName: string): RecommendedSkill => ({ id, displayName, slug: `skill-${id}`, namespace: 'global', summary: `Purpose of ${displayName}`, labels: [{ slug: 'work', displayName: 'Work' }], downloadCount: 0, starCount: 0, ratingCount: 0, updatedAt: '', canSubmitPromotion: false, publishedVersion: { id, version: '1.0.0', status: 'PUBLISHED' } })
const first = skill(1, 'Preview member'), second = skill(2, 'Complete member'), third = skill(3, 'Another topic member')
const topic = { id: 'first', title: 'First topic', description: 'First purpose', skillCount: 2, coverStyle: 'design' as const, skills: [first] }
const content: RecommendationDisplay = { featuredSkills: [first], topics: [topic, { ...topic, id: 'second', title: 'Second topic', skills: [third] }] }
const response = (name = 'First topic', skills = [first, second]): RecommendationTopicView => ({ id: name === 'Second topic' ? 'second' : 'first', skillCount: skills.length, title: name, description: `${name} purpose`, coverStyle: 'design', skills: skills.map(skill => ({ id: skill.id, displayName: skill.displayName, slug: skill.slug, namespace: skill.namespace, summary: skill.summary, labels: skill.labels, publishedVersion: skill.publishedVersion ? { id: skill.publishedVersion.id, version: skill.publishedVersion.version, status: skill.publishedVersion.status } : undefined })) })
function setup(source: 'public' | 'draft' = 'public', client = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  render(<QueryClientProvider client={client}><RecommendationPreview content={content} topicSource={source} /></QueryClientProvider>)
  return client
}
function browse(index = 0) { fireEvent.click(screen.getAllByRole('button', { name: 'recommendations.browseTopic', hidden: true })[index]) }
beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} })
  vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
  HTMLElement.prototype.scrollTo = vi.fn()
  HTMLElement.prototype.scrollIntoView = vi.fn()
  window.scrollTo = vi.fn()
  vi.mocked(recommendationApi.topic).mockResolvedValue(response())
})
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); vi.clearAllMocks() })

describe('inline recommendation topics', () => {
  it('opens the full fetched collection in place and restores filters and trigger focus', async () => {
    setup()
    const input = screen.getByRole('searchbox')
    fireEvent.change(input, { target: { value: 'Purpose' } })
    const trigger = screen.getAllByRole('button', { name: 'recommendations.browseTopic' })[0]
    browse()
    const dialog = await screen.findByRole('region', { name: 'First topic' })
    await within(dialog).findByText('Complete member')
    expect(recommendationApi.topic).toHaveBeenCalledWith('first')
    expect(dialog.querySelectorAll('[data-collection-skill]')).toHaveLength(2)
    expect(dialog.querySelector('#skill-reader')).toBeNull()
    expect(trigger.getAttribute('aria-expanded')).toBe('true')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(dialog.closest('.recommendation-topics-section')).toBeTruthy()
    fireEvent.click(within(dialog).getAllByRole('button', { name: 'recommendations.collapseTopic' })[0])
    await waitFor(() => expect(screen.queryByRole('region', { name: 'First topic' })).toBeNull())
    await waitFor(() => expect(document.activeElement).toBe(trigger))
    expect((input as HTMLInputElement).value).toBe('Purpose')
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
  })
  it('uses the full local draft without fetching the public topic', async () => {
    setup('draft')
    browse(1)
    const dialog = await screen.findByRole('region', { name: 'Second topic' })
    expect(within(dialog).getByRole('heading', { name: 'Another topic member' })).toBeTruthy()
    expect(within(dialog).queryByText('Complete member')).toBeNull()
    expect(recommendationApi.topic).not.toHaveBeenCalled()
  })
  it('does not show cached old members while a fresh authorization-filtered response is pending', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    client.setQueryData([...RECOMMENDATION_KEY, 'topic', 'first', 'en'], response('Old title', [second]))
    let complete: (value: RecommendationTopicView) => void = () => {}
    vi.mocked(recommendationApi.topic).mockImplementation(() => new Promise(resolve => { complete = resolve }))
    setup('public', client); browse()
    const dialog = await screen.findByRole('region', { name: 'First topic' })
    expect(within(dialog).queryByText('Complete member')).toBeNull()
    expect(within(dialog).getByRole('status')).toBeTruthy()
    await act(async () => { complete(response('Current title', [])) })
    expect(await within(dialog).findByText('recommendations.topicEmpty')).toBeTruthy()
    expect(within(dialog).queryByText('Complete member')).toBeNull()
  })
  it('keeps the window open on failure and can retry into an empty collection', async () => {
    vi.mocked(recommendationApi.topic).mockRejectedValueOnce(new Error('Unavailable')).mockResolvedValueOnce(response('First topic', []))
    setup(); browse()
    const dialog = await screen.findByRole('region', { name: 'First topic' })
    await within(dialog).findByRole('alert')
    expect(dialog.querySelectorAll('[data-collection-skill]')).toHaveLength(0)
    fireEvent.click(within(dialog).getByRole('button', { name: 'recommendations.retry' }))
    expect(await within(dialog).findByText('recommendations.topicEmpty')).toBeTruthy()
  })
  it('a late response for a closed topic cannot replace a newer topic', async () => {
    let complete: (value: RecommendationTopicView) => void = () => {}
    vi.mocked(recommendationApi.topic).mockImplementation(id => id === 'first' ? new Promise(resolve => { complete = resolve }) : Promise.resolve(response('Second topic', [third])))
    setup(); browse()
    let dialog = await screen.findByRole('region', { name: 'First topic' })
    fireEvent.click(within(dialog).getAllByRole('button', { name: 'recommendations.collapseTopic' })[0])
    await waitFor(() => expect(screen.queryByRole('region', { name: 'First topic' })).toBeNull())
    browse(1); dialog = await screen.findByRole('region', { name: 'Second topic' })
    await within(dialog).findByRole('heading', { name: 'Another topic member' })
    await act(async () => { complete(response()) })
    expect(within(dialog).queryByText('Complete member')).toBeNull()
    expect(within(dialog).getByRole('heading', { name: 'Second topic' })).toBeTruthy()
  })
  it('offers only skill-detail links and no usage reader in the expanded topic', async () => {
    setup(); browse()
    const region = await screen.findByRole('region', { name: 'First topic' })
    await within(region).findByText('Complete member')
    expect(within(region).queryByRole('button', { name: 'recommendations.readUsage' })).toBeNull()
    expect(within(region).getAllByText('recommendations.viewSkill')).toHaveLength(2)
    expect(region.querySelector('.recommendation-usage')).toBeNull()
  })
})
