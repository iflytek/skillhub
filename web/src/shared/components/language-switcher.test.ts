import { describe, expect, it } from 'vitest'
import * as mod from './language-switcher'

/**
 * LanguageSwitcher is a React component that renders a dropdown to switch
 * between Chinese and English using i18next.
 * All logic depends on i18next hooks and Radix DropdownMenu primitives.
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('language-switcher module exports', () => {
  it('exports the LanguageSwitcher component', () => {
    expect(mod.LanguageSwitcher).toBeTypeOf('function')
  })
})
