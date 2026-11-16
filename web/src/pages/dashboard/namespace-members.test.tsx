/** @vitest-environment jsdom */
import { Children, type ReactElement, type ReactNode, type Ref } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'

const state = vi.hoisted(() => ({
  role: 'OWNER',
  status: 'ACTIVE',
  type: 'TEAM',
  granted: false,
  update: vi.fn(),
  remove: vi.fn(),
  autoPublication: vi.fn(),
}))
vi.mock('@tanstack/react-router', () => ({ useParams: () => ({ slug: 'team' }) }))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string>) => (values?.userId ? `${key}:${values.userId}` : key),
    i18n: { language: 'en' },
  }),
}))
vi.mock('@/features/namespace/namespace-header', () => ({ NamespaceHeader: () => <h1>Team</h1> }))
vi.mock('@/features/namespace/namespace-management-nav', () => ({ NamespaceManagementBack: () => <a href="/dashboard/namespaces">Back</a>, NamespaceManagementNav: () => <nav>Navigation</nav> }))
vi.mock('@/features/namespace/invitation-controls', () => ({ InvitationControls: () => <span>Active invitation controls</span> }))
vi.mock('@/features/namespace/invitation-history', () => ({ InvitationHistory: () => <span>Read-only invitation history</span> }))
vi.mock('@/features/namespace/add-namespace-member-dialog', () => ({ AddNamespaceMemberDialog: ({ children }: { children: ReactNode }) => children }))
vi.mock('@/features/namespace/batch-import-members-dialog', () => ({ BatchImportMembersDialog: ({ children }: { children: ReactNode }) => children }))
vi.mock('@/features/namespace/transfer-ownership-dialog', () => ({ TransferOwnershipDialog: ({ children }: { children: ReactNode }) => children }))
vi.mock('@/shared/components/confirm-dialog', () => ({
  ConfirmDialog: ({ open, title, description, confirmText, onConfirm }: {
    open: boolean
    title: string
    description?: ReactNode
    confirmText?: string
    onConfirm: () => Promise<void>
  }) => (open ? (
    <div role="alertdialog" aria-label={title}>
      <p>{description}</p>
      <button type="button" onClick={() => void onConfirm().catch(() => undefined)}>{confirmText}</button>
    </div>
  ) : null),
}))
vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
vi.mock('@/shared/ui/select', () => ({
  Select: ({ children, value, onValueChange, disabled }: { children: ReactNode; value: string; onValueChange: (value: string) => void; disabled?: boolean }) => {
    const trigger = Children.toArray(children)[0] as ReactElement<{ ref?: Ref<HTMLSelectElement> }>
    return <select ref={trigger.props.ref} aria-label="Role" value={value} disabled={disabled} onChange={(event) => onValueChange(event.target.value)}>{children}</select>
  },
  SelectTrigger: () => null,
  SelectValue: () => null,
  SelectContent: ({ children }: { children: ReactNode }) => children,
  SelectItem: ({ children, value }: { children: ReactNode; value: string }) => <option value={value}>{children}</option>,
}))
vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useMyNamespaces: () => ({ data: [{ slug: 'team', currentUserRole: state.role }] }),
  useNamespaceDetail: () => ({ data: { slug: 'team', type: state.type, status: state.status }, isLoading: false }),
  useNamespaceMembers: () => ({ data: { items: [
    { id: 1, userId: 'owner', displayName: 'Owner', role: 'OWNER', createdAt: '2026-10-13T00:00:00Z' },
    {
      id: 2,
      userId: 'member',
      displayName: 'Member',
      role: 'MEMBER',
      teamAutoPublicationGranted: state.granted,
      createdAt: '2026-10-13T00:00:00Z',
    },
  ], total: 2 }, isLoading: false }),
  useUpdateNamespaceMemberRole: () => ({ mutateAsync: state.update }),
  useRemoveNamespaceMember: () => ({ mutateAsync: state.remove }),
  useSetNamespaceMemberTeamAutoPublication: () => ({ mutateAsync: state.autoPublication }),
}))
import { NamespaceMembersPage } from './namespace-members'

afterEach(cleanup)
beforeEach(() => {
  state.role = 'OWNER'
  state.status = 'ACTIVE'
  state.type = 'TEAM'
  state.granted = false
  state.update.mockReset().mockResolvedValue({})
  state.remove.mockReset()
  state.autoPublication.mockReset().mockResolvedValue({})
})

describe('NamespaceMembersPage permissions and role actions', () => {
  it.each(['FROZEN', 'ARCHIVED'])('allows OWNER to view history without creating links in %s space', async (status) => {
    state.status = status
    render(<NamespaceMembersPage />)
    fireEvent.click(screen.getByRole('button', { name: 'invitation.historyTitle' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Read-only invitation history')).toBeTruthy()
    expect(within(dialog).queryByText('Active invitation controls')).toBeNull()
    expect(screen.queryByRole('button', { name: 'namespaceManagement.invite' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'members.addMember' })).toBeNull()
  })
  it('does not offer invitation history to an ordinary member', () => {
    state.role = 'MEMBER'
    render(<NamespaceMembersPage />)
    expect(screen.queryByRole('button', { name: 'invitation.historyTitle' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'namespaceManagement.invite' })).toBeNull()
  })
  it('places back navigation before the namespace header', () => {
    const { container } = render(<NamespaceMembersPage />)
    expect(container.firstElementChild?.firstElementChild?.tagName).toBe('A')
  })
  it.each(['MEMBER', 'FROZEN', 'ARCHIVED'])('hides management buttons for %s', (mode) => {
    if (mode === 'MEMBER') state.role = mode
    else state.status = mode
    render(<NamespaceMembersPage />)
    expect(screen.queryByRole('button', { name: 'members.addMember' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'members.remove' })).toBeNull()
    expect(screen.queryByRole('columnheader', { name: 'members.colActions' })).toBeNull()
  })
  it('never shows a removal button for the owner and places icon actions in the action column', async () => {
    render(<NamespaceMembersPage />)
    const ownerRow = screen.getByText('Owner').closest('tr')!
    expect(within(ownerRow).queryByRole('button')).toBeNull()
    const row = screen.getByText('Member').closest('tr')!
    fireEvent.change(within(row).getByRole('combobox'), { target: { value: 'ADMIN' } })
    const save = within(row).getByRole('button', { name: 'members.saveRole' })
    expect(save.closest('td')).toBe(row.lastElementChild)
    expect(save.getAttribute('title')).toBe('members.saveRole')
    expect(save.querySelector('svg')).toBeTruthy()
    save.focus()
    fireEvent.click(save)
    await waitFor(() => expect(state.update).toHaveBeenCalledWith({ slug: 'team', userId: 'member', role: 'ADMIN' }))
    await waitFor(() => expect(document.activeElement).toBe(within(row).getByRole('combobox')))
  })
  it('preserves the selected role and save action when an async update fails', async () => {
    state.update.mockRejectedValueOnce(new Error('unavailable'))
    render(<NamespaceMembersPage />)
    const role = screen.getByRole('combobox')
    fireEvent.change(role, { target: { value: 'ADMIN' } })
    fireEvent.click(screen.getByRole('button', { name: 'members.saveRole' }))
    await waitFor(() => expect((role as HTMLSelectElement).disabled).toBe(false))
    expect((role as HTMLSelectElement).value).toBe('ADMIN')
    expect(screen.getByRole('button', { name: 'members.saveRole' })).toBeTruthy()
    expect(state.update).toHaveBeenCalledTimes(1)
  })
  it('cancel clears the role draft without sending an update', () => {
    render(<NamespaceMembersPage />)
    const role = screen.getByRole('combobox')
    fireEvent.change(role, { target: { value: 'ADMIN' } })
    const cancel = screen.getByRole('button', { name: 'members.cancelRole' })
    cancel.focus()
    fireEvent.click(cancel)
    expect(document.activeElement).toBe(role)
    expect((role as HTMLSelectElement).value).toBe('MEMBER')
    expect(state.update).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'members.saveRole' })).toBeNull()
  })
})

describe('NamespaceMembersPage team-only automatic publication', () => {
  it('shows status, grant action and the token and browser scope explanation to managers', async () => {
    render(<NamespaceMembersPage />)
    expect(screen.getByRole('columnheader', { name: 'members.autoPublication.column' })).toBeTruthy()
    expect(screen.getByText('members.autoPublication.scopeHint')).toBeTruthy()
    const ownerRow = screen.getByText('Owner').closest('tr')!
    expect(within(ownerRow).getByText('members.autoPublication.notApplicable')).toBeTruthy()
    const row = screen.getByText('Member').closest('tr')!
    expect(within(row).getByText('members.autoPublication.notGranted')).toBeTruthy()
    fireEvent.click(within(row).getByRole('button', { name: 'members.autoPublication.grant' }))
    const dialog = await screen.findByRole('alertdialog', { name: 'members.autoPublication.grantConfirmTitle' })
    expect(within(dialog).getByText('members.autoPublication.grantConfirmDescription:member')).toBeTruthy()
    expect(state.autoPublication).not.toHaveBeenCalled()
    fireEvent.click(within(dialog).getByRole('button', { name: 'members.autoPublication.grant' }))
    await waitFor(() => expect(state.autoPublication).toHaveBeenCalledWith({ slug: 'team', userId: 'member', granted: true }))
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
  })

  it('offers revocation with fallback explanation for a granted member', async () => {
    state.granted = true
    render(<NamespaceMembersPage />)
    const row = screen.getByText('Member').closest('tr')!
    expect(within(row).getByText('members.autoPublication.granted')).toBeTruthy()
    fireEvent.click(within(row).getByRole('button', { name: 'members.autoPublication.revoke' }))
    const dialog = await screen.findByRole('alertdialog', { name: 'members.autoPublication.revokeConfirmTitle' })
    expect(within(dialog).getByText('members.autoPublication.revokeConfirmDescription:member')).toBeTruthy()
    fireEvent.click(within(dialog).getByRole('button', { name: 'members.autoPublication.revoke' }))
    await waitFor(() => expect(state.autoPublication).toHaveBeenCalledWith({ slug: 'team', userId: 'member', granted: false }))
  })

  it('keeps the dialog open when the change fails', async () => {
    state.autoPublication.mockRejectedValueOnce(new Error('forbidden'))
    render(<NamespaceMembersPage />)
    const row = screen.getByText('Member').closest('tr')!
    fireEvent.click(within(row).getByRole('button', { name: 'members.autoPublication.grant' }))
    const dialog = await screen.findByRole('alertdialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'members.autoPublication.grant' }))
    await waitFor(() => expect(state.autoPublication).toHaveBeenCalledTimes(1))
    expect(screen.getByRole('alertdialog')).toBeTruthy()
  })

  it('shows status without actions to ordinary members and read-only namespaces', () => {
    for (const mode of ['MEMBER', 'FROZEN']) {
      state.role = mode === 'MEMBER' ? 'MEMBER' : 'OWNER'
      state.status = mode === 'FROZEN' ? 'FROZEN' : 'ACTIVE'
      render(<NamespaceMembersPage />)
      expect(screen.getByText('members.autoPublication.notGranted')).toBeTruthy()
      expect(screen.queryByRole('button', { name: 'members.autoPublication.grant' })).toBeNull()
      cleanup()
    }
  })

  it('hides the column outside TEAM namespaces', () => {
    state.type = 'GLOBAL'
    render(<NamespaceMembersPage />)
    expect(screen.queryByRole('columnheader', { name: 'members.autoPublication.column' })).toBeNull()
    expect(screen.queryByText('members.autoPublication.scopeHint')).toBeNull()
  })
})
