import { describe, expect, it } from 'vitest'
import * as mod from './create-namespace-dialog'

/**
 * create-namespace-dialog.tsx exports the CreateNamespaceDialog component.
 * The validation helper (buildFieldErrors), slug constants (SLUG_PATTERN,
 * RESERVED_SLUGS, length limits), and FieldErrors type are all
 * module-private. We verify the public export contract here.
 */
describe('create-namespace-dialog module exports', () => {
  it('exports the CreateNamespaceDialog component', () => {
    expect(mod.CreateNamespaceDialog).toBeDefined()
    expect(typeof mod.CreateNamespaceDialog).toBe('function')
  })
})
