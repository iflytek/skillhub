import { describe, expect, it } from 'vitest'
import * as mod from './star-button'

/**
 * star-button.tsx exports the StarButton component. The toggle logic and
 * authentication guard are internal to the component with no exported pure
 * helpers or constants.
 *
 * We verify the export contract so downstream consumers break fast if
 * the module shape changes.
 */
describe('star-button module exports', () => {
  it('exports the StarButton component', () => {
    expect(mod.StarButton).toBeDefined()
    expect(typeof mod.StarButton).toBe('function')
  })
})
