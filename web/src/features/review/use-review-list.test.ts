import { afterEach, describe, expect, it, vi } from 'vitest'

afterEach(() => {
  vi.unstubAllGlobals()
})

// useReviewList is a useQuery wrapper. The internal getReviewList function computes
// totalElements and totalPages from the API response. While getReviewList itself is
// not exported, its behaviour can be validated indirectly through the reviewApi
// integration surface, similar to the pattern used in profile-review.test.ts.

describe('use-review-list exports', () => {
  it('exports useReviewList', async () => {
    const mod = await import('./use-review-list')
    expect(mod.useReviewList).toBeDefined()
    expect(typeof mod.useReviewList).toBe('function')
  })
})

describe('reviewApi.list response mapping', () => {
  it('reviewApi exports the list method', async () => {
    const { reviewApi } = await import('@/api/client')
    expect(reviewApi.list).toBeDefined()
    expect(typeof reviewApi.list).toBe('function')
  })

  it('returns paginated skill review list from the API', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        code: 0,
        msg: 'response.success',
        data: {
          items: [],
          total: 15,
          page: 0,
          size: 20,
        },
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ))
    vi.stubGlobal('document', { cookie: '' })

    const { reviewApi } = await import('@/api/client')
    const response = await reviewApi.list({ status: 'PENDING', page: 0, size: 20 })

    expect(response.total).toBe(15)
    expect(response.items).toEqual([])
  })
})
