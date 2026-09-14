/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SkillSuiteBundleOperationSummary } from '@/api/types'
import { MySuitesPage } from './my-suites'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  remember: vi.fn(),
  active: [] as SkillSuiteBundleOperationSummary[],
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
    mocks.active = []
  })

  it('shows an active Bundle before its Suite draft exists and restores its progress', () => {
    mocks.active = [{
      operationId: 'operation-1',
      mode: 'CREATE',
      targetCoordinate: '@global/waiting-suite',
      targetVersion: '1.0.0',
      status: 'WAITING_FOR_MEMBERS',
      failureCode: undefined,
      totalMembers: 2,
      completedMembers: 1,
      waitingMembers: 1,
      updatedAt: '2026-09-14T04:00:00Z',
    }]

    render(<MySuitesPage />)

    expect(screen.getByText('@global/waiting-suite@1.0.0')).not.toBeNull()
    expect(screen.getByText('suite.bundle.status.WAITING_FOR_MEMBERS')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.continueOperation' }))
    expect(mocks.remember).toHaveBeenCalledWith('CREATE', '@global/waiting-suite', 'operation-1')
    expect(mocks.navigate).toHaveBeenCalledWith({ to: '/dashboard/suites/new' })
  })
})
