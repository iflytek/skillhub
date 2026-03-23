import { describe, expect, it } from 'vitest'

// The use-review-detail module exports thin useQuery / useMutation wrappers
// (useReviewDetail, useReviewSkillDetail, useApproveReview, useRejectReview).
// Internal helper functions (getReviewDetail, getReviewSkillDetail, approveReview,
// rejectReview) are not exported and cannot be tested directly.
//
// Verifying that each public hook is exported and is a callable function serves as
// a smoke check that the module and its dependency graph resolve correctly.

describe('use-review-detail exports', () => {
  it('exports useReviewDetail', async () => {
    const mod = await import('./use-review-detail')
    expect(mod.useReviewDetail).toBeDefined()
    expect(typeof mod.useReviewDetail).toBe('function')
  })

  it('exports useReviewSkillDetail', async () => {
    const mod = await import('./use-review-detail')
    expect(mod.useReviewSkillDetail).toBeDefined()
    expect(typeof mod.useReviewSkillDetail).toBe('function')
  })

  it('exports useApproveReview', async () => {
    const mod = await import('./use-review-detail')
    expect(mod.useApproveReview).toBeDefined()
    expect(typeof mod.useApproveReview).toBe('function')
  })

  it('exports useRejectReview', async () => {
    const mod = await import('./use-review-detail')
    expect(mod.useRejectReview).toBeDefined()
    expect(typeof mod.useRejectReview).toBe('function')
  })
})
