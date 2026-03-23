import { describe, expect, it } from 'vitest'
import * as authMethods from './use-auth-methods'

/**
 * use-auth-methods is a thin useQuery wrapper around authApi.getMethods.
 * The query key includes the returnTo parameter for proper cache isolation.
 * There are no exported pure functions or data transformations to unit-test.
 *
 * This file verifies the public API surface so that accidental export removals are caught.
 */
describe('use-auth-methods module exports', () => {
  it('exports useAuthMethods hook', () => {
    expect(authMethods.useAuthMethods).toBeTypeOf('function')
  })
})
