import { describe, expect, it } from 'vitest'
import * as mod from './copy-button'

/**
 * CopyButton is a React component that copies text to the clipboard.
 * All logic uses React state and navigator.clipboard API.
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('copy-button module exports', () => {
  it('exports the CopyButton component', () => {
    expect(mod.CopyButton).toBeTypeOf('function')
  })
})
