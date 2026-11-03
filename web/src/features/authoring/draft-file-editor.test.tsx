// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { authoringApi } from '@/api/client'
import { DraftFileEditor } from './draft-file-editor'
import i18n from '@/i18n/config'

vi.mock('@tanstack/react-router', () => ({ useBlocker: () => ({ status: 'idle' }) }))
vi.mock('@/api/client', () => ({ authoringApi: { getDraft: vi.fn(), readFile: vi.fn(), saveFile: vi.fn(), deleteFile: vi.fn() } }))
vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
const clients: QueryClient[] = []
function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  clients.push(client)
  const props = { draftId: 42, revision: 3, files: [{ path: 'SKILL.md', sha256: 'old', size: 4 }, { path: 'scripts/test.sh', sha256: 'script', size: 2 }], onSaved: vi.fn(), onDirtyChange: vi.fn() }
  const view = render(<QueryClientProvider client={client}><DraftFileEditor {...props} /></QueryClientProvider>)
  return { ...view, props, client }
}
beforeEach(async () => {
  vi.clearAllMocks()
  await i18n.changeLanguage('en')
  vi.mocked(authoringApi.getDraft).mockResolvedValue({ revision: 3 })
  vi.mocked(authoringApi.readFile).mockImplementation(async (_id, path) => ({ path, content: path === 'SKILL.md' ? 'original' : 'echo ok', encoding: 'text', sha256: path }))
  vi.mocked(authoringApi.saveFile).mockResolvedValue({ created: true, draft: { revision: 4 } })
})
afterEach(() => { cleanup(); clients.forEach(client => client.clear()); clients.length = 0 })

describe('draft files', () => {
  it('keeps edits after a conflict and uses the revision captured with the read', async () => {
    const { props, rerender, client } = mount()
    const editor = await screen.findByLabelText('File content')
    fireEvent.change(editor, { target: { value: 'my edit' } })
    rerender(<QueryClientProvider client={client}><DraftFileEditor {...props} revision={9} /></QueryClientProvider>)
    vi.mocked(authoringApi.saveFile).mockRejectedValueOnce(new Error('Revision conflict'))
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(authoringApi.saveFile).toHaveBeenCalledWith(42, { path: 'SKILL.md', content: 'my edit', expectedRevision: 3 }))
    await waitFor(() => expect((editor as HTMLTextAreaElement).disabled).toBe(false))
    expect((screen.getByLabelText('File content') as HTMLTextAreaElement).value).toBe('my edit')
    expect(screen.getByText('Unsaved changes')).toBeTruthy()
  })

  it('requires confirmation before switching away from a dirty file', async () => {
    mount()
    fireEvent.change(await screen.findByLabelText('File content'), { target: { value: 'my edit' } })
    fireEvent.click(screen.getByRole('button', { name: 'scripts/test.sh' }))
    expect(screen.getByRole('dialog')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect((screen.getByLabelText('File content') as HTMLTextAreaElement).value).toBe('my edit')
    fireEvent.click(screen.getByRole('button', { name: 'scripts/test.sh' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    await waitFor(() => expect((screen.getByLabelText('File content') as HTMLTextAreaElement).value).toBe('echo ok'))
  })

  it('does not create an empty file over an existing path', async () => {
    mount()
    await screen.findByLabelText('File content')
    fireEvent.change(screen.getByLabelText('Path'), { target: { value: 'skill.md' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create file' }))
    expect(authoringApi.saveFile).not.toHaveBeenCalled()
  })

  it('uses server encoding rather than an extension to protect binary bytes', async () => {
    vi.mocked(authoringApi.readFile).mockResolvedValue({ content: '/wAB', encoding: 'base64', sha256: 'binary' })
    mount()
    await screen.findByTestId('binary-file-panel')
    expect(screen.queryByLabelText('File content')).toBeNull()
    expect(screen.queryByText('/wAB')).toBeNull()
  })

  it('shows a retry on read failure instead of an editable empty file', async () => {
    vi.mocked(authoringApi.readFile).mockRejectedValueOnce(new Error('unavailable'))
    mount()
    await screen.findByRole('alert')
    expect(screen.queryByLabelText('File content')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    await screen.findByLabelText('File content')
  })

  it('can discard edits and reload even when the server bytes did not change', async () => {
    mount()
    fireEvent.change(await screen.findByLabelText('File content'), { target: { value: 'my edit' } })
    fireEvent.click(screen.getByRole('button', { name: 'Reload file' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    await waitFor(() => expect((screen.getByLabelText('File content') as HTMLTextAreaElement).value).toBe('original'))
    expect(screen.queryByText('Unsaved changes')).toBeNull()
  })
})
