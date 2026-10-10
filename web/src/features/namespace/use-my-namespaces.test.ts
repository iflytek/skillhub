import { describe, expect, it } from 'vitest'
import * as mod from './use-my-namespaces'

/**
 * use-my-namespaces.ts is a feature-local re-export of the
 * useMyNamespaces hook from the shared query layer. There is no custom
 * logic, transformation, or query-key function to test.
 *
 * We verify the re-export contract so import paths used by namespace
 * dashboard screens break fast if the module shape changes.
 */
describe('use-my-namespaces re-export', () => {
  it('re-exports useMyNamespaces as a function', () => {
    expect(mod.useMyNamespaces).toBeDefined()
    expect(typeof mod.useMyNamespaces).toBe('function')
  })
})
