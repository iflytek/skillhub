/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SuiteBundleOperationDetail } from './suite-bundle-operation-detail'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  cancel: { mutate: vi.fn(), isPending: false },
  retry: { mutate: vi.fn(), isPending: false },
  operation: {
    data: undefined as Record<string, unknown> | undefined,
    isLoading: false,
    error: null as Error | null,
    refetch: vi.fn(),
  },
}))

vi.mock('@tanstack/react-router', () => ({ useNavigate: () => mocks.navigate }))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'zh-CN' } }),
}))
vi.mock('@/shared/hooks/use-suite-queries', () => ({
  useSuiteBundleOperation: () => mocks.operation,
  useCancelSuiteBundleOperation: () => mocks.cancel,
  useRetrySuiteBundleOperation: () => mocks.retry,
}))

describe('SuiteBundleOperationDetail', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
    mocks.operation.data = undefined
    mocks.operation.error = null
  })

  it('explains cancellation before stopping Suite creation', () => {
    mocks.operation.data = {
      operationId: 'operation-1',
      status: 'WAITING_FOR_MEMBERS',
      mode: 'CREATE',
      targetCoordinate: '@global/care-suite',
      targetVersion: '1.0.0',
      updatedAt: '2026-09-14T04:00:00Z',
      members: [],
    }

    render(<SuiteBundleOperationDetail operationId="operation-1" />)

    expect(screen.getByText('suite.bundle.nextStep.WAITING_FOR_MEMBERS')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.cancelOperation' }))

    expect(screen.getByText('suite.bundle.cancelConfirmDescription')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.cancelConfirmAction' }))
    expect(mocks.cancel.mutate).toHaveBeenCalledWith(
      'operation-1',
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    )
  })

  it('keeps the review link available for a created member version after cancellation', () => {
    mocks.operation.data = {
      operationId: 'operation-1',
      status: 'CANCELLED',
      mode: 'CREATE',
      targetCoordinate: '@global/care-suite',
      targetVersion: '1.0.0',
      updatedAt: '2026-09-14T04:00:00Z',
      members: [{
        position: 0,
        redacted: false,
        coordinate: '@global/member-under-review',
        status: 'CANCELLED',
        version: '1.4.0',
        skillVersionId: 42,
        sourceType: 'PACKAGE',
        relationship: 'ADDED',
        publishAction: 'CREATE_VERSION',
        visibility: 'PUBLIC',
        errors: [],
        warnings: [],
      }],
    }

    render(<SuiteBundleOperationDetail operationId="operation-1" />)

    expect(screen.getByText('suite.bundle.cancelledDescription')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.viewMemberReview' }))
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/space/global/member-under-review',
      search: { version: '1.4.0' },
    })
  })

  it('opens the generated Suite draft when publishing completes', () => {
    mocks.operation.data = {
      operationId: 'operation-2',
      status: 'SUITE_DRAFT_CREATED',
      mode: 'CREATE',
      targetCoordinate: '@global/generated-suite',
      targetVersion: '1.2.0',
      updatedAt: '2026-09-14T04:00:00Z',
      members: [],
    }

    render(<SuiteBundleOperationDetail operationId="operation-2" />)
    expect(screen.getByText('suite.bundle.nextStep.SUITE_DRAFT_CREATED')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.openDraft' }))
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/suite/global/generated-suite',
      search: { version: '1.2.0' },
    })
  })

  it('restarts an update from the original Suite version after re-preview is required', () => {
    mocks.operation.data = {
      operationId: 'operation-3',
      status: 'REPREVIEW_REQUIRED',
      mode: 'UPDATE',
      targetCoordinate: '@team-a/care-suite',
      targetVersion: '1.2.0',
      baseVersion: '1.1.0',
      updatedAt: '2026-09-14T04:00:00Z',
      members: [],
    }

    render(<SuiteBundleOperationDetail operationId="operation-3" />)

    expect(screen.getByText('suite.bundle.nextStep.REPREVIEW_REQUIRED')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.startAgain' }))
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/team-a/care-suite/new-version',
      search: { sourceVersion: '1.1.0' },
    })
  })
})
