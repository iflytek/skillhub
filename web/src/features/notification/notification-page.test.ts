import { describe, expect, it } from 'vitest'
import { getNotificationItems, getNotificationTotal, shouldShowNotificationPagination } from './notification-page'

describe('getNotificationItems', () => {
  it('returns backend page items', () => {
    expect(getNotificationItems({
      items: [
        {
          id: 1,
          category: 'REVIEW',
          eventType: 'REVIEW_SUBMITTED',
          title: 'Review submitted',
          status: 'UNREAD',
          createdAt: '2026-03-20T00:00:00Z',
        },
      ],
      total: 1,
      page: 0,
      size: 20,
    })).toHaveLength(1)
  })

  it('falls back to an empty array when page data is missing', () => {
    expect(getNotificationItems(undefined)).toEqual([])
  })
})

describe('getNotificationTotal', () => {
  it('returns backend total field', () => {
    expect(getNotificationTotal({ items: [], total: 7, page: 0, size: 20 })).toBe(7)
  })

  it('falls back to zero when page data is missing', () => {
    expect(getNotificationTotal(undefined)).toBe(0)
  })
})

describe('shouldShowNotificationPagination', () => {
  it('returns true when there is more than one page', () => {
    expect(shouldShowNotificationPagination(21, 20)).toBe(true)
  })

  it('returns false when the first page already contains all items', () => {
    expect(shouldShowNotificationPagination(20, 20)).toBe(false)
  })
})
