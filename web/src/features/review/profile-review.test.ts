import { afterEach, describe, expect, it, vi } from 'vitest'

afterEach(() => {
  vi.unstubAllGlobals()
})

// Verify the hook module exports the expected functions
describe('use-profile-review-list exports', () => {
  it('exports useProfileReviewList', async () => {
    const mod = await import('./use-profile-review-list')
    expect(mod.useProfileReviewList).toBeDefined()
    expect(typeof mod.useProfileReviewList).toBe('function')
  })

  it('exports useApproveProfileReview', async () => {
    const mod = await import('./use-profile-review-list')
    expect(mod.useApproveProfileReview).toBeDefined()
    expect(typeof mod.useApproveProfileReview).toBe('function')
  })

  it('exports useRejectProfileReview', async () => {
    const mod = await import('./use-profile-review-list')
    expect(mod.useRejectProfileReview).toBeDefined()
    expect(typeof mod.useRejectProfileReview).toBe('function')
  })
})

// Verify adminApi has the profile review methods
describe('adminApi profile review methods', () => {
  it('adminApi exports getProfileReviews', async () => {
    const { adminApi } = await import('@/api/client')
    expect(adminApi.getProfileReviews).toBeDefined()
    expect(typeof adminApi.getProfileReviews).toBe('function')
  })

  it('adminApi exports approveProfileReview', async () => {
    const { adminApi } = await import('@/api/client')
    expect(adminApi.approveProfileReview).toBeDefined()
    expect(typeof adminApi.approveProfileReview).toBe('function')
  })

  it('adminApi exports rejectProfileReview', async () => {
    const { adminApi } = await import('@/api/client')
    expect(adminApi.rejectProfileReview).toBeDefined()
    expect(typeof adminApi.rejectProfileReview).toBe('function')
  })

  it('maps page response totals for profile review lists', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        code: 0,
        msg: 'response.success',
        data: {
          items: [],
          total: 7,
          page: 0,
          size: 20,
        },
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ))
    vi.stubGlobal('document', { cookie: '' })

    const { adminApi } = await import('@/api/client')
    const response = await adminApi.getProfileReviews({ status: 'PENDING', page: 0, size: 20 })

    expect(response.totalElements).toBe(7)
    expect(response.totalPages).toBe(1)
  })

  it('passes sortDirection through profile and skill review list requests', async () => {
    const createResponse = () => new Response(JSON.stringify({
      code: 0,
      msg: 'response.success',
      data: {
        items: [],
        total: 0,
        page: 0,
        size: 20,
      },
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(createResponse()))
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('document', { cookie: '' })

    const { adminApi, reviewApi } = await import('@/api/client')

    await adminApi.getProfileReviews({ status: 'APPROVED', page: 1, size: 10, sortDirection: 'ASC' })
    await reviewApi.list({ status: 'REJECTED', namespaceId: 9, page: 2, size: 5, sortDirection: 'ASC' })

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/admin/profile-reviews?status=APPROVED&page=1&size=10&sortDirection=ASC',
      expect.objectContaining({
        headers: expect.any(Headers),
      }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/web/reviews?status=REJECTED&namespaceId=9&page=2&size=5&sortDirection=ASC',
      expect.objectContaining({
        headers: expect.any(Headers),
      }),
    )
  })
})
