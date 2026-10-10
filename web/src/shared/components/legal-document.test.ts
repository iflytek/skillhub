import { describe, expect, it } from 'vitest'
import * as mod from './legal-document'

/**
 * LegalDocument is a React component that renders structured legal content
 * with eyebrow, title, summary, note, and sections (paragraphs + bullets).
 * It is purely presentational JSX with no exported helpers or constants.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('legal-document module exports', () => {
  it('exports the LegalDocument component', () => {
    expect(mod.LegalDocument).toBeTypeOf('function')
  })
})
