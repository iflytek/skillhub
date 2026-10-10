import { describe, expect, it, vi } from 'vitest'

/**
 * use-security-audit.ts exports a single hook `useSecurityAudits` that wraps
 * `useQuery` from TanStack Query.  The module-private `fetchSecurityAudits`
 * function handles the API call and 404-to-empty-array fallback.
 *
 * Since the hook tightly couples to `useQuery` and `fetchJson`, we test
 * the observable configuration: the query key structure and the `enabled`
 * guard logic.
 */

// Capture the options passed to useQuery so we can assert on them.
let capturedOptions: Record<string, unknown> | undefined
let capturedMutationOptions: Record<string, unknown> | undefined
const apiMocks = vi.hoisted(() => ({
  fetchJson: vi.fn(),
  getCsrfHeaders: vi.fn(() => ({ 'X-XSRF-TOKEN': 'csrf-token' })),
}))

vi.mock('@tanstack/react-query', () => ({
  useQuery: (options: Record<string, unknown>) => {
    capturedOptions = options
    return { data: undefined, isLoading: false }
  },
  useMutation: (options: Record<string, unknown>) => {
    capturedMutationOptions = options
    return { mutate: vi.fn(), isPending: false }
  },
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}))

// Mock fetchJson to avoid actual network calls.  The hook's queryFn
// calls the private fetchSecurityAudits which uses fetchJson internally.
vi.mock('@/api/client', () => ({
  ApiError: class ApiError extends Error {
    status: number
    constructor(message: string, status: number) {
      super(message)
      this.status = status
    }
  },
  fetchJson: apiMocks.fetchJson,
  getCsrfHeaders: apiMocks.getCsrfHeaders,
}))

// Dynamic import to ensure mocks are established first.
const { useRetrySecurityScan, useSecurityAudits } = await import('./use-security-audit')

describe('useSecurityAudits', () => {
  it('uses the correct query key structure', () => {
    useSecurityAudits(42, 100)

    expect(capturedOptions?.queryKey).toEqual(['security-audits', 42, 100])
  })

  it('is enabled when both skillId and versionId are provided', () => {
    useSecurityAudits(1, 2)

    expect(capturedOptions?.enabled).toBe(true)
  })

  it('is disabled when skillId is undefined', () => {
    useSecurityAudits(undefined, 2)

    expect(capturedOptions?.enabled).toBe(false)
  })

  it('is disabled when versionId is undefined', () => {
    useSecurityAudits(1, undefined)

    expect(capturedOptions?.enabled).toBe(false)
  })

  it('is disabled when both skillId and versionId are undefined', () => {
    useSecurityAudits(undefined, undefined)

    expect(capturedOptions?.enabled).toBe(false)
  })

  it('configures a 30-second stale time', () => {
    useSecurityAudits(1, 2)

    expect(capturedOptions?.staleTime).toBe(30_000)
  })

  it('disables retry to avoid retrying on expected 404 responses', () => {
    useSecurityAudits(1, 2)

    expect(capturedOptions?.retry).toBe(false)
  })

  it('sends the CSRF header when retrying a security scan', async () => {
    apiMocks.fetchJson.mockResolvedValueOnce({ status: 'SCANNING' })
    useRetrySecurityScan(42, 100)

    await (capturedMutationOptions?.mutationFn as () => Promise<unknown>)()

    expect(apiMocks.getCsrfHeaders).toHaveBeenCalledOnce()
    expect(apiMocks.fetchJson).toHaveBeenCalledWith(
      '/api/v1/skills/42/versions/100/security-audit/retry',
      { method: 'POST', headers: { 'X-XSRF-TOKEN': 'csrf-token' } },
    )
  })
})
