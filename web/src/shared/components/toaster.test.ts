import { describe, expect, it } from 'vitest'
import * as mod from './toaster'

/**
 * Toaster is a React component that wraps Sonner's Toaster with project-specific
 * positioning and styling. It uses CENTER_TOASTER_ID from @/shared/lib/toast.
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('toaster module exports', () => {
  it('exports the Toaster component', () => {
    expect(mod.Toaster).toBeTypeOf('function')
  })
})
