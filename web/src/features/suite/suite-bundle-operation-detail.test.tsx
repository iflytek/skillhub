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

    expect(screen.queryByText('suite.bundle.nextStep.WAITING_FOR_MEMBERS')).toBeNull()
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
        skillId: 41,
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
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.viewMemberSkill' }))
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/space/global/member-under-review',
      search: { returnTo: '/dashboard/suites/publishing/operation-1', version: '1.4.0' },
    })
  })

  it('explains when a new member Skill does not exist yet', () => {
    mocks.operation.data = {
      operationId: 'operation-planned',
      status: 'RUNNING',
      mode: 'CREATE',
      targetCoordinate: '@global/care-suite',
      targetVersion: '1.0.0',
      updatedAt: '2026-09-14T04:00:00Z',
      members: [{
        position: 0,
        redacted: false,
        coordinate: '@global/new-member',
        status: 'PLANNED',
        version: '1.0.0',
        skillId: null,
        skillVersionId: null,
        errors: [],
        warnings: [],
      }],
    }

    render(<SuiteBundleOperationDetail operationId="operation-planned" />)

    expect(screen.getByText('suite.bundle.memberSkillNotCreated')).not.toBeNull()
    expect(screen.queryByRole('button', { name: 'suite.bundle.viewMemberSkill' })).toBeNull()
  })

  it('opens the generated Suite draft when publishing completes', () => {
    mocks.operation.data = {
      operationId: 'operation-2',
      status: 'SUITE_DRAFT_CREATED',
      mode: 'CREATE',
      targetCoordinate: '@global/generated-suite',
      targetVersion: '1.2.0',
      resultSuiteId: 8,
      resultSuiteVersionId: 9,
      updatedAt: '2026-09-14T04:00:00Z',
      members: [],
    }

    render(<SuiteBundleOperationDetail operationId="operation-2" />)
    expect(screen.queryByText('suite.bundle.nextStep.SUITE_DRAFT_CREATED')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.openDraft' }))
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/global/generated-suite',
      search: { version: '1.2.0' },
    })
  })

  it('does not offer to open a draft when a completed legacy operation has no Suite result', () => {
    mocks.operation.data = {
      operationId: 'operation-missing-draft',
      status: 'SUITE_DRAFT_CREATED',
      mode: 'CREATE',
      targetCoordinate: '@global/browser-suite',
      targetVersion: '1.0.0',
      resultSuiteId: null,
      resultSuiteVersionId: null,
      updatedAt: '2026-09-14T04:00:00Z',
      members: [{
        position: 0,
        redacted: false,
        coordinate: '@global/browser-entry',
        status: 'COMPLETED',
        version: '1.0.0',
        skillId: null,
        skillVersionId: null,
        sourceType: 'PACKAGE',
        packagePath: 'skills/browser-entry',
        relationship: 'ADDED',
        publishAction: 'CREATE_SKILL',
        visibility: 'PUBLIC',
        errors: [],
        warnings: ['Disallowed file extension: tool.exe'],
      }],
    }

    render(<SuiteBundleOperationDetail operationId="operation-missing-draft" />)

    expect(screen.getByText('suite.bundle.problem.draftMissing.title')).not.toBeNull()
    expect(screen.getByText('suite.bundle.problem.draftMissing.description')).not.toBeNull()
    expect(screen.getByText('suite.bundle.memberStatus.ATTENTION')).not.toBeNull()
    expect(screen.getByText('Disallowed file extension: tool.exe')).not.toBeNull()
    expect(screen.getByText('suite.bundle.memberSkillNotCreated')).not.toBeNull()
    expect(screen.queryByRole('button', { name: 'suite.bundle.openDraft' })).toBeNull()
    expect(screen.getByRole('button', { name: 'suite.bundle.startAgain' })).not.toBeNull()
  })

  it('restarts an update from the original Suite version after re-preview is required', () => {
    mocks.operation.data = {
      operationId: 'operation-3',
      status: 'REPREVIEW_REQUIRED',
      mode: 'UPDATE',
      targetCoordinate: '@team-a/care-suite',
      targetVersion: '1.2.0',
      baseVersion: '1.1.0',
      failureCode: 'BUNDLE_PLAN_CHANGED',
      updatedAt: '2026-09-14T04:00:00Z',
      members: [],
    }

    render(<SuiteBundleOperationDetail operationId="operation-3" />)

    expect(screen.getByText('suite.bundle.problem.planChanged.title')).not.toBeNull()
    expect(screen.getByText('suite.bundle.problem.planChanged.description')).not.toBeNull()
    expect(screen.queryByText('BUNDLE_PLAN_CHANGED')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.startAgain' }))
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/team-a/care-suite/new-version',
      search: { sourceVersion: '1.1.0' },
    })
  })

  it('replaces an internal execution code with a useful explanation and affected member', () => {
    mocks.operation.data = {
      operationId: 'operation-5',
      status: 'BLOCKED_RETRYABLE',
      mode: 'CREATE',
      targetCoordinate: '@global/care-suite',
      targetVersion: '1.0.0',
      failureCode: 'MEMBER_EXECUTION_FAILED',
      updatedAt: '2026-09-14T04:00:00Z',
      members: [{
        position: 0,
        redacted: false,
        coordinate: '@global/member-one',
        status: 'RUNNING',
        version: '1.0.0',
        errors: [],
        warnings: [],
      }],
    }

    render(<SuiteBundleOperationDetail operationId="operation-5" />)

    expect(screen.getByText('suite.bundle.problem.memberExecutionFailed.title')).not.toBeNull()
    expect(screen.getByText('suite.bundle.problem.memberExecutionFailed.description')).not.toBeNull()
    expect(screen.getByText('suite.bundle.problem.affectedMembersLabel')).not.toBeNull()
    expect(screen.getAllByText('@global/member-one')).toHaveLength(2)
    expect(screen.getByRole('button', { name: 'suite.bundle.retryMemberPublish' })).not.toBeNull()
    expect(screen.getByText('suite.bundle.memberStatus.ATTENTION')).not.toBeNull()
    expect(screen.queryByText('suite.bundle.memberStatus.RUNNING')).toBeNull()
    expect(screen.queryByText('MEMBER_EXECUTION_FAILED')).toBeNull()
    expect(screen.queryByText('suite.bundle.nextStep.BLOCKED_RETRYABLE')).toBeNull()
  })

  it('uses a scan-specific retry action for a failed member scan', () => {
    mocks.operation.data = {
      operationId: 'operation-scan',
      status: 'BLOCKED_RETRYABLE',
      mode: 'CREATE',
      targetCoordinate: '@global/care-suite',
      targetVersion: '1.0.0',
      failureCode: 'MEMBER_SCAN_FAILED',
      updatedAt: '2026-09-14T04:00:00Z',
      members: [],
    }

    render(<SuiteBundleOperationDetail operationId="operation-scan" />)

    expect(screen.getByRole('button', { name: 'suite.bundle.retryScan' })).not.toBeNull()
    expect(screen.queryByRole('button', { name: 'suite.bundle.retryMemberPublish' })).toBeNull()
  })

  it('keeps demo-only blocked rows readable without exposing retry or internal codes', () => {
    mocks.operation.data = {
      operationId: 'operation-demo',
      status: 'BLOCKED_RETRYABLE',
      mode: 'CREATE',
      targetCoordinate: '@global/care-suite',
      targetVersion: '1.0.0',
      failureCode: 'MEMBER_EXECUTION_FAILED',
      updatedAt: '2026-09-14T04:00:00Z',
      members: [{
        position: 0,
        redacted: false,
        coordinate: '@global/demo-member',
        status: 'RUNNING',
        version: '1.0.0',
        errors: ['演示数据：仅展示可重试阻塞状态，不具备真实执行计划，请勿点击重试', 'MEMBER_EXECUTION_FAILED'],
        warnings: [],
      }],
    }

    render(<SuiteBundleOperationDetail operationId="operation-demo" />)

    expect(screen.getByText('suite.bundle.problem.memberExecutionFailed.title')).not.toBeNull()
    expect(screen.getByText('suite.bundle.memberStatus.ATTENTION')).not.toBeNull()
    expect(screen.queryByText('演示数据：仅展示可重试阻塞状态，不具备真实执行计划，请勿点击重试')).toBeNull()
    expect(screen.queryByText('MEMBER_EXECUTION_FAILED')).toBeNull()
    expect(screen.queryByRole('button', { name: 'suite.bundle.retryMemberPublish' })).toBeNull()
  })

  it('keeps update semantics when the original Suite version no longer exists', () => {
    mocks.operation.data = {
      operationId: 'operation-4',
      status: 'REPREVIEW_REQUIRED',
      mode: 'UPDATE',
      targetCoordinate: '@team-a/care-suite',
      targetVersion: '1.2.0',
      baseVersion: null,
      updatedAt: '2026-09-14T04:00:00Z',
      members: [],
    }

    render(<SuiteBundleOperationDetail operationId="operation-4" />)

    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.startAgain' }))
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/team-a/care-suite/new-version',
      search: { sourceVersion: undefined },
    })
  })
})
