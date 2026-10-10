import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { governanceApi } from '@/api/client'

export const GOVERNANCE_PAGE_SIZE = 10

/**
 * Governance query and mutation hooks shared by dashboard moderation pages.
 */
export function useGovernanceSummary() {
  return useQuery({
    queryKey: ['governance', 'summary'],
    queryFn: () => governanceApi.getSummary(),
  })
}

export function useGovernanceInbox(type?: string, page = 0, size = GOVERNANCE_PAGE_SIZE) {
  return useQuery({
    queryKey: ['governance', 'inbox', type ?? 'ALL', page, size],
    queryFn: () => governanceApi.getInbox({ type, page, size }),
  })
}

export function useGovernanceActivity(page = 0, size = GOVERNANCE_PAGE_SIZE) {
  return useQuery({
    queryKey: ['governance', 'activity', page, size],
    queryFn: () => governanceApi.getActivity({ page, size }),
  })
}

export function useGovernanceNotifications(page = 0, size = GOVERNANCE_PAGE_SIZE) {
  return useQuery({
    queryKey: ['governance', 'notifications', page, size],
    queryFn: () => governanceApi.getNotifications({ page, size }),
  })
}

export function useMarkGovernanceNotificationRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => governanceApi.markNotificationRead(id),
    onSuccess: () => {
      // Notifications affect the global governance badge state, so keep the whole notification list
      // fresh after marking one item as read.
      queryClient.invalidateQueries({ queryKey: ['governance', 'notifications'] })
      queryClient.invalidateQueries({ queryKey: ['governance', 'summary'] })
    },
  })
}

export function useRebuildSearchIndex() {
  return useMutation({
    mutationFn: () => governanceApi.rebuildSearchIndex(),
  })
}
