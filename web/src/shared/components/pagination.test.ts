import { describe, expect, it } from 'vitest'
import * as mod from './pagination'

/**
 * Pagination is a React component that renders prev/next buttons with a
 * page counter display. All logic depends on useTranslation and callback props.
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('pagination module exports', () => {
  it('exports the Pagination component', () => {
    expect(mod.Pagination).toBeTypeOf('function')
  })
})
