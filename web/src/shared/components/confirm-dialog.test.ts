import { describe, expect, it } from 'vitest'
import * as mod from './confirm-dialog'

/**
 * ConfirmDialog is a React component that wraps Radix Dialog with confirm/cancel buttons.
 * All logic depends on React hooks (useState, useTranslation) and Dialog UI primitives.
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('confirm-dialog module exports', () => {
  it('exports the ConfirmDialog component', () => {
    expect(mod.ConfirmDialog).toBeTypeOf('function')
  })
})
