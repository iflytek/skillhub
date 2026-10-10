import { describe, expect, it } from 'vitest'

/**
 * types.ts only exports TypeScript type aliases and interfaces:
 *
 *   - SecurityVerdict (type alias)
 *   - FindingSeverity (type alias)
 *   - SecurityFinding (interface)
 *   - SecurityAuditRecord (interface)
 *
 * These are erased at compile time and produce no runtime code.
 * There are no runtime-exported constants, functions, or classes to test.
 *
 * This file exists to document the deliberate decision to skip runtime
 * tests for types.ts.  The type correctness is validated by TypeScript
 * compilation and by the tests of modules that consume these types.
 */

describe('types.ts', () => {
  it('exports only TypeScript types with no runtime code to test', () => {
    // Intentionally empty — the types are compile-time only.
    expect(true).toBe(true)
  })
})
