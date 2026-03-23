import { describe, expect, it } from 'vitest'
import * as mod from './quick-start'

/**
 * QuickStartSection is a React component that renders a multi-step quick-start
 * guide with code blocks, copy buttons, and syntax-highlighted code lines.
 * Internal helpers (getAppBaseUrl, CodeLine, CodeBlock, CopyButton) are not exported.
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('quick-start module exports', () => {
  it('exports the QuickStartSection component', () => {
    expect(mod.QuickStartSection).toBeTypeOf('function')
  })
})
