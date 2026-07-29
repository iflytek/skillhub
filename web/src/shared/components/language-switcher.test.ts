import { describe, expect, it } from 'vitest'
import * as mod from './language-switcher'

/**
 * LanguageSwitcher renders an in-tree locale menu (no Radix portal) driven by i18next.
 * There are no exported pure helpers; verify the export contract only.
 */
describe('language-switcher module exports', () => {
  it('exports the LanguageSwitcher component', () => {
    expect(mod.LanguageSwitcher).toBeTypeOf('function')
  })
})
