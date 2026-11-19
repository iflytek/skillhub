/** @vitest-environment jsdom */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { reviewApi } from '@/api/client'
import { ReviewRepositoryGroups } from './review-repository-groups'
import { DEFAULT_REVIEW_FILTERS } from './review-center-filters'

vi.mock('react-i18next', async (original) => ({ ...await original<typeof import('react-i18next')>(), useTranslation: () => ({ t: (key: string, args?: Record<string, unknown>) => args?.total ? `${key} ${args.total}` : key, i18n: { language: 'en' } }) }))
afterEach(() => { cleanup(); vi.restoreAllMocks() })
function setup(enabled = true) {
  const onGroup = vi.fn(), onPage = vi.fn()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><ReviewRepositoryGroups filters={{ ...DEFAULT_REVIEW_FILTERS, originType: 'GITHUB_SYNC' }} page={0} enabled={enabled} onGroup={onGroup} onPage={onPage} /></QueryClientProvider>)
  return { onGroup, onPage }
}
describe('repository review groups', () => {
  it('uses the server complete group count rather than the current page length', async () => {
    vi.spyOn(reviewApi, 'syncGroups').mockResolvedValue({ total: 2, page: 0, size: 1, items: [{ snapshotId: 42, namespaceId: 1, namespace: 'team', repositoryId: 100, owner: 'upstream', repository: 'skills', commitSha: 'a'.repeat(40), capturedAt: '2026-11-19T00:00:00Z', total: 39, scanPassed: 30, scanWaiting: 7, scanBlocked: 2 }] })
    const { onGroup } = setup()
    expect(await screen.findByText('reviewGroups.summary 39')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'reviewGroups.open' }))
    expect(onGroup).toHaveBeenCalledWith(42)
    expect(reviewApi.syncGroups).toHaveBeenCalledWith(expect.objectContaining({ status: 'PENDING', page: 0 }))
  })
  it('does not issue a group request until the authorized scope is ready', () => {
    const request = vi.spyOn(reviewApi, 'syncGroups')
    setup(false)
    expect(request).not.toHaveBeenCalled()
  })
  it('separates a failed request from the empty state and offers retry', async () => {
    vi.spyOn(reviewApi, 'syncGroups').mockRejectedValue(new Error('failed'))
    setup()
    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(screen.queryByText('reviewGroups.empty')).toBeNull()
    expect(screen.getByRole('button', { name: 'reviewCenter.retry' })).toBeTruthy()
  })
})
