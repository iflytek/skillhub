// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiError } from '@/api/client'
import { recommendationApi, RECOMMENDATION_KEY, type RecommendationAdmin } from '@/api/recommendations'
import { RecommendationSettingsPanel } from './recommendation-settings'

vi.mock('react-i18next', async original => ({ ...await original<typeof import('react-i18next')>(), useTranslation: () => ({ i18n: { language: 'en' }, t: (key: string) => key }) }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: { userId: 'admin' } }) }))
vi.mock('@tanstack/react-router', () => ({ useBlocker: () => ({ status: 'idle' }) }))
vi.mock('@/api/recommendations', async original => ({ ...await original<typeof import('@/api/recommendations')>(), recommendationApi: { admin: vi.fn(), save: vi.fn(), publish: vi.fn(), candidates: vi.fn() } }))
vi.mock('./recommendation-preview', () => ({ RecommendationPreview: ({ onManageTopics }: { onManageTopics?: () => void }) => <div>{onManageTopics && <button onClick={onManageTopics}>manage topics</button>}</div> }))
vi.mock('@dnd-kit/react', () => ({ DragDropProvider: ({ children }: { children: React.ReactNode }) => children }))
vi.mock('@dnd-kit/react/sortable', () => ({ useSortable: () => ({ ref: vi.fn(), handleRef: vi.fn(), isDragging: false }), isSortable: () => false }))
afterEach(() => { cleanup(); vi.resetAllMocks() })
async function setup(beforeApply?: (client: QueryClient, response: RecommendationAdmin) => Promise<void>, callbacks: { onDirtyChange?: (dirty: boolean) => void; onBusyChange?: (busy: boolean) => void } = {}) {
  const content = { topics: [{ id: 'meeting', title: 'Meeting', description: '', enabled: true, skillIds: [1] }], featuredSkillIds: [1] }
  const response: RecommendationAdmin = { version: 7, draft: content, published: structuredClone(content), skills: [], usingDefaults: false }
  vi.mocked(recommendationApi.admin).mockResolvedValue(response)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  client.setQueryData(RECOMMENDATION_KEY, { topics: [], featuredSkills: [] })
  render(<QueryClientProvider client={client}><RecommendationSettingsPanel {...callbacks} /></QueryClientProvider>)
  await screen.findByRole('button', { name: 'manage topics' })
  fireEvent.click(screen.getByRole('button', { name: 'manage topics' }))
  fireEvent.click(screen.getByRole('switch'))
  await beforeApply?.(client, response)
  fireEvent.click(screen.getByRole('button', { name: 'recommendations.apply' }))
  return { client, response }
}
describe('recommendation draft publication', () => {
  it('reports unsaved edits and in-flight saving to the surrounding settings navigation', async () => {
    const onDirtyChange = vi.fn()
    const onBusyChange = vi.fn()
    const { response } = await setup(undefined, { onDirtyChange, onBusyChange })
    await waitFor(() => expect(onDirtyChange).toHaveBeenLastCalledWith(true))
    let completeSave: (() => void) | undefined
    vi.mocked(recommendationApi.save).mockImplementation((_version, draft) => new Promise(resolve => {
      completeSave = () => resolve({ ...response, version: 8, draft })
    }))
    fireEvent.click(screen.getByRole('button', { name: 'recommendations.saveDraft' }))
    await waitFor(() => expect(onBusyChange).toHaveBeenLastCalledWith(true))
    await act(async () => { completeSave?.() })
    await waitFor(() => expect(onDirtyChange).toHaveBeenLastCalledWith(false))
    expect(onBusyChange).toHaveBeenLastCalledWith(false)
    expect(recommendationApi.publish).not.toHaveBeenCalled()
  })
  it('keeps edits after save failure and saves only the private draft on retry', async () => {
    const { client, response } = await setup()
    vi.mocked(recommendationApi.save).mockRejectedValueOnce(new Error('Offline')).mockImplementationOnce(async (version, draft) => ({ ...response, version: version + 1, draft }))
    fireEvent.click(screen.getByRole('button', { name: 'recommendations.saveDraft' }))
    expect(await screen.findByText('Offline')).toBeTruthy()
    expect(screen.getByText('recommendations.unsaved')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'recommendations.saveDraft' }))
    await screen.findByText('recommendations.draftSaved')
    expect(recommendationApi.save).toHaveBeenLastCalledWith(7, expect.objectContaining({ topics: [expect.objectContaining({ enabled: false })] }))
    expect(client.getQueryData(RECOMMENDATION_KEY)).toEqual({ topics: [], featuredSkills: [] })
    expect(recommendationApi.publish).not.toHaveBeenCalled()
  })
  it('conflict retains the local draft and requires explicit reload', async () => {
    await setup()
    vi.mocked(recommendationApi.save).mockRejectedValue(new ApiError('Conflict', 409))
    fireEvent.click(screen.getByRole('button', { name: 'recommendations.saveDraft' }))
    await screen.findByText('recommendations.conflict')
    expect(screen.getByText('recommendations.unsaved')).toBeTruthy()
    expect((screen.getByRole('button', { name: 'recommendations.publish' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: 'recommendations.reload' }))
    await waitFor(() => expect(screen.queryByText('recommendations.unsaved')).toBeNull())
  })
  it('keeps the modal opening version when a background query receives newer settings', async () => {
    await setup(async (client, response) => {
      await act(async () => { client.setQueryData(['system-config', 'recommendations', 'admin', 'en'], { ...response, version: 8, draft: { ...response.draft, topics: [{ ...response.draft!.topics[0], title: 'Other administrator' }] } }) })
    })
    vi.mocked(recommendationApi.save).mockRejectedValue(new ApiError('Conflict', 409))
    fireEvent.click(screen.getByRole('button', { name: 'recommendations.saveDraft' }))
    await screen.findByText('recommendations.conflict')
    expect(recommendationApi.save).toHaveBeenCalledWith(7, expect.objectContaining({ topics: [expect.objectContaining({ title: 'Meeting', enabled: false })] }))
  })
  it('does not publish a newer administrator draft that arrives while confirmation is open', async () => {
    const { response, client } = await setup()
    vi.mocked(recommendationApi.save).mockImplementation(async (_version, draft) => ({ ...response, draft, version: 8 }))
    vi.mocked(recommendationApi.publish).mockRejectedValue(new ApiError('Conflict', 409))
    fireEvent.click(screen.getByRole('button', { name: 'recommendations.publish' }))
    await screen.findByRole('dialog')
    await act(async () => { client.setQueryData(['system-config', 'recommendations', 'admin', 'en'], { ...response, version: 9 }) })
    fireEvent.click(screen.getAllByRole('button', { name: 'recommendations.publish' }).at(-1)!)
    await waitFor(() => expect(recommendationApi.publish).toHaveBeenCalledExactlyOnceWith(8))
    expect(screen.getByRole('dialog')).toBeTruthy()
  })
  it('autosaves before publish confirmation then publishes the returned server version', async () => {
    const { response, client } = await setup()
    vi.mocked(recommendationApi.save).mockImplementation(async (_version, draft) => ({ ...response, draft, version: 8 }))
    vi.mocked(recommendationApi.publish).mockResolvedValue({ ...response, version: 9, publishedAt: '2026-11-11T00:00:00Z' })
    fireEvent.click(screen.getByRole('button', { name: 'recommendations.publish' }))
    await screen.findByRole('dialog')
    expect(recommendationApi.publish).not.toHaveBeenCalled()
    fireEvent.click(screen.getAllByRole('button', { name: 'recommendations.publish' }).at(-1)!)
    await screen.findByText('recommendations.published')
    expect(recommendationApi.publish).toHaveBeenCalledExactlyOnceWith(8)
    expect(client.getQueryState(RECOMMENDATION_KEY)?.isInvalidated).toBe(true)
  })
})
