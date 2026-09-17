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
  requestedOperationIds: [] as Array<string | undefined>,
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
  useSuiteBundleOperation: (operationId?: string) => {
    mocks.requestedOperationIds.push(operationId)
    return mocks.operation
  },
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
    mocks.requestedOperationIds = []
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

  it('requires warning acceptance for every affected member', async () => {
    mocks.preview.mutateAsync.mockResolvedValue(preview({
      members: [
        {
          coordinate: '@global/first', sourceType: 'PACKAGE', packagePath: 'members/first',
          relationship: 'ADDED', publishAction: 'CREATE_SKILL', finalVisibility: 'PUBLIC',
          resolvedVersion: '1.0.0', errors: [], warnings: ['first warning'],
        },
        {
          coordinate: '@global/second', sourceType: 'PACKAGE', packagePath: 'members/second',
          relationship: 'ADDED', publishAction: 'CREATE_SKILL', finalVisibility: 'PUBLIC',
          resolvedVersion: '1.0.0', errors: [], warnings: ['second warning'],
        },
      ],
      warnings: ['@global/first: first warning', '@global/second: second warning'],
    }))
    render(<SuiteBundleImport expectedMode="CREATE" />)

    fireEvent.click(screen.getByRole('button', { name: 'pick-zip' }))
    await waitFor(() => expect(screen.getAllByRole('checkbox')).toHaveLength(2))
    const [first, second] = screen.getAllByRole('checkbox')
    const confirmButton = screen.getByRole('button', { name: 'suite.bundle.confirm' })

    fireEvent.click(first)
    expect(confirmButton.hasAttribute('disabled')).toBe(true)
    fireEvent.click(second)
    expect(confirmButton.hasAttribute('disabled')).toBe(false)
    expect(screen.getAllByText('first warning')).toHaveLength(1)
    expect(screen.getAllByText('second warning')).toHaveLength(1)
  })

  it('does not report no changes for a confirmable presentation-only update', async () => {
    mocks.preview.mutateAsync.mockResolvedValue(preview({
      confirmable: true,
      target: {
        mode: 'UPDATE', coordinate: '@global/suite', targetVersion: '2.0.0',
        displayName: 'Updated suite', summary: 'Updated summary', overview: 'Updated overview',
        visibility: 'PUBLIC',
      },
      members: [{
        coordinate: '@global/member', sourceType: 'REFERENCE', relationship: 'UNCHANGED',
        publishAction: 'REFERENCE_VERSION', finalVisibility: 'PUBLIC', resolvedVersion: '1.0.0',
        errors: [], warnings: [],
      }],
    }))
    render(<SuiteBundleImport expectedMode="UPDATE" expectedCoordinate="@global/suite" />)

    fireEvent.click(screen.getByRole('button', { name: 'pick-zip' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'suite.bundle.confirm' })).toBeTruthy())

    expect(screen.queryByText('suite.bundle.noChanges')).toBeNull()
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

  it('opens the dedicated publishing task after confirmation', async () => {
    mocks.preview.mutateAsync.mockResolvedValue(preview({ members: [] }))
    mocks.confirm.mutateAsync.mockResolvedValue({ operationId: 'operation-1', status: 'RUNNING' })
    render(<SuiteBundleImport expectedMode="CREATE" />)
    fireEvent.click(screen.getByRole('button', { name: 'pick-zip' }))
    await waitFor(() => expect(
      screen.getByRole('button', { name: 'suite.bundle.confirm' }).hasAttribute('disabled')
    ).toBe(false))
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.confirm' }))

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/publishing/operation-1',
      replace: true,
    }))
    expect(window.sessionStorage.getItem(
      'skillhub:suite-bundle-operation:CREATE:new'
    )).toBeNull()
  })

  it('preserves the Suite return target for an update publishing task', async () => {
    mocks.preview.mutateAsync.mockResolvedValue(preview({
      members: [],
      target: { mode: 'UPDATE', coordinate: '@global/suite', targetVersion: '2.0.0' },
    }))
    mocks.confirm.mutateAsync.mockResolvedValue({ operationId: 'operation-update', status: 'RUNNING' })
    render(
      <SuiteBundleImport
        expectedMode="UPDATE"
        expectedCoordinate="@global/suite"
        returnToSuite={{ namespace: 'global', slug: 'suite', version: '1.0.0' }}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: 'pick-zip' }))
    await waitFor(() => expect(
      screen.getByRole('button', { name: 'suite.bundle.confirm' }).hasAttribute('disabled')
    ).toBe(false))
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.confirm' }))

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/publishing/operation-update',
      search: {
        suiteNamespace: 'global',
        suiteSlug: 'suite',
        suiteVersion: '1.0.0',
      },
      replace: true,
    }))
  })

  it('opens the dedicated publishing task when a stored operation is restored', async () => {
    const key = 'skillhub:suite-bundle-operation:CREATE:new'
    window.sessionStorage.setItem(key, 'operation-restored')
    mocks.operation.data = {
      operationId: 'operation-restored',
      status: 'RUNNING',
      mode: 'CREATE',
      targetCoordinate: '@global/suite',
      members: [],
    }

    render(<SuiteBundleImport expectedMode="CREATE" />)

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/publishing/operation-restored',
      replace: true,
    }))
    expect(window.sessionStorage.getItem(key)).toBeNull()
  })

  it('opens the dedicated task when restoring its status fails instead of staying stuck', async () => {
    const key = 'skillhub:suite-bundle-operation:CREATE:new'
    window.sessionStorage.setItem(key, 'operation-offline')
    mocks.operation.error = new Error('offline')

    render(<SuiteBundleImport expectedMode="CREATE" />)

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/publishing/operation-offline',
      replace: true,
    }))
    expect(window.sessionStorage.getItem(key)).toBeNull()
  })

  it('reads the new Suite recovery key when an UPDATE route changes without a full page reload', async () => {
    window.sessionStorage.setItem(
      'skillhub:suite-bundle-operation:UPDATE:@global/suite-a',
      'operation-a',
    )
    window.sessionStorage.setItem(
      'skillhub:suite-bundle-operation:UPDATE:@global/suite-b',
      'operation-b',
    )
    const Harness = ({ coordinate }: { coordinate: string }) => (
      <SuiteBundleImport
        key={`UPDATE:${coordinate}`}
        expectedMode="UPDATE"
        expectedCoordinate={coordinate}
      />
    )
    const { rerender } = render(<Harness coordinate="@global/suite-a" />)
    expect(mocks.requestedOperationIds[mocks.requestedOperationIds.length - 1]).toBe('operation-a')

    rerender(<Harness coordinate="@global/suite-b" />)

    await waitFor(() => expect(
      mocks.requestedOperationIds[mocks.requestedOperationIds.length - 1]
    ).toBe('operation-b'))
    expect(window.sessionStorage.getItem(
      'skillhub:suite-bundle-operation:UPDATE:@global/suite-b'
    )).toBe('operation-b')
  })

  it('clears a restored UPDATE operation that belongs to another Suite without exposing actions', async () => {
    const key = 'skillhub:suite-bundle-operation:UPDATE:@global/suite-b'
    window.sessionStorage.setItem(key, 'operation-a')
    mocks.operation.data = {
      operationId: 'operation-a',
      status: 'BLOCKED_RETRYABLE',
      mode: 'UPDATE',
      targetCoordinate: '@global/suite-a',
      members: [{ position: 0, status: 'FAILED_RETRYABLE', redacted: true }],
    }

    render(<SuiteBundleImport expectedMode="UPDATE" expectedCoordinate="@global/suite-b" />)

    expect(screen.queryByText('suite.bundle.redactedMember')).toBeNull()
    expect(screen.queryByRole('button', { name: /suite.bundle.retry/ })).toBeNull()
    expect(screen.queryByRole('button', { name: 'suite.bundle.cancelOperation' })).toBeNull()
    await waitFor(() => expect(window.sessionStorage.getItem(key)).toBeNull())
    expect(screen.getByText('suite.bundle.uploadTitle')).not.toBeNull()
    expect(mocks.toast.error).toHaveBeenCalledWith('suite.bundle.operationTargetMismatch')
  })
})
