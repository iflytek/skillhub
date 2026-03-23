import { describe, expect, it } from 'vitest'
import * as mod from './empty-state'

/**
 * EmptyState is a React component that renders a centered placeholder with
 * title, optional description, and optional action slot.
 * It is purely presentational JSX with no exported helpers or constants.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('empty-state module exports', () => {
  it('exports the EmptyState component', () => {
    expect(mod.EmptyState).toBeTypeOf('function')
  })
})
