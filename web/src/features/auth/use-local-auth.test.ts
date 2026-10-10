import { describe, expect, it } from 'vitest'
import * as localAuth from './use-local-auth'

/**
 * use-local-auth exports two thin useMutation hooks (useLocalLogin, useLocalRegister)
 * that delegate directly to authApi. Both update the ['auth', 'me'] query cache on
 * success. There are no exported pure functions, constants, or data transformations
 * to unit-test.
 *
 * This file verifies the public API surface so that accidental export removals are caught.
 */
describe('use-local-auth module exports', () => {
  it('exports useLocalLogin hook', () => {
    expect(localAuth.useLocalLogin).toBeTypeOf('function')
  })

  it('exports useLocalRegister hook', () => {
    expect(localAuth.useLocalRegister).toBeTypeOf('function')
  })
})
