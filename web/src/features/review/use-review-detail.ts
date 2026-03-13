import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { reviewApi } from '@/api/client'
import type { ReviewTask } from '@/api/types'

async function getReviewDetail(taskId: number): Promise<ReviewTask> {
  return reviewApi.get(taskId)
}

async function approveReview(taskId: number, comment?: string): Promise<void> {
  await reviewApi.approve(taskId, comment)
}

async function rejectReview(taskId: number, comment: string): Promise<void> {
  await reviewApi.reject(taskId, comment)
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
