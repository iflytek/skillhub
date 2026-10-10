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

  it('supports a five-item preview before the ellipsis entry', () => {
    expect(limitPreviewItems(['a', 'b', 'c', 'd', 'e', 'f'], 5)).toEqual({
      items: ['a', 'b', 'c', 'd', 'e'],
      hasMore: true,
      remainingCount: 1,
    })
  })

  it('returns an empty preview instead of throwing when input is not an array', () => {
    expect(limitPreviewItems({ items: ['a', 'b'] } as never, 3)).toEqual({
      items: [],
      hasMore: false,
      remainingCount: 0,
    })
  })
})
