import { describe, expect, it } from 'vitest'
import * as mod from './add-namespace-member-dialog'

/**
 * add-namespace-member-dialog.tsx exports a single React component
 * (AddNamespaceMemberDialog). The ROLE_OPTIONS constant and validation
 * helpers are module-private, so we verify the export contract and
 * component function shape to catch accidental breakage.
 */
describe('add-namespace-member-dialog module exports', () => {
  it('exports the AddNamespaceMemberDialog component', () => {
    expect(mod.AddNamespaceMemberDialog).toBeDefined()
    expect(typeof mod.AddNamespaceMemberDialog).toBe('function')
  })
})
