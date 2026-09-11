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
}))

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/shared/lib/toast', () => ({ toast: mocks.toast }))
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
    expiresAt: '2026-09-11T09:00:00Z',
    confirmable: true,
    target: { mode: 'CREATE', coordinate: '@global/suite', targetVersion: '1.0.0' },
    members: [{
      coordinate: '@global/member', sourceType: 'PACKAGE', relationship: 'ADDED',
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
})
