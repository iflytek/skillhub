// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { authoringApi } from '@/api/client'
import i18n from '@/i18n/config'
import { ValidationResults } from './validation-results'
vi.mock('@/api/client', () => ({ authoringApi: { getRun: vi.fn(), listFindings: vi.fn(), cancelRun: vi.fn() } }))
vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
const clients: QueryClient[] = []
function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  clients.push(client)
  render(<QueryClientProvider client={client}><ValidationResults draftId={42} runId={8} /></QueryClientProvider>)
  return client
}
beforeEach(async () => { vi.clearAllMocks(); await i18n.changeLanguage('en') })
afterEach(() => { cleanup(); clients.forEach(client => client.clear()); clients.length = 0 })
describe('structure validation results', () => {
  it('loads final findings and refreshes draft validity on completion', async () => {
    vi.mocked(authoringApi.getRun).mockResolvedValue({ id: 8, draftId: 42, status: 'RUNNING', active: true, terminal: false })
    vi.mocked(authoringApi.listFindings).mockResolvedValueOnce([]).mockResolvedValue([{ id: 1, severity: 'ERROR', ruleCode: 'FRONTMATTER_FIELD_MISSING', filePath: 'SKILL.md', location: 'frontmatter', message: 'Missing description' }])
    const client = mount()
    client.setQueryData(['authoring', 'draft', 42], { validated: false })
    await screen.findByText('Validation is in progress. Findings will appear here.')
    client.setQueryData(['authoring', 'run', 8], { id: 8, draftId: 42, status: 'FAILED', terminal: true, active: false, errorCount: 1 })
    await screen.findByText('Missing description')
    expect(screen.getByText('SKILL.md · frontmatter')).toBeTruthy()
    expect(client.getQueryState(['authoring', 'draft', 42])?.isInvalidated).toBe(true)
    expect(screen.queryByRole('button', { name: 'Cancel run' })).toBeNull()
  })
  it('cancels an active run and shows its terminal state', async () => {
    vi.mocked(authoringApi.getRun).mockResolvedValue({ id: 8, draftId: 42, status: 'RUNNING', active: true })
    vi.mocked(authoringApi.listFindings).mockResolvedValue([])
    vi.mocked(authoringApi.cancelRun).mockResolvedValue({ id: 8, draftId: 42, status: 'CANCELLED', terminal: true, active: false })
    mount()
    fireEvent.click(await screen.findByRole('button', { name: 'Cancel run' }))
    await waitFor(() => expect(authoringApi.cancelRun).toHaveBeenCalledWith(8))
    await screen.findByText(/Cancelled/)
    expect(screen.queryByText(/passed validation/)).toBeNull()
  })
  it('does not fetch findings for a run belonging to another draft', async () => {
    vi.mocked(authoringApi.getRun).mockResolvedValue({ id: 8, draftId: 99, status: 'SUCCEEDED', terminal: true })
    mount()
    await screen.findByRole('alert')
    expect(authoringApi.listFindings).not.toHaveBeenCalled()
  })
})
