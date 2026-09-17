import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useQuery } from '@tanstack/react-query'

const mocks = vi.hoisted(() => ({
  mutationOptions: [] as Array<{ meta?: Record<string, unknown>; onSuccess?: () => void }>,
  invalidate: vi.fn(),
}))

vi.mock('@tanstack/react-query', () => ({
  keepPreviousData: Symbol('keepPreviousData'),
  useQuery: vi.fn(),
  useMutation: vi.fn((options: { meta?: Record<string, unknown> }) => {
    mocks.mutationOptions.push(options)
    return options
  }),
  useQueryClient: () => ({ invalidateQueries: mocks.invalidate }),
}))

import {
  useCreateSuite,
  useCreateSuiteVersion,
  useUpdateSuiteDraft,
  useMySuiteWorkspace,
  useConfirmSuiteBundle,
} from './use-suite-queries'

describe('Suite editor mutations', () => {
  beforeEach(() => {
    mocks.mutationOptions.length = 0
  })

  it('lets the editor own save errors so one failed request produces one toast', () => {
    useCreateSuite()
    useCreateSuiteVersion(7)
    useUpdateSuiteDraft(7, 70)

    expect(mocks.mutationOptions).toHaveLength(3)
    for (const options of mocks.mutationOptions) {
      expect(options.meta).toEqual({ skipGlobalErrorHandler: true })
    }
  })
})

describe('Suite workspace query budget', () => {
  it('invalidates the cached workbench after confirmation so an immediate return includes the new creation', () => {
    mocks.invalidate.mockClear()
    useConfirmSuiteBundle()
    mocks.mutationOptions[mocks.mutationOptions.length - 1]?.onSuccess?.()
    expect(mocks.invalidate).toHaveBeenCalledWith({ queryKey: ['suites', 'workspace'] })
  })
  it('uses one paginated query and polls only changing workspaces', () => {
    useMySuiteWorkspace('care', 'ATTENTION', 2, 12)
    const calls = vi.mocked(useQuery).mock.calls
    const options = calls[calls.length - 1]?.[0]
    expect(options?.queryKey).toEqual(['suites', 'workspace', 'care', 'ATTENTION', 2, 12])
    expect(options?.staleTime).toBe(10_000)
    const interval = options?.refetchInterval as (query: { state: { data: { hasChangingOperations: boolean } } }) => number | false
    expect(interval({ state: { data: { hasChangingOperations: false } } })).toBe(false)
    expect(interval({ state: { data: { hasChangingOperations: true } } })).toBe(5_000)
  })
})
