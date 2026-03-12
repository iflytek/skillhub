import { useQuery } from '@tanstack/react-query'
import { fetchJson } from '@/api/client'

export interface ReviewTask {
  id: number
  skillVersionId: number
  skillName: string
  skillSlug: string
  namespace: string
  version: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submittedBy: string
  submittedAt: string
  reviewedBy?: string
  reviewedAt?: string
  comment?: string
}

async function getReviewList(status?: string): Promise<ReviewTask[]> {
  const url = status ? `/api/v1/reviews?status=${status}` : '/api/v1/reviews'
  return fetchJson<ReviewTask[]>(url)
}

export function useReviewList(status?: string) {
  return useQuery({
    queryKey: ['reviews', status],
    queryFn: () => getReviewList(status),
  })
}
