import { describe, expect, it } from 'vitest'

// useReviewFile is a thin useQuery wrapper that fetches a single file's content
// from a review-bound skill version. It exports no pure helpers, constants, or
// data transformation logic beyond what TanStack Query provides.
//
// The smoke check below verifies that the module resolves correctly.

describe('use-review-file exports', () => {
  it('exports useReviewFile', async () => {
    const mod = await import('./use-review-file')
    expect(mod.useReviewFile).toBeDefined()
    expect(typeof mod.useReviewFile).toBe('function')
  })
})
