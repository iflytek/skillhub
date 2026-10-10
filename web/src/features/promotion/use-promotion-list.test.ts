import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PagedResponse, PromotionTask } from '@/api/types'

const mocks = vi.hoisted(() => ({
  invalidateQueries: vi.fn(),
  useMutation: vi.fn(),
  useQuery: vi.fn(),
  promotionList: vi.fn(),
  promotionGet: vi.fn(),
  promotionApprove: vi.fn(),
  promotionReject: vi.fn(),
}))

vi.mock('@tanstack/react-query', () => ({
  useMutation: mocks.useMutation,
  useQuery: (options: unknown) => mocks.useQuery(options),
  useQueryClient: () => ({ invalidateQueries: mocks.invalidateQueries }),
}))

vi.mock('@/api/client', () => ({
  promotionApi: {
    list: (...args: unknown[]) => mocks.promotionList(...args),
    get: (...args: unknown[]) => mocks.promotionGet(...args),
    approve: (...args: unknown[]) => mocks.promotionApprove(...args),
    reject: (...args: unknown[]) => mocks.promotionReject(...args),
  },
}))

import { usePromotionList } from './use-promotion-list'

const promotion = {
  id: 1,
  sourceSkillId: 10,
  sourceSkillDisplayName: 'Code Review Bot',
  sourceSkillSummary: 'Reviews code changes.',
  sourceNamespace: 'team-ai',
  sourceSkillSlug: 'code-review-bot',
  sourceVersion: '1.0.0',
  sourceVersionFileCount: 3,
  sourceVersionTotalSize: 2048,
  sourceSkillDownloadCount: 7,
  sourceSkillStarCount: 2,
  targetNamespace: 'global',
  targetSkillId: null,
  status: 'PENDING',
  submittedBy: 'owner-1',
  submittedByName: 'Owner One',
  reviewedBy: null,
  reviewedByName: null,
  reviewComment: null,
  submittedAt: '2026-06-18T01:00:00Z',
  reviewedAt: null,
} satisfies PromotionTask

describe('usePromotionList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useQuery.mockImplementation((options: unknown) => options)
    mocks.promotionList.mockResolvedValue({ items: [promotion], total: 1, page: 0, size: 20 })
  })

  it('defaults to the pending queue without history sort params', async () => {
    usePromotionList()
    const options = mocks.useQuery.mock.calls[0]?.[0] as { queryKey: unknown; queryFn: () => Promise<PagedResponse<PromotionTask>> }

    expect(options.queryKey).toEqual(['promotions', {
      status: 'PENDING',
      page: 0,
      size: 20,
      sortBy: undefined,
      sortDirection: undefined,
    }])
    await expect(options.queryFn()).resolves.toEqual({ items: [promotion], total: 1, page: 0, size: 20 })
    expect(mocks.promotionList).toHaveBeenCalledWith({
      status: 'PENDING',
      page: 0,
      size: 20,
      sortBy: undefined,
      sortDirection: undefined,
    })
  })

  it('passes reviewed-time sort params for history queues', async () => {
    usePromotionList({ status: 'APPROVED', sortBy: 'reviewedAt', sortDirection: 'ASC' })
    const options = mocks.useQuery.mock.calls[0]?.[0] as { queryKey: unknown; queryFn: () => Promise<PagedResponse<PromotionTask>> }

    expect(options.queryKey).toEqual(['promotions', {
      status: 'APPROVED',
      page: 0,
      size: 20,
      sortBy: 'reviewedAt',
      sortDirection: 'ASC',
    }])
    await options.queryFn()
    expect(mocks.promotionList).toHaveBeenCalledWith({
      status: 'APPROVED',
      page: 0,
      size: 20,
      sortBy: 'reviewedAt',
      sortDirection: 'ASC',
    })
  })

  it('uses different query keys for opposite history sort directions', () => {
    usePromotionList({ status: 'APPROVED', sortBy: 'reviewedAt', sortDirection: 'ASC' })
    const ascKey = mocks.useQuery.mock.calls[0]?.[0].queryKey

    mocks.useQuery.mockClear()
    usePromotionList({ status: 'APPROVED', sortBy: 'reviewedAt', sortDirection: 'DESC' })
    const descKey = mocks.useQuery.mock.calls[0]?.[0].queryKey

    expect(ascKey).not.toEqual(descKey)
    expect(descKey).toEqual(['promotions', {
      status: 'APPROVED',
      page: 0,
      size: 20,
      sortBy: 'reviewedAt',
      sortDirection: 'DESC',
    }])
  })
})
