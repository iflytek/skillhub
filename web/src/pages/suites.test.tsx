/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SuitesPage } from './suites'

const mocks = vi.hoisted(() => ({
  search: vi.fn((_params?: unknown) => ({ data: { items: [], total: 0, page: 0, size: 12 }, isLoading: false })),
}))

vi.mock('@tanstack/react-router', () => ({ useNavigate: () => vi.fn() }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/shared/hooks/use-suite-queries', () => ({
  useResourceSearch: (params: unknown) => mocks.search(params),
}))
vi.mock('@/shared/hooks/use-label-queries', () => ({
  useVisibleLabels: () => ({
    data: [{ slug: 'healthcare', type: 'RECOMMENDED', displayName: '医疗健康' }],
    isLoading: false,
  }),
}))
vi.mock('@/features/suite/resource-card', () => ({ ResourceCard: () => null }))

describe('SuitesPage', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('filters the Suite catalog with a direct Suite label parameter', () => {
    render(<SuitesPage />)

    fireEvent.click(screen.getByRole('button', { name: '医疗健康' }))

    expect(mocks.search).toHaveBeenLastCalledWith(expect.objectContaining({
      resourceType: 'SUITE',
      labels: ['healthcare'],
      page: 0,
    }))
  })
})
