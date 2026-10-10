import { describe, expect, it } from 'vitest'
import { getGovernanceTotalPages } from './governance-pagination'

describe('getGovernanceTotalPages', () => {
  it('returns at least one page for empty data', () => {
    expect(getGovernanceTotalPages(0, 10)).toBe(1)
  })

  it('rounds up when there are remaining items', () => {
    expect(getGovernanceTotalPages(21, 10)).toBe(3)
  })
})
