import { describe, expect, it } from 'vitest'
import { limitPreviewItems } from './dashboard-preview'

describe('limitPreviewItems', () => {
  it('returns all items when the list is within the limit', () => {
    expect(limitPreviewItems(['a', 'b'], 3)).toEqual({
      items: ['a', 'b'],
      hasMore: false,
      remainingCount: 0,
    })
  })

  it('returns only the first items and reports the remaining count', () => {
    expect(limitPreviewItems(['a', 'b', 'c', 'd'], 3)).toEqual({
      items: ['a', 'b', 'c'],
      hasMore: true,
      remainingCount: 1,
    })
  })
})
