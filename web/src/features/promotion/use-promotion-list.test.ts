import { describe, expect, it } from 'vitest'
import * as mod from './use-promotion-list'

/**
 * use-promotion-list.ts exports four hooks (usePromotionList,
 * usePromotionDetail, useApprovePromotion, useRejectPromotion) and
 * re-exports the PromotionTask type. All hooks are thin wrappers around
 * useQuery/useMutation with no exported pure helpers, query-key functions,
 * or data transformations beyond unwrapping the backend page object
 * (which cannot be tested without an API client mock).
 *
 * We verify the export contract so downstream consumers break fast if
 * the module shape changes.
 */
describe('use-promotion-list module exports', () => {
  it('exports usePromotionList as a function', () => {
    expect(mod.usePromotionList).toBeDefined()
    expect(typeof mod.usePromotionList).toBe('function')
  })

  it('exports usePromotionDetail as a function', () => {
    expect(mod.usePromotionDetail).toBeDefined()
    expect(typeof mod.usePromotionDetail).toBe('function')
  })

  it('exports useApprovePromotion as a function', () => {
    expect(mod.useApprovePromotion).toBeDefined()
    expect(typeof mod.useApprovePromotion).toBe('function')
  })

  it('exports useRejectPromotion as a function', () => {
    expect(mod.useRejectPromotion).toBeDefined()
    expect(typeof mod.useRejectPromotion).toBe('function')
  })
})
