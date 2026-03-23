import { describe, expect, it } from 'vitest'
import * as mod from './search-bar'

/**
 * search-bar.tsx exports the SearchBar component. The component delegates
 * its max-length constraint to the shared MAX_SEARCH_QUERY_LENGTH constant
 * (tested in search-query.test.ts). Controlled/uncontrolled mode logic and
 * submit/clear handlers are component-internal with no exported helpers.
 *
 * We verify the export contract so downstream consumers break fast if
 * the module shape changes.
 */
describe('search-bar module exports', () => {
  it('exports the SearchBar component', () => {
    expect(mod.SearchBar).toBeDefined()
    expect(typeof mod.SearchBar).toBe('function')
  })
})
