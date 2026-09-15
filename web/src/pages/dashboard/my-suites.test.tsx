/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SkillSuiteBundleOperationPage, SkillSuiteBundleOperationSummary } from '@/api/types'
import { MySuitesPage } from './my-suites'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  search: { tab: 'publishing' as 'publishing' | undefined },
  suiteHook: vi.fn(),
  operationHook: vi.fn(),
  operations: {
    items: [], total: 0, page: 0, size: 12, hasChangingOperations: false,
  } as SkillSuiteBundleOperationPage,
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mocks.navigate,
  useSearch: () => mocks.search,
}))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'zh-CN' } }),
}))
vi.mock('@/shared/hooks/use-suite-queries', () => ({
  useMySuites: (...args: unknown[]) => {
    mocks.suiteHook(...args)
    return { data: { items: [], total: 0, page: 0, size: 12 }, isLoading: false }
  },
  useMySuiteBundleOperations: (page: number, size: number, enabled: boolean) => {
    mocks.operationHook(page, size, enabled)
    return { data: mocks.operations, isLoading: false }
  },
}))

function operation(
  operationId: string,
  status: SkillSuiteBundleOperationSummary['status'],
): SkillSuiteBundleOperationSummary {
  return {
    operationId,
    mode: 'CREATE',
    targetCoordinate: `@global/${operationId}`,
    targetVersion: '1.0.0',
    status,
    failureCode: undefined,
    baseVersion: undefined,
    totalMembers: 2,
    completedMembers: status === 'SUITE_DRAFT_CREATED' ? 2 : 1,
    waitingMembers: status === 'WAITING_FOR_MEMBERS' ? 1 : 0,
    updatedAt: '2026-09-14T04:00:00Z',
  }
}

describe('MySuitesPage publishing tasks', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
    mocks.search = { tab: 'publishing' }
    mocks.operations = {
      items: [], total: 0, page: 0, size: 12, hasChangingOperations: false,
    }
  })

  it('groups attention, in-progress, completed and cancelled tasks without a spinner', () => {
    mocks.operations = {
      items: [
        operation('blocked', 'BLOCKED_RETRYABLE'),
        operation('repreview', 'REPREVIEW_REQUIRED'),
        operation('running', 'RUNNING'),
        operation('waiting', 'WAITING_FOR_MEMBERS'),
        operation('draft', 'SUITE_DRAFT_CREATED'),
        operation('cancelled', 'CANCELLED'),
      ],
      total: 6,
      page: 0,
      size: 12,
      hasChangingOperations: true,
    }

    const { container } = render(<MySuitesPage />)

    expect(screen.getByText('suite.bundle.groups.attention')).not.toBeNull()
    expect(screen.getByText('suite.bundle.groups.progress')).not.toBeNull()
    expect(screen.getByText('suite.bundle.groups.recent')).not.toBeNull()
    expect(screen.getByText('@global/cancelled@1.0.0')).not.toBeNull()
    expect(screen.getByText('suite.bundle.statusLabel.BLOCKED_RETRYABLE')).not.toBeNull()
    expect(screen.getByText('suite.bundle.statusLabel.REPREVIEW_REQUIRED')).not.toBeNull()
    expect(screen.getByText('suite.bundle.statusLabel.RUNNING')).not.toBeNull()
    expect(screen.getByText('suite.bundle.statusLabel.WAITING_FOR_MEMBERS')).not.toBeNull()
    expect(screen.getByText('suite.bundle.statusLabel.SUITE_DRAFT_CREATED')).not.toBeNull()
    expect(screen.getByText('suite.bundle.statusLabel.CANCELLED')).not.toBeNull()
    expect(screen.getByText('suite.bundle.nextStep.BLOCKED_RETRYABLE')).not.toBeNull()
    expect(screen.getByText('suite.bundle.nextStep.REPREVIEW_REQUIRED')).not.toBeNull()
    expect(screen.getByText('suite.bundle.nextStep.RUNNING')).not.toBeNull()
    expect(screen.getByText('suite.bundle.nextStep.WAITING_FOR_MEMBERS')).not.toBeNull()
    expect(screen.getByText('suite.bundle.nextStep.SUITE_DRAFT_CREATED')).not.toBeNull()
    expect(screen.getByText('suite.bundle.nextStep.CANCELLED')).not.toBeNull()
    expect(container.querySelector('.animate-spin')).toBeNull()
    expect(mocks.suiteHook).toHaveBeenCalledWith('', 0, 12, false)
    expect(mocks.operationHook).toHaveBeenCalledWith(0, 12, true)
  })

  it('opens a dedicated task detail instead of a Suite creation page', () => {
    mocks.operations = {
      items: [operation('waiting', 'WAITING_FOR_MEMBERS')],
      total: 1,
      page: 0,
      size: 12,
      hasChangingOperations: true,
    }

    render(<MySuitesPage />)
    fireEvent.click(screen.getByRole('button', { name: /@global\/waiting@1.0.0/ }))

    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/publishing/waiting',
    })
  })

  it('keeps the publishing tab in the URL and returns to a valid task page', async () => {
    mocks.operations = {
      items: [operation('task-13', 'WAITING_FOR_MEMBERS')],
      total: 13,
      page: 0,
      size: 12,
      hasChangingOperations: true,
    }
    const view = render(<MySuitesPage />)
    fireEvent.click(screen.getByRole('button', { name: 'pagination.next' }))
    expect(mocks.operationHook).toHaveBeenCalledWith(1, 12, true)

    mocks.operations = {
      items: [], total: 12, page: 1, size: 12, hasChangingOperations: false,
    }
    view.rerender(<MySuitesPage />)
    await waitFor(() => expect(mocks.operationHook).toHaveBeenLastCalledWith(0, 12, true))

    fireEvent.click(screen.getByRole('tab', { name: 'suite.tabs.suites' }))
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites',
      search: { tab: undefined },
      replace: true,
    })
  })
})
