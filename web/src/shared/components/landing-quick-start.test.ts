import { describe, expect, it } from 'vitest'
import * as mod from './landing-quick-start'

/**
 * LandingQuickStartSection is a React component that renders a tabbed quick-start
 * section with agent/human tabs and copy-to-clipboard commands.
 * All logic depends on React state and i18next hooks.
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('landing-quick-start module exports', () => {
  it('exports the LandingQuickStartSection component', () => {
    expect(mod.LandingQuickStartSection).toBeTypeOf('function')
  })
})
