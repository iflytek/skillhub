import { describe, expect, it } from 'vitest'
import * as mod from './file-tree'

/**
 * file-tree.tsx exports a single React component (FileTree).
 * The component delegates tree construction to buildFileTree (tested separately)
 * and rendering to FileTreeNodeComponent. There are no exported pure helpers
 * or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('file-tree module exports', () => {
  it('exports the FileTree component', () => {
    expect(mod.FileTree).toBeDefined()
    expect(typeof mod.FileTree).toBe('function')
  })
})
