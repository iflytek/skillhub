import { describe, expect, it } from 'vitest'
import { isSkillDetailQueriesEnabled } from './skill-detail-query'

describe('isSkillDetailQueriesEnabled', () => {
  it('keeps detail queries active before deletion', () => {
    expect(isSkillDetailQueriesEnabled(false)).toBe(true)
  })

  it('stops detail queries immediately after deletion succeeds', () => {
    expect(isSkillDetailQueriesEnabled(true)).toBe(false)
  })
})
