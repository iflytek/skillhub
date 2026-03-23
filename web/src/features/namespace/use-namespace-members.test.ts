import { describe, expect, it } from 'vitest'
import * as mod from './use-namespace-members'

/**
 * use-namespace-members.ts is a feature-local re-export of the
 * useNamespaceMembers hook from the shared query layer. There is no
 * custom logic or transformation to test.
 *
 * We verify the re-export contract so import paths used by namespace
 * member screens break fast if the module shape changes.
 */
describe('use-namespace-members re-export', () => {
  it('re-exports useNamespaceMembers as a function', () => {
    expect(mod.useNamespaceMembers).toBeDefined()
    expect(typeof mod.useNamespaceMembers).toBe('function')
  })
})
