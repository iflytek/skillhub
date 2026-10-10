import { describe, expect, it } from 'vitest'
import * as mod from './code-renderer'

/**
 * code-renderer.tsx exports a single React component (CodeRenderer).
 * The file contains two useful pure helpers (escapeHtml and treeToHtml),
 * but they are module-private and cannot be imported directly.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('code-renderer module exports', () => {
  it('exports the CodeRenderer component', () => {
    expect(mod.CodeRenderer).toBeDefined()
    expect(typeof mod.CodeRenderer).toBe('function')
  })
})
