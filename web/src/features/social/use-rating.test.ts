import { describe, expect, it } from 'vitest'
import * as mod from './use-rating'

/**
 * use-rating.ts exports useUserRating and useRate hooks. Both are thin
 * wrappers around useQuery/useMutation with no exported pure helpers,
 * query-key functions, or data transformations.
 *
 * We verify the export contract so downstream consumers break fast if
 * the module shape changes.
 */
describe('use-rating module exports', () => {
  it('exports useUserRating as a function', () => {
    expect(mod.useUserRating).toBeDefined()
    expect(typeof mod.useUserRating).toBe('function')
  })

  it('exports useRate as a function', () => {
    expect(mod.useRate).toBeDefined()
    expect(typeof mod.useRate).toBe('function')
  })
})
