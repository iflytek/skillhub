/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { SuiteBundleImport } from './suite-bundle-import'

const mocks = vi.hoisted(() => ({
  preview: { mutateAsync: vi.fn(), isPending: false },
  confirm: { mutateAsync: vi.fn(), isPending: false },
  cancel: { mutate: vi.fn(), isPending: false },
  retry: { mutate: vi.fn(), isPending: false },
  operation: {
    data: undefined as Record<string, unknown> | undefined,
    isLoading: false,
    error: null as Error | null,
    refetch: vi.fn(),
  },
  packageFolder: vi.fn(),
  toast: { error: vi.fn() },
  navigate: vi.fn(),
}))

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/shared/lib/toast', () => ({ toast: mocks.toast }))
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => mocks.navigate }))
vi.mock('@/features/publish/folder-zip', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/features/publish/folder-zip')>()),
  packageFolderAsZip: mocks.packageFolder,
}))
vi.mock('@/features/publish/upload-zone', () => ({
  UploadZone: ({ onFileSelect, onFolderSelect }: {
    onFileSelect: (file: File) => void
    onFolderSelect: (files: File[]) => void
  }) => (
    <div>
      <button onClick={() => onFileSelect(new File(['zip'], 'bundle.zip'))}>pick-zip</button>
      <button onClick={() => onFolderSelect([folderFile('bundle/SUITE.yaml')])}>pick-folder</button>
      <button onClick={() => onFolderSelect([folderFile('bundle/SKILL.md')])}>pick-invalid-folder</button>
    </div>
  ),
}))
vi.mock('@/shared/hooks/use-suite-queries', () => ({
  usePreviewSuiteBundle: () => mocks.preview,
  useConfirmSuiteBundle: () => mocks.confirm,
  useCancelSuiteBundleOperation: () => mocks.cancel,
  useRetrySuiteBundleOperation: () => mocks.retry,
  useSuiteBundleOperation: () => mocks.operation,
}))

function folderFile(path: string): File {
  const file = new File(['content'], path.split('/').pop() || path)
  Object.defineProperty(file, 'webkitRelativePath', { value: path })
  return file
}

function preview(overrides: Record<string, unknown> = {}) {
  return {
    previewToken: 'preview-1',
    warningDigest: 'digest-1',
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    confirmable: true,
    target: { mode: 'CREATE', coordinate: '@global/suite', targetVersion: '1.0.0' },
    members: [{
      coordinate: '@global/member', sourceType: 'PACKAGE', packagePath: 'members/member', relationship: 'ADDED',
      publishAction: 'CREATE_SKILL', finalVisibility: 'PUBLIC', resolvedVersion: '1.0.0',
      errors: [], warnings: ['review visibility'],
    }],
    removedMembers: [], errors: [], warnings: [],
    ...overrides,
  }
}

describe('SuiteBundleImport', () => {
  beforeEach(() => {
    vi.stubGlobal('crypto', { randomUUID: () => 'request-1' })
    window.sessionStorage.clear()
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
    vi.unstubAllGlobals()
    mocks.operation.data = undefined
    mocks.operation.error = null
    mocks.preview.isPending = false
  })

  it('uploads one archive, requires explicit warning acceptance and confirms the exact preview', async () => {
    mocks.preview.mutateAsync.mockResolvedValue(preview())
    mocks.confirm.mutateAsync.mockResolvedValue({ operationId: 'operation-1', status: 'RUNNING' })
    render(<SuiteBundleImport expectedMode="CREATE" />)

    fireEvent.click(screen.getByRole('button', { name: 'pick-zip' }))
    await waitFor(() => expect(screen.getByText('@global/member')).not.toBeNull())
    expect(screen.getByText('suite.bundle.memberDirectory')).not.toBeNull()
    expect(mocks.preview.mutateAsync).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'suite.bundle.confirm' }).hasAttribute('disabled')).toBe(true)

    fireEvent.click(screen.getByRole('checkbox'))
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.confirm' }))

    await waitFor(() => expect(mocks.confirm.mutateAsync).toHaveBeenCalledWith({
      previewToken: 'preview-1',
      warningDigest: 'digest-1',
      idempotencyKey: 'request-1',
    }))
  })

  it('blocks a Bundle targeting a different workflow entry', async () => {
    mocks.preview.mutateAsync.mockResolvedValue(preview({
      target: { mode: 'UPDATE', coordinate: '@global/other', targetVersion: '2.0.0' },
      members: [],
    }))
    render(<SuiteBundleImport expectedMode="UPDATE" expectedCoordinate="@global/expected" />)

    fireEvent.click(screen.getByRole('button', { name: 'pick-zip' }))

    await waitFor(() => expect(screen.getByText('suite.bundle.targetMismatch')).not.toBeNull())
    expect(screen.getByRole('button', { name: 'suite.bundle.confirm' }).hasAttribute('disabled')).toBe(true)
  })

  it('blocks confirmation after the preview expires', async () => {
    mocks.preview.mutateAsync.mockResolvedValue(preview({
      expiresAt: '2000-01-01T00:00:00Z',
      members: [],
    }))
    render(<SuiteBundleImport expectedMode="CREATE" />)

    fireEvent.click(screen.getByRole('button', { name: 'pick-zip' }))

    await waitFor(() => expect(screen.getByText('suite.bundle.previewExpired')).not.toBeNull())
    expect(screen.getByRole('button', { name: 'suite.bundle.confirm' }).hasAttribute('disabled')).toBe(true)
  })

  it('requires explicit acknowledgement for member removals even without warnings', async () => {
    mocks.preview.mutateAsync.mockResolvedValue(preview({
      members: [],
      removedMembers: [{ coordinate: '@global/entry', version: '1.0.0', entry: true }],
    }))
    render(<SuiteBundleImport expectedMode="CREATE" />)

    fireEvent.click(screen.getByRole('button', { name: 'pick-zip' }))

    await waitFor(() => expect(screen.getByText(/suite.bundle.removedEntryMember/)).not.toBeNull())
    const confirmButton = screen.getByRole('button', { name: 'suite.bundle.confirm' })
    expect(confirmButton.hasAttribute('disabled')).toBe(true)
    fireEvent.click(screen.getByRole('checkbox'))
    expect(confirmButton.hasAttribute('disabled')).toBe(false)
  })

  it('reuses the same confirmation key after a lost or failed response', async () => {
    mocks.preview.mutateAsync.mockResolvedValue(preview({ members: [] }))
    mocks.confirm.mutateAsync.mockRejectedValue(new Error('response lost'))
    render(<SuiteBundleImport expectedMode="CREATE" />)
    fireEvent.click(screen.getByRole('button', { name: 'pick-zip' }))
    await waitFor(() => expect(
      screen.getByRole('button', { name: 'suite.bundle.confirm' }).hasAttribute('disabled')
    ).toBe(false))

    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.confirm' }))
    await waitFor(() => expect(mocks.confirm.mutateAsync).toHaveBeenCalledTimes(1))
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.confirm' }))
    await waitFor(() => expect(mocks.confirm.mutateAsync).toHaveBeenCalledTimes(2))

    expect(mocks.confirm.mutateAsync.mock.calls[0][0].idempotencyKey).toBe('request-1')
    expect(mocks.confirm.mutateAsync.mock.calls[1][0].idempotencyKey).toBe('request-1')
  })

  it('rejects a folder without SUITE.yaml before packaging or upload', () => {
    render(<SuiteBundleImport expectedMode="CREATE" />)

    fireEvent.click(screen.getByRole('button', { name: 'pick-invalid-folder' }))

    expect(mocks.toast.error).toHaveBeenCalledWith('suite.bundle.errors.missing-suite-manifest')
    expect(mocks.packageFolder).not.toHaveBeenCalled()
    expect(mocks.preview.mutateAsync).not.toHaveBeenCalled()
  })

  it('uploads a packaged folder once without recursively starting a new selection', async () => {
    mocks.packageFolder.mockResolvedValue(new File(['zip'], 'bundle.zip'))
    mocks.preview.mutateAsync.mockResolvedValue(preview({ members: [] }))
    render(<SuiteBundleImport expectedMode="CREATE" />)

    fireEvent.click(screen.getByRole('button', { name: 'pick-folder' }))

    await waitFor(() => expect(mocks.preview.mutateAsync).toHaveBeenCalledTimes(1))
    expect(mocks.packageFolder).toHaveBeenCalledTimes(1)
  })

  it('prevents an older folder packaging result from replacing a newer ZIP selection', async () => {
    let finishFolder!: (file: File) => void
    mocks.packageFolder.mockImplementation(() => new Promise<File>((resolve) => { finishFolder = resolve }))
    mocks.preview.mutateAsync.mockResolvedValue(preview({ members: [] }))
    render(<SuiteBundleImport expectedMode="CREATE" />)

    fireEvent.click(screen.getByRole('button', { name: 'pick-folder' }))
    fireEvent.click(screen.getByRole('button', { name: 'pick-zip' }))
    await waitFor(() => expect(mocks.preview.mutateAsync).toHaveBeenCalledTimes(1))
    finishFolder(new File(['old'], 'old-folder.zip'))

    await waitFor(() => expect(mocks.preview.mutateAsync).toHaveBeenCalledTimes(1))
    expect(mocks.preview.mutateAsync.mock.calls[0][0].file.name).toBe('bundle.zip')
  })

  it('aborts the previous preview request when a newer archive is selected', async () => {
    let firstSignal: AbortSignal | undefined
    mocks.preview.mutateAsync
      .mockImplementationOnce(({ signal }: { signal?: AbortSignal }) => {
        firstSignal = signal
        return new Promise(() => {})
      })
      .mockResolvedValueOnce(preview({ members: [] }))
    render(<SuiteBundleImport expectedMode="CREATE" />)

    fireEvent.click(screen.getByRole('button', { name: 'pick-zip' }))
    fireEvent.click(screen.getByRole('button', { name: 'pick-zip' }))

    await waitFor(() => expect(mocks.preview.mutateAsync).toHaveBeenCalledTimes(2))
    expect(firstSignal?.aborted).toBe(true)
  })

  it('shows redacted progress and exposes retry and non-destructive cancel actions', async () => {
    mocks.preview.mutateAsync.mockResolvedValue(preview({ members: [] }))
    mocks.confirm.mutateAsync.mockResolvedValue({ operationId: 'operation-1', status: 'RUNNING' })
    mocks.operation.data = {
      operationId: 'operation-1',
      status: 'BLOCKED_RETRYABLE',
      members: [{ position: 0, status: 'FAILED_RETRYABLE', redacted: true }],
    }
    render(<SuiteBundleImport expectedMode="CREATE" />)
    fireEvent.click(screen.getByRole('button', { name: 'pick-zip' }))
    await waitFor(() => expect(
      screen.getByRole('button', { name: 'suite.bundle.confirm' }).hasAttribute('disabled')
    ).toBe(false))
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.confirm' }))

    await waitFor(() => expect(screen.getByText('suite.bundle.redactedMember')).not.toBeNull())
    fireEvent.click(screen.getByRole('button', { name: /suite.bundle.retry/ }))
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.cancelOperation' }))

    expect(mocks.retry.mutate).toHaveBeenCalledWith('operation-1', expect.objectContaining({ onError: expect.any(Function) }))
    expect(mocks.cancel.mutate).toHaveBeenCalledWith('operation-1', expect.objectContaining({ onError: expect.any(Function) }))
  })

  it('lets the user reload an operation after a status request fails', async () => {
    mocks.preview.mutateAsync.mockResolvedValue(preview({ members: [] }))
    mocks.confirm.mutateAsync.mockResolvedValue({ operationId: 'operation-1', status: 'RUNNING' })
    mocks.operation.error = new Error('offline')
    render(<SuiteBundleImport expectedMode="CREATE" />)
    fireEvent.click(screen.getByRole('button', { name: 'pick-zip' }))
    await waitFor(() => expect(
      screen.getByRole('button', { name: 'suite.bundle.confirm' }).hasAttribute('disabled')
    ).toBe(false))
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.confirm' }))

    await waitFor(() => expect(screen.getByRole('button', { name: /suite.bundle.reloadOperation/ })).not.toBeNull())
    fireEvent.click(screen.getByRole('button', { name: /suite.bundle.reloadOperation/ }))
    expect(mocks.operation.refetch).toHaveBeenCalledTimes(1)
  })

  it('restores an operation after refresh and can discard a stale recovery entry', async () => {
    window.sessionStorage.setItem('skillhub:suite-bundle-operation:CREATE:new', 'operation-restored')
    mocks.operation.error = new Error('not found')

    render(<SuiteBundleImport expectedMode="CREATE" />)

    expect(screen.getByText('operation-restored')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.forgetOperation' }))
    await waitFor(() => expect(window.sessionStorage.getItem(
      'skillhub:suite-bundle-operation:CREATE:new'
    )).toBeNull())
  })

  it('links a waiting member to its exact Skill version and shows its package path', () => {
    window.sessionStorage.setItem('skillhub:suite-bundle-operation:CREATE:new', 'operation-waiting')
    mocks.operation.data = {
      operationId: 'operation-waiting',
      status: 'WAITING_FOR_MEMBERS',
      members: [{
        position: 0,
        status: 'WAITING_FOR_MEMBER',
        redacted: false,
        coordinate: '@global/member-under-review',
        version: '1.4.0',
        visibility: 'PUBLIC',
        sourceType: 'PACKAGE',
        relationship: 'UPDATED',
        publishAction: 'CREATE_VERSION',
        packagePath: 'skills/member-under-review',
        errors: [],
        warnings: [],
      }],
    }

    render(<SuiteBundleImport expectedMode="CREATE" />)
    expect(screen.getByText('suite.bundle.memberDirectory')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.viewMemberReview' }))

    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/space/global/member-under-review',
      search: { version: '1.4.0' },
    })
  })

  it('opens the exact generated Suite draft from a recovered terminal operation', () => {
    window.sessionStorage.setItem('skillhub:suite-bundle-operation:CREATE:new', 'operation-complete')
    mocks.operation.data = {
      operationId: 'operation-complete',
      status: 'SUITE_DRAFT_CREATED',
      targetCoordinate: '@global/generated-suite',
      targetVersion: '1.2.0',
      members: [],
    }

    render(<SuiteBundleImport expectedMode="CREATE" />)
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.openDraft' }))

    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/suite/global/generated-suite',
      search: { version: '1.2.0' },
    })
  })
})
