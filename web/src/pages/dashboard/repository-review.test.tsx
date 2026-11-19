/** @vitest-environment jsdom */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { reviewApi } from '@/api/client'
import type { ReviewTask } from '@/api/types'
import { RepositoryReviewPage } from './repository-review'

const route = vi.hoisted(() => ({ id: '42', search: {} as Record<string, unknown>, navigate: vi.fn() }))
vi.mock('@tanstack/react-router', () => ({ useParams: () => ({ snapshotId: route.id }), useSearch: () => route.search, useNavigate: () => route.navigate }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: { userId: 'reviewer' } }) }))
vi.mock('react-i18next', async original => ({ ...await original<typeof import('react-i18next')>(), useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }) }))
vi.mock('@/features/skill/skill-avatar', () => ({ SkillAvatar: () => null }))
vi.mock('@/shared/ui/select', () => ({ Select: ({ children }: { children: React.ReactNode }) => <div>{children}</div>, SelectTrigger: ({ children }: { children: React.ReactNode }) => <button>{children}</button>, SelectValue: () => null, SelectContent: () => null, SelectItem: () => null }))
const task: ReviewTask = { id: 9, skillVersionId: 101, namespace: 'team', skillSlug: 'skill-one', displayName: 'Skill one', subjectType: 'SKILL_VERSION' as const, status: 'PENDING' as const, version: '1.0.0', reviewKind: 'FIRST_PUBLISH', submittedBy: 'author', submittedAt: '2026-11-19T00:00:00Z' }
function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><RepositoryReviewPage /></QueryClientProvider>)
  return client
}
beforeEach(() => {
  route.id = '42'; route.search = {}; route.navigate.mockClear()
  vi.spyOn(reviewApi, 'syncGroup').mockResolvedValue({ snapshotId: 42, repositoryId: 100, namespaceId: 8, namespace: 'team', owner: 'upstream', repository: 'skills', commitSha: 'a'.repeat(40), capturedAt: '2026-11-19T00:00:00Z', total: 2, scanPassed: 2, scanWaiting: 0, scanBlocked: 0 })
  vi.spyOn(reviewApi, 'syncGroupMembers').mockResolvedValue({ items: [{ review: task, scanStatus: 'PASSED' }], total: 1, page: 0, size: 20 })
})
afterEach(() => { cleanup(); vi.restoreAllMocks() })
describe('repository review page', () => {
  it('loads the authorized snapshot before requesting its members', async () => {
    route.search = { q: 'repository filter', memberQ: 'skill-one', memberStatus: 'APPROVED', page: 1 }
    mount()
    expect(await screen.findByRole('heading', { name: 'upstream/skills' })).toBeTruthy()
    await waitFor(() => expect(reviewApi.syncGroupMembers).toHaveBeenCalledWith(42, expect.objectContaining({ namespaceId: 8, q: 'skill-one', status: 'APPROVED', page: 1 })))
    expect(screen.queryByRole('tablist')).toBeNull()
  })
  it('keeps selection actions in the same surface as member rows', async () => {
    mount()
    const checkbox = (await screen.findAllByLabelText('reviewBatch.selectTask'))[0]
    fireEvent.click(checkbox)
    const toolbar = await screen.findByRole('region', { name: 'reviewBatch.selection' })
    expect(toolbar.closest('[data-review-results-panel]')?.contains(checkbox)).toBe(true)
    expect(toolbar.closest('[data-review-results-toolbar]')).toBeTruthy()
  })
  it('returns to the original list filters and page, excluding member filters', async () => {
    route.search = { q: 'upstream', status: 'ALL', memberStatus: 'REJECTED', memberQ: 'member', groupPage: 3, page: 1 }
    mount()
    await screen.findByRole('heading', { name: 'upstream/skills' })
    fireEvent.click(screen.getByRole('button', { name: 'reviewGroups.back' }))
    expect(route.navigate).toHaveBeenLastCalledWith({ to: '/dashboard/reviews', search: expect.objectContaining({ q: 'upstream', status: 'ALL', page: 3, memberStatus: undefined, memberQ: undefined, snapshotId: undefined }) })
  })
  it('passes snapshot and member context when opening an individual review', async () => {
    route.search = { memberQ: 'skill-one', memberStatus: 'PENDING', groupPage: 2 }
    mount()
    fireEvent.click((await screen.findAllByRole('button', { name: 'reviewCenter.open' }))[0])
    expect(route.navigate).toHaveBeenLastCalledWith(expect.objectContaining({ to: '/dashboard/reviews/$id', params: { id: '9' }, search: expect.objectContaining({ snapshotId: 42, memberQ: 'skill-one', groupPage: 2 }) }))
  })
  it('does not query members after the snapshot permission check fails', async () => {
    vi.mocked(reviewApi.syncGroup).mockRejectedValue({ status: 403 })
    mount()
    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(reviewApi.syncGroupMembers).not.toHaveBeenCalled()
  })
  it('rejects an invalid snapshot route without issuing API requests', async () => {
    route.id = 'not-an-id'
    mount()
    expect(screen.getByRole('alert')).toBeTruthy()
    expect(reviewApi.syncGroup).not.toHaveBeenCalled()
    expect(reviewApi.syncGroupMembers).not.toHaveBeenCalled()
  })
})
