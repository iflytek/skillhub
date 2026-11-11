/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const state = vi.hoisted(() => ({ hasRole: vi.fn(), mutateAsync: vi.fn(), pending: false, success: vi.fn(), error: vi.fn() }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ hasRole: state.hasRole }) }))
vi.mock('./use-governance', () => ({ useRebuildSearchIndex: () => ({ mutateAsync: state.mutateAsync, isPending: state.pending }) }))
vi.mock('@/shared/lib/toast', () => ({ toast: { success: state.success, error: state.error } }))

import { SearchIndexMaintenance } from './search-index-maintenance'

const action = 'governance.searchRebuildAction'
function openConfirmation() {
  fireEvent.click(screen.getByRole('button', { name: action }))
  return screen.getByRole('dialog', { name: 'governance.searchRebuildConfirmTitle' })
}

describe('SearchIndexMaintenance', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    state.hasRole.mockReturnValue(true)
    state.mutateAsync.mockResolvedValue(undefined)
    state.pending = false
  })
  afterEach(cleanup)

  it('hides the maintenance action for non-super administrators', () => {
    state.hasRole.mockReturnValue(false)
    render(<SearchIndexMaintenance />)
    expect(state.hasRole).toHaveBeenCalledWith('SUPER_ADMIN')
    expect(screen.queryByRole('button')).toBeNull()
    expect(state.mutateAsync).not.toHaveBeenCalled()
  })

  it('requires explicit confirmation and allows cancellation without rebuilding', () => {
    render(<SearchIndexMaintenance />)
    const dialog = openConfirmation()
    expect(within(dialog).getByText('governance.searchRebuildConfirmDescription')).toBeTruthy()
    expect(state.mutateAsync).not.toHaveBeenCalled()
    fireEvent.click(within(dialog).getByRole('button', { name: 'dialog.cancel' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(state.mutateAsync).not.toHaveBeenCalled()
  })

  it('calls rebuilding once after confirmation and closes on success', async () => {
    render(<SearchIndexMaintenance />)
    fireEvent.click(within(openConfirmation()).getByRole('button', { name: action }))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(state.mutateAsync).toHaveBeenCalledTimes(1)
    expect(state.success).toHaveBeenCalledWith('governance.searchRebuildSuccessTitle', 'governance.searchRebuildSuccessDescription')
    expect(state.error).not.toHaveBeenCalled()
  })

  it('retains the confirmation after failure and allows an explicit retry', async () => {
    state.mutateAsync.mockRejectedValueOnce(new Error('Search service unavailable'))
    render(<SearchIndexMaintenance />)
    fireEvent.click(within(openConfirmation()).getByRole('button', { name: action }))
    await waitFor(() => expect(state.error).toHaveBeenCalledWith('governance.searchRebuildErrorTitle', 'Search service unavailable'))
    const dialog = screen.getByRole('dialog')
    await waitFor(() => expect(within(dialog).getByRole('button', { name: action }).hasAttribute('disabled')).toBe(false))
    expect(state.success).not.toHaveBeenCalled()
    fireEvent.click(within(dialog).getByRole('button', { name: action }))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(state.mutateAsync).toHaveBeenCalledTimes(2)
  })

  it('prevents duplicate confirmation and cancellation while the operation is running', async () => {
    let finish: () => void = () => undefined
    state.mutateAsync.mockImplementationOnce(() => new Promise<void>((resolve) => { finish = resolve }))
    render(<SearchIndexMaintenance />)
    const dialog = openConfirmation()
    const confirm = within(dialog).getByRole('button', { name: action })
    fireEvent.click(confirm)
    fireEvent.click(confirm)
    expect(state.mutateAsync).toHaveBeenCalledTimes(1)
    expect(within(dialog).getByRole('button', { name: 'dialog.processing' }).hasAttribute('disabled')).toBe(true)
    expect(within(dialog).getByRole('button', { name: 'dialog.cancel' }).hasAttribute('disabled')).toBe(true)
    await act(async () => finish())
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  it('does not open a second operation while the mutation reports pending', () => {
    state.pending = true
    render(<SearchIndexMaintenance />)
    const button = screen.getByRole('button', { name: 'governance.searchRebuildRunning' })
    expect(button.hasAttribute('disabled')).toBe(true)
    fireEvent.click(button)
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(state.mutateAsync).not.toHaveBeenCalled()
  })
})
