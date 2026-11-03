/** @vitest-environment jsdom */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { createInstance } from 'i18next'
import { I18nextProvider } from 'react-i18next'
import en from '@/i18n/locales/en.json'

const api = vi.hoisted(() => ({ listMembers: vi.fn(), getNamespaceMembers: vi.fn(), transferOwnership: vi.fn() }))
vi.mock('@/api/client', () => ({
  namespaceApi: { listMembers: api.listMembers },
  adminApi: { getNamespaceMembers: api.getNamespaceMembers },
  skillLifecycleApi: { transferOwnership: api.transferOwnership },
}))
vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn() } }))
vi.mock('@/shared/ui/select', () => ({
  Select: ({ value, onValueChange, children, disabled }: { value: string; onValueChange: (value: string) => void; children: ReactNode; disabled?: boolean }) =>
    <select aria-label="New owner" value={value} onChange={(event) => onValueChange(event.target.value)} disabled={disabled}>{children}</select>,
  SelectTrigger: () => null,
  SelectValue: () => null,
  SelectContent: ({ children }: { children: ReactNode }) => <><option value="" />{children}</>,
  SelectItem: ({ value, children }: { value: string; children: ReactNode }) => <option value={value}>{children}</option>,
}))
import { SkillOwnerDialog } from './skill-owner-dialog'

async function mount(asPlatformAdmin = false) {
  const i18n = createInstance()
  await i18n.init({ lng: 'en', resources: { en: { translation: en } }, interpolation: { escapeValue: false } })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<I18nextProvider i18n={i18n}><QueryClientProvider client={client}>
    <SkillOwnerDialog namespace="team" slug="foo" ownerId="leaver" asPlatformAdmin={asPlatformAdmin} />
  </QueryClientProvider></I18nextProvider>)
  fireEvent.click(screen.getByRole('button', { name: 'Change owner' }))
}

describe('SkillOwnerDialog', () => {
  afterEach(() => { cleanup(); vi.clearAllMocks() })

  it('offers other active people only and sends both owners with a trimmed reason', async () => {
    api.listMembers.mockResolvedValue({ items: [
      { id: 1, userId: 'leaver', displayName: 'Jane Doe', role: 'MEMBER', createdAt: '', active: true },
      { id: 2, userId: 'successor', displayName: 'Sam Successor', role: 'MEMBER', createdAt: '', active: true },
      { id: 3, userId: 'disabled', displayName: 'Dee Disabled', role: 'MEMBER', createdAt: '', active: false },
      { id: 4, userId: 'bot', displayName: 'Publisher Bot', role: 'OWNER', createdAt: '', active: true, systemAccount: true },
    ], total: 4, page: 0, size: 100 })
    api.transferOwnership.mockResolvedValue({ ownerId: 'successor', previousOwnerId: 'leaver' })
    await mount()

    await waitFor(() => expect(screen.getByRole('option', { name: 'Sam Successor' })).toBeTruthy())
    for (const name of ['Jane Doe', 'Dee Disabled', 'Publisher Bot']) {
      expect(screen.queryByRole('option', { name })).toBeNull()
    }

    const transfer = screen.getByRole('button', { name: 'Transfer' }) as HTMLButtonElement
    fireEvent.change(screen.getByLabelText('New owner'), { target: { value: 'successor' } })
    expect(transfer.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('Reason'), { target: { value: '  Left the company ' } })
    fireEvent.click(transfer)

    await waitFor(() => expect(api.transferOwnership).toHaveBeenCalledWith('team', 'foo', 'leaver', 'successor', 'Left the company'))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  it('keeps the dialog open and shows the server message when the transfer is rejected', async () => {
    api.listMembers.mockResolvedValue({ items: [
      { id: 2, userId: 'successor', displayName: 'Sam Successor', role: 'MEMBER', createdAt: '', active: true },
    ], total: 1, page: 0, size: 100 })
    api.transferOwnership.mockRejectedValue(new Error('The new owner already has a skill named foo in this namespace.'))
    await mount()

    await waitFor(() => expect(screen.getByRole('option', { name: 'Sam Successor' })).toBeTruthy())
    fireEvent.change(screen.getByLabelText('New owner'), { target: { value: 'successor' } })
    fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Handover' } })
    fireEvent.click(screen.getByRole('button', { name: 'Transfer' }))

    await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('already has a skill named foo'))
    expect(screen.getByRole('dialog')).toBeTruthy()
  })

  it('reads members through the admin API for a super admin outside the namespace', async () => {
    api.getNamespaceMembers.mockResolvedValue({ items: [
      { id: 2, userId: 'successor', displayName: 'Sam Successor', role: 'MEMBER', createdAt: '', active: true },
    ], total: 1, page: 0, size: 100 })
    await mount(true)

    await waitFor(() => expect(screen.getByRole('option', { name: 'Sam Successor' })).toBeTruthy())
    expect(api.getNamespaceMembers).toHaveBeenCalledWith('team', { page: 0, size: 100 })
    expect(api.listMembers).not.toHaveBeenCalled()
  })
})
