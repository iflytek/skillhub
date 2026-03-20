import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { reviewApi } from '@/api/client'
import type { ReviewSkillDetail, ReviewTask } from '@/api/types'

/**
 * Fetches one review task for governance detail views.
 */
async function getReviewDetail(taskId: number): Promise<ReviewTask> {
  return reviewApi.get(taskId)
}

async function getReviewSkillDetail(taskId: number): Promise<ReviewSkillDetail> {
  return reviewApi.getSkillDetail(taskId)
}

/**
 * Approves the current review task.
 */
async function approveReview(taskId: number, comment?: string): Promise<void> {
  await reviewApi.approve(taskId, comment)
}

/**
 * Rejects the current review task. A comment is required here because the UI
 * treats rejection as an explicit feedback action rather than a silent deny.
 */
async function rejectReview(taskId: number, comment: string): Promise<void> {
  await reviewApi.reject(taskId, comment)
}

/**
 * Exposes the review detail query keyed by task id.
 */
export function useReviewDetail(taskId: number) {
  return useQuery({
    queryKey: ['reviews', taskId],
    queryFn: () => getReviewDetail(taskId),
    enabled: !!taskId,
  })
}

export function useReviewSkillDetail(taskId: number) {
  return useQuery({
    queryKey: ['reviews', taskId, 'skill-detail'],
    queryFn: () => getReviewSkillDetail(taskId),
    enabled: !!taskId,
  })
}

/**
 * Approves a review and refreshes both the review queue and the governance
 * dashboard, which reads aggregate review state from separate endpoints.
 */
export function useApproveReview(callbacks?: { onSuccess?: () => void; onError?: (error: Error) => void }) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ taskId, comment }: { taskId: number; comment?: string }) =>
      approveReview(taskId, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
      queryClient.invalidateQueries({ queryKey: ['governance'] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}

/**
 * Rejects a review with the same cache invalidation strategy as approval.
 */
export function useRejectReview(callbacks?: { onSuccess?: () => void; onError?: (error: Error) => void }) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ taskId, comment }: { taskId: number; comment: string }) =>
      rejectReview(taskId, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
      queryClient.invalidateQueries({ queryKey: ['governance'] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}
