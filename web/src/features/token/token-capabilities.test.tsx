// @vitest-environment jsdom
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { CreateTokenDialog } from './create-token-dialog'
import { TokenList } from './token-list'

const copy = vi.hoisted(() => vi.fn())
vi.mock('@/shared/lib/clipboard', () => ({
  useCopyToClipboard: () => [false, copy],
}))

const api = vi.hoisted(() => ({
  createToken: vi.fn(),
  getTokens: vi.fn(),
  deleteToken: vi.fn(),
  updateTokenExpiration: vi.fn(),
}))
vi.mock('@/api/client', () => ({ tokenApi: api }))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
}))
vi.mock('@/shared/ui/select', () => ({
  Select: ({
    value,
    onValueChange,
    children,
  }: {
    value: string
    onValueChange: (value: string) => void
    children: ReactNode
  }) => (
    <select
      aria-label={value}
      value={value}
      onChange={(event) => onValueChange(event.target.value)}
    >
      {children}
    </select>
  ),
  SelectContent: ({ children }: { children: ReactNode }) => <>{children}</>,
  SelectItem: ({ value, children }: { value: string; children: ReactNode }) => (
    <option value={value}>{children}</option>
  ),
  SelectTrigger: () => null,
  SelectValue: () => null,
}))

function mount(children: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>{children}</QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('token capabilities UI', () => {
  it('uses server default permissions without a permission selection step', async () => {
    const capabilities = ['skill:read', 'skill:delete', 'namespace:manage', 'namespace:read']
    api.createToken.mockResolvedValue({ token: 'test-value', name: 'test', capabilities })
    mount(<CreateTokenDialog><button>open</button></CreateTokenDialog>)
    fireEvent.click(screen.getByText('open'))
    fireEvent.change(screen.getByLabelText('createToken.nameLabel'), { target: { value: 'test' } })
    expect(screen.queryByRole('group', { name: 'token.permissionsTitle' })).toBeNull()
    expect(screen.queryByRole('checkbox')).toBeNull()
    fireEvent.click(screen.getByText('createToken.create'))
    await waitFor(() => expect(api.createToken).toHaveBeenCalledWith({ name: 'test', expiresAt: undefined }))
    expect(await screen.findByText(capabilities.join(', '))).toBeDefined()
    fireEvent.click(screen.getByText('createToken.savedClose'))
    fireEvent.click(screen.getByText('open'))
    expect(screen.queryByRole('group', { name: 'token.permissionsTitle' })).toBeNull()
    expect((screen.getByLabelText('createToken.nameLabel') as HTMLInputElement).value).toBe('')
  })

  it('renders expanded capabilities and the legacy marker from the server', async () => {
    api.getTokens.mockResolvedValue({
      items: [
        {
          id: 1,
          name: 'legacy',
          tokenPrefix: 'prefix',
          createdAt: '2026-01-01T00:00:00Z',
          capabilities: ['skill:submit', 'skill:publish-private'],
          legacyCompat: true,
        },
      ],
      total: 1,
      page: 0,
      size: 10,
    })
    mount(<TokenList />)
    fireEvent.click(await screen.findByText('token.viewCapabilitiesCount'))
    fireEvent.click(screen.getByText('token.showCapabilityIdentifiers'))
    expect(screen.getByText('skill:submit')).toBeDefined()
    expect(screen.getByText('skill:publish-private')).toBeDefined()
    expect(screen.getByText('token.legacyBadge')).toBeDefined()
  })
})

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<T>((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

const token = (id: number) => ({
  id,
  name: `credential-${id}`,
  tokenPrefix: 'sk_prefix',
  createdAt: '2026-01-01T00:00:00Z',
  capabilities: ['skill:read'],
})

describe('token creation recovery', () => {
  it('blocks duplicate submission and close while issuance is pending', async () => {
    const request = deferred<{
      token: string
      name: string
      capabilities: string[]
    }>()
    api.createToken.mockReturnValue(request.promise)
    mount(
      <CreateTokenDialog>
        <button>open</button>
      </CreateTokenDialog>,
    )
    fireEvent.click(screen.getByText('open'))
    fireEvent.change(screen.getByLabelText('createToken.nameLabel'), {
      target: { value: 'pending' },
    })
    const form = screen.getByLabelText('createToken.nameLabel').closest('form')!
    fireEvent.submit(form)
    fireEvent.submit(form)
    await waitFor(() => expect(api.createToken).toHaveBeenCalledTimes(1))
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(screen.getByRole('dialog')).toBeDefined()
    await act(async () =>
      request.resolve({
        token: 'test-only-value',
        name: 'pending',
        capabilities: ['skill:read'],
      }),
    )
    fireEvent.click(await screen.findByText('createToken.savedClose'))
    fireEvent.click(screen.getByText('open'))
    expect(screen.queryByText('test-only-value')).toBeNull()
    expect(
      (screen.getByLabelText('createToken.nameLabel') as HTMLInputElement)
        .value,
    ).toBe('')
  })

  it('preserves custom inputs after a failed request and allows retry', async () => {
    api.createToken
      .mockRejectedValueOnce(new Error('Issuance unavailable'))
      .mockResolvedValueOnce({
        token: 'retry-value',
        name: 'retry',
        capabilities: ['skill:read'],
      })
    mount(
      <CreateTokenDialog>
        <button>open</button>
      </CreateTokenDialog>,
    )
    fireEvent.click(screen.getByText('open'))
    fireEvent.change(screen.getByLabelText('createToken.nameLabel'), {
      target: { value: 'retry' },
    })
    fireEvent.click(screen.getByText('createToken.create'))
    expect(await screen.findByRole('alert')).toBeDefined()
    expect(
      (screen.getByLabelText('createToken.nameLabel') as HTMLInputElement)
        .value,
    ).toBe('retry')
    expect(screen.queryByRole('group', { name: 'token.permissionsTitle' })).toBeNull()
    fireEvent.click(screen.getByText('createToken.create'))
    expect(await screen.findByText('createToken.savedClose')).toBeDefined()
    expect(api.createToken).toHaveBeenCalledTimes(2)
  })
})

describe('server-backed token listing', () => {
  it('keeps the previous page on a failed page request, then retries the requested page', async () => {
    const second = deferred<{
      items: ReturnType<typeof token>[]
      total: number
      page: number
      size: number
    }>()
    api.getTokens
      .mockResolvedValueOnce({
        items: [token(1)],
        total: 11,
        page: 0,
        size: 10,
      })
      .mockReturnValueOnce(second.promise)
      .mockResolvedValue({ items: [token(11)], total: 11, page: 1, size: 10 })
    mount(<TokenList />)
    expect(await screen.findByText('credential-1')).toBeDefined()
    fireEvent.click(screen.getByText('pagination.next'))
    expect(screen.getByText('credential-1')).toBeDefined()
    await waitFor(() =>
      expect(
        (screen.getByText('pagination.next') as HTMLButtonElement).disabled,
      ).toBe(true),
    )
    await act(async () => second.reject(new Error('Page unavailable')))
    expect(await screen.findByRole('alert')).toBeDefined()
    expect(screen.getByText('credential-1')).toBeDefined()
    fireEvent.click(screen.getByText('token.retry'))
    expect(await screen.findByText('credential-11')).toBeDefined()
    expect(api.getTokens).toHaveBeenLastCalledWith({
      page: 1,
      size: 10,
      query: '',
      status: 'ALL',
    })
  })

  it('resets pagination when applying a name or expiration filter', async () => {
    api.getTokens.mockImplementation(({ page }: { page: number }) =>
      Promise.resolve({ items: [token(page + 1)], total: 11, page, size: 10 }),
    )
    mount(<TokenList />)
    await screen.findByText('credential-1')
    fireEvent.click(screen.getByText('pagination.next'))
    await screen.findByText('credential-2')
    const search = screen.getByLabelText('token.search')
    fireEvent.change(search, { target: { value: '  deployment  ' } })
    fireEvent.submit(search.closest('form')!)
    await waitFor(() =>
      expect(api.getTokens).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        query: 'deployment',
        status: 'ALL',
      }),
    )
    fireEvent.change(screen.getByRole('combobox', { name: 'ALL' }), {
      target: { value: 'EXPIRED' },
    })
    await waitFor(() =>
      expect(api.getTokens).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        query: 'deployment',
        status: 'EXPIRED',
      }),
    )
  })

  it('restores a credential and retains the confirmation after revocation fails', async () => {
    api.getTokens.mockResolvedValue({
      items: [token(1)],
      total: 1,
      page: 0,
      size: 10,
    })
    api.deleteToken.mockRejectedValue(new Error('Revocation unavailable'))
    mount(<TokenList />)
    await screen.findByText('credential-1')
    fireEvent.click(screen.getByText('token.delete'))
    fireEvent.click(screen.getAllByText('token.delete')[1])
    await waitFor(() => expect(api.deleteToken).toHaveBeenCalledWith(1))
    await waitFor(() =>
      expect(screen.getAllByText('credential-1')).toHaveLength(2),
    )
    expect(screen.getByRole('dialog')).toBeDefined()
    expect(
      screen.queryByRole('navigation', { name: 'pagination.label' }),
    ).toBeNull()
  })
})

describe('once-only credential copy', () => {
  it.each([true, false])(
    'keeps credential selectable and reports copy success=%s',
    async (succeeds) => {
      if (succeeds) copy.mockResolvedValue(undefined)
      else copy.mockRejectedValue(new Error('Clipboard unavailable'))
      api.createToken.mockResolvedValue({
        token: 'test-copy-only',
        name: 'copy-test',
        capabilities: ['skill:read'],
      })
      mount(
        <CreateTokenDialog>
          <button>open</button>
        </CreateTokenDialog>,
      )
      fireEvent.click(screen.getByText('open'))
      fireEvent.change(screen.getByLabelText('createToken.nameLabel'), {
        target: { value: 'copy-test' },
      })
      fireEvent.click(screen.getByText('createToken.create'))
      fireEvent.click(await screen.findByText('createToken.copyToken'))
      await waitFor(() => expect(copy).toHaveBeenCalledWith('test-copy-only'))
      if (succeeds)
        expect(await screen.findByText('createToken.copied')).toBeDefined()
      else
        await waitFor(() =>
          expect(screen.getByText('createToken.copyToken')).toBeDefined(),
        )
      expect(screen.getByText('test-copy-only').tagName).toBe('CODE')
      expect(screen.getByText('createToken.manualCopyHint')).toBeDefined()
    },
  )
})

describe('revocation at a page boundary', () => {
  it.each([1, 2])(
    'moves back only when the last page becomes empty (%s items before revoke)',
    async (count) => {
      let remaining = Array.from({ length: count }, (_, index) =>
        token(index + 11),
      )
      api.getTokens.mockImplementation(({ page }: { page: number }) =>
        Promise.resolve({
          items: page === 0 ? [token(1)] : remaining,
          total: 10 + remaining.length,
          page,
          size: 10,
        }),
      )
      api.deleteToken.mockImplementation(async (id: number) => {
        remaining = remaining.filter((item) => item.id !== id)
      })
      mount(<TokenList />)
      await screen.findByText('credential-1')
      fireEvent.click(screen.getByText('pagination.next'))
      await screen.findByText('credential-11')
      fireEvent.click(screen.getAllByText('token.delete')[0])
      fireEvent.click(screen.getAllByText('token.delete')[count])
      await waitFor(() => expect(api.deleteToken).toHaveBeenCalledWith(11))
      await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
      if (count === 1) await screen.findByText('credential-1')
      else await screen.findByText('credential-12')
      expect(api.getTokens).toHaveBeenLastCalledWith({
        page: count === 1 ? 0 : 1,
        size: 10,
        query: '',
        status: 'ALL',
      })
    },
  )
})

describe('default token expiry selection', () => {
  it('preserves the chosen name and expiry while using server default permissions', async () => {
    api.createToken.mockResolvedValue({ token: 'test-expiry-value', name: 'retained', capabilities: ['skill:read'] })
    mount(<CreateTokenDialog><button>open</button></CreateTokenDialog>)
    fireEvent.click(screen.getByText('open'))
    fireEvent.change(screen.getByLabelText('createToken.nameLabel'), { target: { value: 'retained' } })
    fireEvent.change(screen.getByRole('combobox', { name: 'never' }), { target: { value: '7d' } })
    fireEvent.click(screen.getByText('createToken.create'))
    await waitFor(() => expect(api.createToken).toHaveBeenCalledWith({ name: 'retained', expiresAt: expect.any(String) }))
  })
})
