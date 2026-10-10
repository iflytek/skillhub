import { describe, expect, it } from 'vitest'
import * as accountMerge from './use-account-merge'

/**
 * use-account-merge exports three thin useMutation hooks (useInitiateAccountMerge,
 * useVerifyAccountMerge, useConfirmAccountMerge) that delegate directly to accountApi.
 * There are no exported pure functions, constants, or data transformations to unit-test.
 *
 * This file verifies the public API surface so that accidental export removals are caught.
 */
describe('use-account-merge module exports', () => {
  it('exports useInitiateAccountMerge hook', () => {
    expect(accountMerge.useInitiateAccountMerge).toBeTypeOf('function')
  })

  it('exports useVerifyAccountMerge hook', () => {
    expect(accountMerge.useVerifyAccountMerge).toBeTypeOf('function')
  })

  it('exports useConfirmAccountMerge hook', () => {
    expect(accountMerge.useConfirmAccountMerge).toBeTypeOf('function')
  })
})
