import { describe, expect, it } from 'vitest'
import * as mod from './file-tree-node'

/**
 * file-tree-node.tsx exports the FileTreeNodeComponent React component.
 * It contains two module-private helpers (formatFileSize and getIconComponent)
 * that are pure functions but cannot be imported for direct testing.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('file-tree-node module exports', () => {
  it('exports the FileTreeNodeComponent component', () => {
    expect(mod.FileTreeNodeComponent).toBeDefined()
    expect(typeof mod.FileTreeNodeComponent).toBe('function')
  })
})
