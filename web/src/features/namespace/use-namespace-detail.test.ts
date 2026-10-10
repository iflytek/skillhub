import { describe, expect, it } from 'vitest'
import * as mod from './use-namespace-detail'

/**
 * use-namespace-detail.ts is a feature-local re-export of the
 * useNamespaceDetail hook from the shared query layer. There is no
 * custom logic or transformation to test.
 *
 * We verify the re-export contract so import paths used by namespace
 * detail screens break fast if the module shape changes.
 */
describe('use-namespace-detail re-export', () => {
  it('re-exports useNamespaceDetail as a function', () => {
    expect(mod.useNamespaceDetail).toBeDefined()
    expect(typeof mod.useNamespaceDetail).toBe('function')
  })
})
