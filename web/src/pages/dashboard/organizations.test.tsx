// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const state = vi.hoisted(() => ({
  owner: false,
  canManageMembers: false,
  canViewMembers: true,
  canViewLoginConnections: false,
  viewerId: 'viewer-a',
  routeViewerId: 'viewer-a',
  organizationId: 'org-a',
  membersError: null as Error | null,
  mutate: vi.fn(),
}))

vi.mock('react-i18next', async () => ({
  ...await vi.importActual<typeof import('react-i18next')>('react-i18next'),
  useTranslation: () => ({ t: (key: string) => key }),
}))
vi.mock('@tanstack/react-router', () => ({
  useParams: () => ({ organizationId: state.organizationId }),
  useRouteContext: () => ({ user: { userId: state.routeViewerId } }),
  Link: ({ children, to, 'aria-label': label }: { children: React.ReactNode; to: string; 'aria-label'?: string }) =>
    <a href={to} aria-label={label}>{children}</a>,
}))
vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ user: { userId: state.viewerId } }),
}))
vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
  useMutation: () => ({ isPending: false, mutate: state.mutate, mutateAsync: vi.fn(), reset: vi.fn() }),
  useQuery: ({ queryKey }: { queryKey: Array<string | number> }) => {
    if (queryKey[2] === 'list') {
      return { data: { items: [{ id: 'org-a', slug: 'team-a', displayName: 'Team A',
        permissions: { canViewMembers: state.owner, canViewLoginConnections: state.canViewLoginConnections } }], total: 1 } }
    }
    if (queryKey.length === 4) {
      return { data: {
        id: 'org-a', displayName: 'Team A', slug: 'team-a',
        roles: state.owner ? ['ORG_OWNER'] : ['ORG_AUDITOR'],
        permissions: {
          canViewMembers: state.canViewMembers,
          canManageMembers: state.canManageMembers,
          canViewRoles: state.owner || !state.canManageMembers,
        },
      } }
    }
    return { data: { items: [{
      id: 'membership-a', userId: 'user-a', displayName: 'Alice', status: 'ACTIVE',
      roles: [{ id: 'binding-a', role: 'ORG_AUDITOR' }],
    }], total: 1 }, error: state.membersError }
  },
}))

import { OrganizationMembersPage, OrganizationsPage } from './organizations'

afterEach(cleanup)

describe('OrganizationMembersPage permissions', () => {
  beforeEach(() => {
    state.owner = false
    state.canManageMembers = false
    state.canViewMembers = true
    state.canViewLoginConnections = false
    state.viewerId = 'viewer-a'
    state.routeViewerId = 'viewer-a'
    state.organizationId = 'org-a'
    state.membersError = null
    state.mutate.mockClear()
  })

  it('keeps member and role actions hidden from a read-only member', () => {
    render(<OrganizationMembersPage />)
    expect(screen.getByText('Alice')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'organizations.addMember' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'organizations.grantMemberRole' })).toBeNull()
  })

  it('keeps the management route out of a member-only organization card', () => {
    const { rerender } = render(<OrganizationsPage />)
    expect(screen.queryByRole('link', { name: 'organizations.viewOrganizationMembers' })).toBeNull()
    state.owner = true
    rerender(<OrganizationsPage />)
    expect(screen.getByRole('link', { name: 'organizations.viewOrganizationMembers' }).getAttribute('href'))
      .toBe('/dashboard/organizations/$organizationId')
  })

  it('makes the organization detail reachable for a connection-only administrator', () => {
    state.canViewLoginConnections = true
    render(<OrganizationsPage />)
    expect(screen.getByRole('link', { name: 'organizationConnections.viewOrganization' }).getAttribute('href'))
      .toBe('/dashboard/organizations/$organizationId')
  })

  it('lets a member administrator add members but not grant roles', () => {
    state.canManageMembers = true
    render(<OrganizationMembersPage />)
    expect(screen.queryByRole('button', { name: 'organizations.grantMemberRole' })).toBeNull()
    expect(screen.queryByText('organizations.roles.ORG_AUDITOR')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'organizations.addMember' }))
    const dialog = within(screen.getByRole('dialog', { name: 'organizations.addMember' }))
    expect(dialog.getByLabelText('organizations.userId')).toBeTruthy()
    expect(dialog.getByText('organizations.addMemberHint')).toBeTruthy()
  })

  it('lets the owner choose a role with the shared keyboard-accessible picker', async () => {
    state.owner = true
    state.canManageMembers = true
    const restoreScroll = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollIntoView')
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: vi.fn() })
    render(<OrganizationMembersPage />)
    try {
      fireEvent.click(screen.getByRole('button', { name: 'organizations.grantMemberRole' }))
      const dialog = within(screen.getByRole('dialog', { name: 'organizations.grantRole' }))
      const submit = dialog.getByRole<HTMLButtonElement>('button', { name: 'organizations.grantRole' })
      const trigger = dialog.getByRole('combobox', { name: 'organizations.selectRole' })
      expect(trigger.tagName).toBe('BUTTON')
      expect(submit.disabled).toBe(true)
      fireEvent.keyDown(trigger, { key: 'ArrowDown' })
      const picker = within(await screen.findByRole('listbox'))
      expect(picker.queryByRole('option', { name: 'organizations.roles.ORG_AUDITOR' })).toBeNull()
      fireEvent.keyDown(picker.getByRole('option', { name: 'organizations.roles.LOGIN_SECRET_ADMIN' }), { key: 'Enter' })
      await waitFor(() => expect(document.activeElement).toBe(trigger))
      expect(submit.disabled).toBe(false)
      fireEvent.click(submit)
      expect(state.mutate).toHaveBeenCalledExactlyOnceWith({ targetUserId: 'user-a', role: 'LOGIN_SECRET_ADMIN' })
    } finally {
      if (restoreScroll) Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', restoreScroll)
      else delete (HTMLElement.prototype as Partial<HTMLElement>).scrollIntoView
    }
  })

  it.each(['viewerId', 'organizationId'] as const)('discards the grant dialog when %s changes', (key) => {
    state.owner = true
    const { rerender } = render(<OrganizationMembersPage />)
    fireEvent.click(screen.getByRole('button', { name: 'organizations.grantMemberRole' }))
    expect(screen.getByRole('dialog')).toBeTruthy()
    state[key] = 'new-scope'
    if (key === 'viewerId') state.routeViewerId = 'new-scope'
    rerender(<OrganizationMembersPage />)
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(state.mutate).not.toHaveBeenCalled()
  })

  it('drops previously rendered member details when refresh fails', () => {
    const { rerender } = render(<OrganizationMembersPage />)
    expect(screen.getByText('Alice')).toBeTruthy()
    state.membersError = new Error('Forbidden')
    rerender(<OrganizationMembersPage />)
    expect(screen.getByRole('alert').textContent).toBe('Forbidden')
    expect(screen.queryByText('Alice')).toBeNull()
  })

  it('does not render the previous session while the router has a newer signed-in user', () => {
    state.routeViewerId = 'viewer-b'
    render(<OrganizationMembersPage />)
    expect(screen.queryByText('Alice')).toBeNull()
  })
})
