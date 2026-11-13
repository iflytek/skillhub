// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { CreateTokenDialog } from './create-token-dialog'
import { TokenList } from './token-list'

const api = vi.hoisted(() => ({ createToken: vi.fn(), getTokens: vi.fn() }))
vi.mock('@/api/client', () => ({ tokenApi: api }))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
}))
vi.mock('@/shared/ui/select', () => ({
  Select: ({ value, onValueChange, children }: {
    value: string; onValueChange: (value: string) => void; children: ReactNode
  }) => <select aria-label={value} value={value} onChange={(event) => onValueChange(event.target.value)}>{children}</select>,
  SelectContent: ({ children }: { children: ReactNode }) => <>{children}</>,
  SelectItem: ({ value, children }: { value: string; children: ReactNode }) => <option value={value}>{children}</option>,
  SelectTrigger: () => null,
  SelectValue: () => null,
}))

function mount(children: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>{children}</QueryClientProvider>)
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('token capabilities UI', () => {
  it.each([
    ['readonly', ['skill:read']],
    ['candidate', ['skill:read', 'skill:submit']],
    ['private', ['skill:read', 'skill:submit', 'skill:publish-private']],
  ])('submits explicit %s scopes without destructive permissions', async (preset, scopes) => {
    api.createToken.mockResolvedValue({ token: 'test-value', name: 'test', capabilities: scopes })
    mount(<CreateTokenDialog><button>open</button></CreateTokenDialog>)
    fireEvent.click(screen.getByText('open'))
    fireEvent.change(screen.getByLabelText('createToken.nameLabel'), { target: { value: 'test' } })
    fireEvent.change(screen.getByRole('combobox', { name: 'candidate' }), { target: { value: preset } })
    expect(screen.queryByRole('checkbox')).toBeNull()
    fireEvent.click(screen.getByText('createToken.create'))
    await waitFor(() => expect(api.createToken).toHaveBeenCalledWith({ name: 'test', expiresAt: undefined, scopes }))
    expect(await screen.findByText(scopes.join(', '))).toBeDefined()
  })

  it('requires explicit custom selection for delete and token management and resets on reopen', async () => {
    api.createToken.mockResolvedValue({ token: 'test-value', name: 'test', capabilities: ['skill:delete', 'token:manage'] })
    mount(<CreateTokenDialog><button>open</button></CreateTokenDialog>)
    fireEvent.click(screen.getByText('open'))
    fireEvent.change(screen.getByRole('combobox', { name: 'candidate' }), { target: { value: 'custom' } })
    fireEvent.change(screen.getByLabelText('createToken.nameLabel'), { target: { value: 'test' } })
    fireEvent.click(screen.getByLabelText('token.capabilityLabels.delete (skill:delete)'))
    fireEvent.click(screen.getByLabelText('token.capabilityLabels.tokenManage (token:manage)'))
    fireEvent.click(screen.getByText('createToken.create'))
    await waitFor(() => expect(api.createToken).toHaveBeenCalledWith({
      name: 'test', expiresAt: undefined, scopes: ['skill:delete', 'token:manage'],
    }))
    fireEvent.click(await screen.findByText('dialog.close'))
    fireEvent.click(screen.getByText('open'))
    expect(screen.getByRole('combobox', { name: 'candidate' })).toBeDefined()
    expect(screen.queryByRole('checkbox')).toBeNull()
  })

  it('renders expanded capabilities and the legacy marker from the server', async () => {
    api.getTokens.mockResolvedValue({ items: [{
      id: 1, name: 'legacy', tokenPrefix: 'prefix', createdAt: '2026-01-01T00:00:00Z',
      capabilities: ['skill:submit', 'skill:publish-private'], legacyCompat: true,
    }], total: 1, page: 0, size: 10 })
    mount(<TokenList />)
    expect(await screen.findByText('skill:submit, skill:publish-private')).toBeDefined()
    expect(screen.getByText('token.legacyCompat')).toBeDefined()
  })
})
