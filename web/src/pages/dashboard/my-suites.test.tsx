/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { MySkillSuiteWorkspace } from '@/api/types'
import { MySuitesPage } from './my-suites'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(), hook: vi.fn(),
  data: { items: [], total: 0, page: 0, size: 12, attentionCount: 0, hasChangingOperations: false } as MySkillSuiteWorkspace,
}))
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => mocks.navigate }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'zh-CN' } }) }))
vi.mock('@/shared/hooks/use-suite-queries', () => ({ useMySuiteWorkspace: (...args: unknown[]) => {
  mocks.hook(...args)
  return { data: mocks.data, isLoading: false, isError: false, isFetching: false, isPlaceholderData: false }
} }))

function row(state = 'DRAFT') {
  return { suiteId: 7, namespace: 'global', slug: 'care-suite', displayName: 'Care', version: '1.1.0',
    suiteVersion: '1.0.0', state, updatedAt: '2026-09-15T04:00:00Z' }
}
describe('MySuites owner workbench', () => {
  afterEach(() => { cleanup(); vi.clearAllMocks(); mocks.data = { items: [], total: 0, page: 0, size: 12, attentionCount: 0, hasChangingOperations: false } })
  it('uses one paginated workbench without a separate publishing tab or task cards', () => {
    mocks.data.items = [row()]; mocks.data.total = 25; mocks.data.attentionCount = 3
    render(<MySuitesPage />)
    expect(screen.queryAllByRole('tab')).toHaveLength(0)
    expect(screen.getByRole('button', { name: 'suite.workspace.attention 3' })).not.toBeNull()
    expect(mocks.hook).toHaveBeenCalledWith('', 'ALL', 0, 12)
    fireEvent.click(screen.getByRole('button', { name: 'suite.view' }))
    expect(mocks.navigate).toHaveBeenCalledWith({ to: '/dashboard/suites/global/care-suite', search: { version: '1.0.0' } })
  })
  it('opens the Suite workbench for a Suite under review', () => {
    mocks.data.items = [row('PENDING_REVIEW')]; mocks.data.total = 1
    render(<MySuitesPage />)
    fireEvent.click(screen.getByRole('button', { name: 'suite.view' }))
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/global/care-suite',
      search: { version: '1.0.0' },
    })
  })
  it('opens the Suite publishing tab when an existing Suite has a publishing task', () => {
    mocks.data.items = [{ ...row('ATTENTION'), operationId: 'op-1', operationStatus: 'BLOCKED_RETRYABLE', failureCode: 'MEMBER_EXECUTION_FAILED' }]
    mocks.data.total = 1
    render(<MySuitesPage />)
    fireEvent.click(screen.getByRole('button', { name: 'suite.view' }))
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/global/care-suite',
      search: { version: '1.0.0', tab: 'publishing' },
    })
  })
  it('keeps a temporary blocked creation discoverable with a specific action and no raw error code', () => {
    mocks.data.items = [{ ...row('ATTENTION'), suiteId: undefined, suiteVersion: undefined,
      operationId: 'op-1', operationStatus: 'BLOCKED_RETRYABLE', failureCode: 'MEMBER_EXECUTION_FAILED' }]
    mocks.data.total = 1
    render(<MySuitesPage />)
    expect(screen.queryByText('MEMBER_EXECUTION_FAILED')).toBeNull()
    expect(screen.getByText('suite.bundle.statusLabel.BLOCKED_RETRYABLE')).not.toBeNull()
    expect(screen.getByText('suite.bundle.problem.memberExecutionFailed.title')).not.toBeNull()
    expect(screen.getByText('suite.bundle.taskHint.BLOCKED_RETRYABLE')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.taskAction.BLOCKED_RETRYABLE' }))
    expect(mocks.navigate).toHaveBeenCalledWith({ to: '/dashboard/suites/publishing/op-1' })
  })
  it('flags a completed creation task without a generated Suite draft as a problem', () => {
    mocks.data.items = [{ ...row('ATTENTION'), suiteId: undefined, suiteVersion: undefined,
      operationId: 'op-missing', operationStatus: 'SUITE_DRAFT_CREATED' }]
    mocks.data.total = 1
    render(<MySuitesPage />)
    expect(screen.getByText('suite.bundle.problem.draftMissing.title')).not.toBeNull()
    expect(screen.getByText('suite.bundle.taskHint.SUITE_DRAFT_CREATED')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.taskAction.SUITE_DRAFT_CREATED' }))
    expect(mocks.navigate).toHaveBeenCalledWith({ to: '/dashboard/suites/publishing/op-missing' })
  })
  it('only applies submitted search and resets pagination in the same transition', async () => {
    mocks.data.items = [row()]; mocks.data.total = 25; mocks.data.attentionCount = 3
    render(<MySuitesPage />)
    fireEvent.click(screen.getByRole('button', { name: 'pagination.next' }))
    await waitFor(() => expect(mocks.hook).toHaveBeenLastCalledWith('', 'ALL', 1, 12))
    mocks.hook.mockClear()
    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: 'c' } }); fireEvent.change(input, { target: { value: 'care' } })
    expect(mocks.hook.mock.calls.every(args => args[0] === '')).toBe(true)
    expect(mocks.hook).toHaveBeenLastCalledWith('', 'ALL', 1, 12)
    fireEvent.submit(input.closest('form')!)
    await waitFor(() => expect(mocks.hook).toHaveBeenLastCalledWith('care', 'ALL', 0, 12))
    expect(mocks.hook.mock.calls.some(args => args[0] === 'c')).toBe(false)
    expect(mocks.hook.mock.calls.some(args => args[0] === 'care' && args[2] === 1)).toBe(false)
    fireEvent.click(screen.getByRole('button', { name: 'suite.workspace.clearSearch' }))
    expect(mocks.hook).toHaveBeenLastCalledWith('care', 'ALL', 0, 12)
    fireEvent.submit(input.closest('form')!)
    await waitFor(() => expect(mocks.hook).toHaveBeenLastCalledWith('', 'ALL', 0, 12))
    fireEvent.click(screen.getByRole('button', { name: 'suite.workspace.attention 3' }))
    await waitFor(() => expect(mocks.hook).toHaveBeenLastCalledWith('', 'ATTENTION', 0, 12))
  })
})
