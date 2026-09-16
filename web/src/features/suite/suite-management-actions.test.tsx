/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SkillSuite } from '@/api/types'
import { SuiteManagementActions } from './suite-management-actions'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  reopen: { mutateAsync: vi.fn(), isPending: false },
  yank: { mutateAsync: vi.fn(), isPending: false },
  hidden: { mutateAsync: vi.fn(), isPending: false },
  archived: { mutateAsync: vi.fn(), isPending: false },
  remove: { mutateAsync: vi.fn(), isPending: false },
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@tanstack/react-router', () => ({ useNavigate: () => mocks.navigate }))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))
vi.mock('@/shared/hooks/use-suite-queries', () => ({
  useReopenSuiteVersion: () => mocks.reopen,
  useYankSuiteVersion: () => mocks.yank,
  useSetSuiteHidden: () => mocks.hidden,
  useSetSuiteArchived: () => mocks.archived,
  useDeleteSuite: () => mocks.remove,
}))
vi.mock('@/shared/lib/toast', () => ({ toast: mocks.toast }))
vi.mock('@/shared/components/confirm-dialog', () => ({
  ConfirmDialog: ({ open, confirmText, onConfirm }: {
    open: boolean
    confirmText?: string
    onConfirm: () => void | Promise<void>
  }) => open ? <button onClick={onConfirm}>confirm:{confirmText}</button> : null,
}))
vi.mock('@/shared/ui/dialog', () => ({
  Dialog: ({ open, children }: { open: boolean; children?: ReactNode }) => open ? <>{children}</> : null,
  DialogContent: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  DialogDescription: ({ children }: { children?: ReactNode }) => <p>{children}</p>,
  DialogFooter: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  DialogHeader: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  DialogTitle: ({ children }: { children?: ReactNode }) => <h2>{children}</h2>,
}))

function suite(overrides: Partial<SkillSuite> = {}): SkillSuite {
  return {
    id: 7,
    versionId: 70,
    namespace: 'global',
    slug: 'starter',
    displayName: 'Starter suite',
    version: '1.0.0',
    status: 'PUBLISHED',
    visibility: 'PUBLIC',
    suiteStatus: 'ACTIVE',
    hidden: false,
    allowedActions: [],
    available: true,
    members: [],
    ...overrides,
  }
}

describe('SuiteManagementActions', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('does not expose management controls to a public reader', () => {
    const { container } = render(<SuiteManagementActions suite={suite()} />)

    expect(container.innerHTML).toBe('')
  })

  it('does not render an empty management card for draft-only actions', () => {
    const { container } = render(<SuiteManagementActions suite={suite({
      status: 'DRAFT',
      allowedActions: ['EDIT', 'SUBMIT'],
    })} />)

    expect(container.innerHTML).toBe('')
  })

  it('shows only rejected-version recovery to its author', () => {
    render(<SuiteManagementActions suite={suite({ status: 'REJECTED', allowedActions: ['REOPEN'] })} />)

    expect(screen.getByRole('button', { name: 'suite.reopenDraft' })).not.toBeNull()
    expect(screen.queryByRole('button', { name: 'suite.delete' })).toBeNull()
  })

  it('reopens a rejected version before navigating to its editor', async () => {
    mocks.reopen.mutateAsync.mockResolvedValue(undefined)
    render(<SuiteManagementActions suite={suite({ status: 'REJECTED', allowedActions: ['REOPEN'] })} />)

    fireEvent.click(screen.getByRole('button', { name: 'suite.reopenDraft' }))

    await waitFor(() => expect(mocks.reopen.mutateAsync).toHaveBeenCalledWith({ suiteId: 7, versionId: 70 }))
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/global/starter/edit',
      search: { version: '1.0.0' },
    })
  })

  it('offers a new immutable version and governance actions to an administrator', () => {
    render(<SuiteManagementActions suite={suite({
      allowedActions: ['CREATE_VERSION', 'YANK', 'HIDE', 'ARCHIVE', 'DELETE'],
    })} />)

    expect(screen.getByRole('button', { name: 'suite.createVersion' })).not.toBeNull()
    expect(screen.getByRole('button', { name: 'suite.yankVersion' })).not.toBeNull()
    expect(screen.getByRole('button', { name: 'suite.hide' })).not.toBeNull()
    expect(screen.getByRole('button', { name: 'suite.archive' })).not.toBeNull()
    expect(screen.getByRole('button', { name: 'suite.delete' })).not.toBeNull()
  })

  it('prefills a new version from the selected immutable version', () => {
    render(<SuiteManagementActions suite={suite({ allowedActions: ['CREATE_VERSION'] })} />)

    fireEvent.click(screen.getByRole('button', { name: 'suite.createVersion' }))

    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/global/starter/new-version',
      search: { sourceVersion: '1.0.0' },
    })
  })

  it('requires a reason and yanks the exact version', async () => {
    mocks.yank.mutateAsync.mockResolvedValue(undefined)
    render(<SuiteManagementActions suite={suite({ allowedActions: ['YANK'] })} />)

    fireEvent.click(screen.getByRole('button', { name: 'suite.yankVersion' }))
    const confirm = screen.getAllByRole('button', { name: 'suite.yankVersion' })[1]
    expect(confirm.hasAttribute('disabled')).toBe(true)

    fireEvent.change(screen.getByPlaceholderText('suite.yankReasonPlaceholder'), {
      target: { value: 'superseded' },
    })
    fireEvent.click(confirm)

    await waitFor(() => expect(mocks.yank.mutateAsync).toHaveBeenCalledWith({
      suiteId: 7,
      versionId: 70,
      reason: 'superseded',
    }))
    expect(mocks.toast.success).toHaveBeenCalledWith('suite.yanked')
  })

  it('confirms hiding without inferring the opposite action', async () => {
    mocks.hidden.mutateAsync.mockResolvedValue(undefined)
    render(<SuiteManagementActions suite={suite({ allowedActions: ['HIDE'] })} />)

    fireEvent.click(screen.getByRole('button', { name: 'suite.hide' }))
    fireEvent.click(screen.getByRole('button', { name: 'confirm:suite.hide' }))

    await waitFor(() => expect(mocks.hidden.mutateAsync).toHaveBeenCalledWith({ suiteId: 7, hidden: true }))
    expect(screen.queryByRole('button', { name: 'suite.restore' })).toBeNull()
  })

  it('confirms restoring a hidden Suite', async () => {
    mocks.hidden.mutateAsync.mockResolvedValue(undefined)
    render(<SuiteManagementActions suite={suite({ hidden: true, allowedActions: ['RESTORE'] })} />)

    fireEvent.click(screen.getByRole('button', { name: 'suite.restore' }))
    fireEvent.click(screen.getByRole('button', { name: 'confirm:suite.restore' }))

    await waitFor(() => expect(mocks.hidden.mutateAsync).toHaveBeenCalledWith({ suiteId: 7, hidden: false }))
  })

  it('confirms archive and unarchive with the server-provided action', async () => {
    mocks.archived.mutateAsync.mockResolvedValue(undefined)
    const first = render(<SuiteManagementActions suite={suite({ allowedActions: ['ARCHIVE'] })} />)

    fireEvent.click(screen.getByRole('button', { name: 'suite.archive' }))
    fireEvent.click(screen.getByRole('button', { name: 'confirm:suite.archive' }))
    await waitFor(() => expect(mocks.archived.mutateAsync).toHaveBeenCalledWith({ suiteId: 7, archived: true }))

    first.unmount()
    render(<SuiteManagementActions suite={suite({
      suiteStatus: 'ARCHIVED',
      allowedActions: ['UNARCHIVE'],
    })} />)
    fireEvent.click(screen.getByRole('button', { name: 'suite.unarchive' }))
    fireEvent.click(screen.getByRole('button', { name: 'confirm:suite.unarchive' }))
    await waitFor(() => expect(mocks.archived.mutateAsync).toHaveBeenCalledWith({ suiteId: 7, archived: false }))
  })

  it('deletes only after confirmation and returns to the Suite dashboard', async () => {
    mocks.remove.mutateAsync.mockResolvedValue(undefined)
    render(<SuiteManagementActions suite={suite({ allowedActions: ['DELETE'] })} />)

    fireEvent.click(screen.getByRole('button', { name: 'suite.delete' }))
    fireEvent.click(screen.getByRole('button', { name: 'confirm:suite.delete' }))

    await waitFor(() => expect(mocks.remove.mutateAsync).toHaveBeenCalledWith({ suiteId: 7 }))
    expect(mocks.navigate).toHaveBeenCalledWith({ to: '/dashboard/suites' })
  })

  it('shows an operation error without navigating', async () => {
    mocks.reopen.mutateAsync.mockRejectedValue(new Error('forbidden'))
    render(<SuiteManagementActions suite={suite({ allowedActions: ['REOPEN'] })} />)

    fireEvent.click(screen.getByRole('button', { name: 'suite.reopenDraft' }))

    await waitFor(() => expect(mocks.toast.error).toHaveBeenCalledWith('suite.actionFailed', 'forbidden'))
    expect(mocks.navigate).not.toHaveBeenCalled()
  })
})
