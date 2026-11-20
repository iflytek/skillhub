// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { components } from '@/api/generated/schema'
import { ApiError } from '@/shared/lib/api-error'
import { GithubRepositoryInstall } from './github-repository-install'

const mocks = vi.hoisted(() => ({ fetch: vi.fn(), copy: vi.fn() }))
vi.mock('@/api/client', () => ({ fetchJson: mocks.fetch, buildApiUrl: (path: string) => path, WEB_API_PREFIX: '/api/web' }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ user: { userId: 'reader' }, isLoading: false }) }))
vi.mock('@/shared/lib/clipboard', () => ({ copyToClipboard: mocks.copy }))
vi.mock('react-i18next', async importOriginal => ({
  ...await importOriginal<typeof import('react-i18next')>(),
  useTranslation: () => ({ t: (key: string) => key }),
}))

const plan: components['schemas']['GithubRepositoryInstallPlanResponse'] = {
  repositoryId: '42', owner: 'fixture', name: 'skills', members: [{ skillId: 1, namespace: 'github', slug: 'alpha',
    displayName: 'Alpha', version: '1.0.0', versionId: 11, fingerprint: 'sha256:' + 'a'.repeat(64),
    downloadUrl: '/api/cli/v1/skills/github/alpha/versions/1.0.0/download' }],
}
const clients: QueryClient[] = []
function setup() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  clients.push(client)
  return render(<QueryClientProvider client={client}><GithubRepositoryInstall repositoryId="42" /></QueryClientProvider>)
}
beforeEach(() => { mocks.fetch.mockReset(); mocks.copy.mockReset() })
afterEach(() => { cleanup(); clients.splice(0).forEach(client => client.clear()) })

it('only resolves when expanded and copies a repository command with no snapshot selector', async () => {
  mocks.fetch.mockResolvedValue(plan)
  mocks.copy.mockResolvedValue(undefined)
  setup()
  expect(mocks.fetch).not.toHaveBeenCalled()
  const open = screen.getByRole('button', { name: 'githubCatalog.installOpen' })
  expect(open.getAttribute('aria-expanded')).toBe('false')
  fireEvent.click(open)
  await screen.findByText('Alpha')
  expect(mocks.fetch.mock.calls[0]?.[0]).toBe('/api/web/github-repositories/42/install-plan')
  fireEvent.click(screen.getByRole('button', { name: 'copyButton.copy' }))
  await screen.findByRole('button', { name: 'copyButton.copied' })
  expect(mocks.copy.mock.calls[0]?.[0]).toContain('skillhub repository install 42 --scope project')
  expect(mocks.copy.mock.calls[0]?.[0]).not.toContain('snapshot')
  fireEvent.click(screen.getByRole('button', { name: 'githubCatalog.installCollapse' }))
  expect(screen.queryByText('Alpha')).toBeNull()
})

it('distinguishes no eligible versions from a network error and retries explicitly', async () => {
  mocks.fetch.mockRejectedValueOnce(new ApiError('not found', 404)).mockResolvedValueOnce(plan)
  setup()
  fireEvent.click(screen.getByRole('button', { name: 'githubCatalog.installOpen' }))
  expect((await screen.findByRole('alert')).textContent).toContain('githubCatalog.installEmpty')
  expect(screen.queryByRole('button', { name: 'copyButton.copy' })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'githubCatalog.repositoryRetry' }))
  await screen.findByText('Alpha')
})

it('preserves command and visible error when clipboard access fails', async () => {
  mocks.fetch.mockResolvedValue(plan)
  mocks.copy.mockRejectedValue(new Error('clipboard unavailable'))
  setup()
  fireEvent.click(screen.getByRole('button', { name: 'githubCatalog.installOpen' }))
  await screen.findByText('Alpha')
  fireEvent.click(screen.getByRole('button', { name: 'copyButton.copy' }))
  await waitFor(() => expect(screen.getByRole('alert').textContent).toBe('githubCatalog.installCopyFailed'))
  expect(screen.getByText(/skillhub repository install 42/)).toBeTruthy()
})

it('offers local-only check and upgrade commands without an installation scope or force flag', async () => {
  mocks.fetch.mockResolvedValue(plan)
  mocks.copy.mockResolvedValue(undefined)
  setup()
  fireEvent.click(screen.getByRole('button', { name: 'githubCatalog.installOpen' }))
  await screen.findByText('Alpha')
  for (const action of ['check', 'upgrade']) {
    const choice = screen.getByRole('button', { name: `githubCatalog.repositoryCommandActions.${action}` })
    fireEvent.click(choice)
    expect(choice.getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByText('githubCatalog.repositoryUpdateHint')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'copyButton.copy' }))
    await waitFor(() => expect(mocks.copy).toHaveBeenLastCalledWith(expect.stringContaining(`repository ${action} 42`)))
    const command = mocks.copy.mock.calls.at(-1)?.[0]
    expect(command).not.toContain('--scope')
    expect(command).not.toContain('--force')
  }
})

it('shows pending state and ignores a late result while collapsed', async () => {
  let complete: (value: typeof plan) => void = () => {}
  mocks.fetch.mockReturnValue(new Promise(resolve => { complete = resolve }))
  setup()
  fireEvent.click(screen.getByRole('button', { name: 'githubCatalog.installOpen' }))
  expect(screen.getByRole('status').textContent).toBe('githubCatalog.installLoading')
  fireEvent.click(screen.getByRole('button', { name: 'githubCatalog.installCollapse' }))
  complete(plan)
  await waitFor(() => expect(screen.queryByText('Alpha')).toBeNull())
})
