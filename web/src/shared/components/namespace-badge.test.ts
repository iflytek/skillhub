import { describe, expect, it } from 'vitest'
import * as mod from './namespace-badge'

/**
 * NamespaceBadge is a React component that renders a styled badge for
 * GLOBAL or TEAM namespace types using cn() for conditional class merging.
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('namespace-badge module exports', () => {
  it('exports the NamespaceBadge component', () => {
    expect(mod.NamespaceBadge).toBeTypeOf('function')
  })
})
