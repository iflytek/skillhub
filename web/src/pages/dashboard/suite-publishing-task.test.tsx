/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SuitePublishingTaskPage } from './suite-publishing-task'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  search: {} as { suiteNamespace?: string; suiteSlug?: string; suiteVersion?: string },
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mocks.navigate,
  useParams: () => ({ operationId: 'operation-1' }),
  useSearch: () => mocks.search,
}))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/features/suite/suite-bundle-operation-detail', () => ({
  SuiteBundleOperationDetail: ({ operationId }: { operationId: string }) => <div>{operationId}</div>,
}))

describe('SuitePublishingTaskPage', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
    mocks.search = {}
  })

  it('returns to the Suite publishing tab when opened from a Suite', () => {
    mocks.search = { suiteNamespace: 'global', suiteSlug: 'starter', suiteVersion: '1.0.0' }
    render(<SuitePublishingTaskPage />)

    fireEvent.click(screen.getByRole('button', { name: 'suite.bundle.backToSuite' }))

    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites/global/starter',
      search: { version: '1.0.0', tab: 'publishing' },
    })
  })

  it('returns to my suites when opened without Suite context', () => {
    render(<SuitePublishingTaskPage />)

    fireEvent.click(screen.getByRole('button', { name: 'suite.management.backToSuites' }))

    expect(mocks.navigate).toHaveBeenCalledWith({
      to: '/dashboard/suites',
    })
  })
})
