/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { PagedResponse, SkillSuiteBundleOperationSummary } from '@/api/types'
import { MySuitesPage } from './my-suites'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  remember: vi.fn(),
  active: { items: [], total: 0, page: 0, size: 12 } as PagedResponse<SkillSuiteBundleOperationSummary>,
}))

vi.mock('@tanstack/react-router', () => ({ useNavigate: () => mocks.navigate }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/features/suite/suite-bundle-import', () => ({
  rememberSuiteBundleOperation: mocks.remember,
}))
vi.mock('@/shared/hooks/use-suite-queries', () => ({
  useMySuites: () => ({ data: { items: [], total: 0, page: 0, size: 12 }, isLoading: false }),
  useActiveSuiteBundleOperations: () => ({ data: mocks.active, isLoading: false }),
}))

describe('MySuitesPage', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
    mocks.active = { items: [], total: 0, page: 0, size: 12 }
  })

  it('shows an active Bundle before its Suite draft exists and restores its progress', () => {
    mocks.active = {
      items: [{
        operationId: 'operation-1',
        mode: 'CREATE',
        targetCoordinate: '@global/waiting-suite',
        targetVersion: '1.0.0',
        status: 'WAITING_FOR_MEMBERS',
        failureCode: undefined,
        baseVersion: undefined,
        totalMembers: 2,
        completedMembers: 1,
        waitingMembers: 1,
        updatedAt: '2026-09-14T04:00:00Z',
      }],
      total: 1,
      page: 0,
      size: 12,
    }

    render(<MySuitesPage />)

    expect(screen.getByText('@global/waiting-suite@1.0.0')).not.toBeNull()
    expect(screen.getByText('suite.bundle.status.WAITING_FOR_MEMBERS')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.continueOperation' }))
    expect(mocks.remember).toHaveBeenCalledWith('CREATE', '@global/waiting-suite', 'operation-1')
    expect(mocks.navigate).toHaveBeenCalledWith({ to: '/dashboard/suites/new' })
  })

  it('restores an UPDATE operation against its frozen base version', () => {
    mocks.active = {
      items: [{
        operationId: 'operation-2',
        mode: 'UPDATE',
        targetCoordinate: '@team-ai/care-suite',
        targetVersion: '1.1.0',
        status: 'BLOCKED_RETRYABLE',
        failureCode: 'TEMPORARY_STORAGE_FAILURE',
        baseVersion: '1.0.0',
        totalMembers: 3,
        completedMembers: 2,
        waitingMembers: 0,
        updatedAt: '2026-09-14T05:00:00Z',
      }],
      total: 1,
      page: 0,
      size: 12,
    }

    render(<MySuitesPage />)
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.continueOperation' }))

    expect(mocks.remember).toHaveBeenCalledWith('UPDATE', '@team-ai/care-suite', 'operation-2')
    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/team-ai/care-suite/new-version',
      search: { sourceVersion: '1.0.0' },
    })
  })
})
