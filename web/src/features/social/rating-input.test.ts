import { describe, expect, it } from 'vitest'
import * as mod from './rating-input'

/**
 * rating-input.tsx exports the RatingInput component. All rating logic
 * (hover state, star fill calculation, authentication guard) lives inside
 * the component with no exported pure helpers or constants.
 *
 * We verify the export contract so downstream consumers break fast if
 * the module shape changes.
 */
describe('rating-input module exports', () => {
  it('exports the RatingInput component', () => {
    expect(mod.RatingInput).toBeDefined()
    expect(typeof mod.RatingInput).toBe('function')
  })
})
