import { describe, expect, it } from 'vitest'
import { GOVERNANCE_PAGE_SIZE } from './use-governance'

describe('GOVERNANCE_PAGE_SIZE', () => {
  it('defaults to 10 items per page', () => {
    expect(GOVERNANCE_PAGE_SIZE).toBe(10)
  })

  it('is a positive integer', () => {
    expect(Number.isInteger(GOVERNANCE_PAGE_SIZE)).toBe(true)
    expect(GOVERNANCE_PAGE_SIZE).toBeGreaterThan(0)
  })
})

// The hook exports (useGovernanceSummary, useGovernanceInbox, useGovernanceActivity,
// useGovernanceNotifications, useMarkGovernanceNotificationRead, useRebuildSearchIndex)
// are thin useQuery / useMutation wrappers with no custom data transformation logic.
// Testing them would only verify TanStack Query internals, so they are intentionally
// skipped in favour of integration or E2E coverage.
