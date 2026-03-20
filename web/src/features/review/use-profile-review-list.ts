import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/client'

export function useProfileReviewList(status: string, page = 0, size = 20, enabled = true) {
  return useQuery({
    queryKey: ['profile-reviews', status, page, size],
    queryFn: () => adminApi.getProfileReviews({ status, page, size }),
    enabled,
  })
}

export function useApproveProfileReview() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => adminApi.approveProfileReview(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile-reviews'] })
    },
  })
}

export function useRejectProfileReview() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: number; comment: string }) =>
      adminApi.rejectProfileReview(id, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile-reviews'] })
    },
  })
}
