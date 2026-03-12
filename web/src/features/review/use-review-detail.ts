import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchJson, getCsrfHeaders } from '@/api/client'
import type { ReviewTask } from './use-review-list'

async function getReviewDetail(taskId: number): Promise<ReviewTask> {
  return fetchJson<ReviewTask>(`/api/v1/reviews/${taskId}`)
}

async function approveReview(taskId: number, comment?: string): Promise<void> {
  await fetchJson<void>(`/api/v1/reviews/${taskId}/approve`, {
    method: 'POST',
    headers: getCsrfHeaders({
      'Content-Type': 'application/json',
    }),
    body: JSON.stringify({ comment }),
  })
}

async function rejectReview(taskId: number, comment: string): Promise<void> {
  await fetchJson<void>(`/api/v1/reviews/${taskId}/reject`, {
    method: 'POST',
    headers: getCsrfHeaders({
      'Content-Type': 'application/json',
    }),
    body: JSON.stringify({ comment }),
  })
}

export function useReviewDetail(taskId: number) {
  return useQuery({
    queryKey: ['reviews', taskId],
    queryFn: () => getReviewDetail(taskId),
    enabled: !!taskId,
  })
}

export function useApproveReview() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ taskId, comment }: { taskId: number; comment?: string }) =>
      approveReview(taskId, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
    },
  })
}

export function useRejectReview() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ taskId, comment }: { taskId: number; comment: string }) =>
      rejectReview(taskId, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
    },
  })
}
