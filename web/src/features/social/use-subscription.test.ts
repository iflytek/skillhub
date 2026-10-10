import { describe, expect, it } from 'vitest'
import * as mod from './use-subscription'

/**
 * use-subscription.ts exports useSubscription and useToggleSubscription hooks.
 * Both are thin wrappers around useQuery/useMutation with no exported pure helpers,
 * query-key functions, or data transformations.
 *
 * We verify the export contract so downstream consumers break fast if
 * the module shape changes.
 */
describe('use-subscription module exports', () => {
  it('exports useSubscription as a function', () => {
    expect(mod.useSubscription).toBeDefined()
    expect(typeof mod.useSubscription).toBe('function')
  })

  it('exports useToggleSubscription as a function', () => {
    expect(mod.useToggleSubscription).toBeDefined()
    expect(typeof mod.useToggleSubscription).toBe('function')
  })
})
