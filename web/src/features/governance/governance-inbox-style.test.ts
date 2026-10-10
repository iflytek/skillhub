import { describe, expect, it } from 'vitest'
import { GOVERNANCE_INBOX_SUBTITLE_CLASS_NAME } from './governance-inbox-style'

describe('GOVERNANCE_INBOX_SUBTITLE_CLASS_NAME', () => {
  it('wraps long text and truncates overflow with an ellipsis', () => {
    expect(GOVERNANCE_INBOX_SUBTITLE_CLASS_NAME).toContain('break-words')
    expect(GOVERNANCE_INBOX_SUBTITLE_CLASS_NAME).toContain('[overflow-wrap:anywhere]')
    expect(GOVERNANCE_INBOX_SUBTITLE_CLASS_NAME).toContain('line-clamp-2')
  })
})
