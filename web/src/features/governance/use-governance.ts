import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { governanceApi } from '@/api/client'

/**
 * Governance query and mutation hooks shared by dashboard moderation pages.
 */
export function useGovernanceSummary() {
  return useQuery({
    queryKey: ['governance', 'summary'],
    queryFn: () => governanceApi.getSummary(),
  })
}

export function useGovernanceInbox(type?: string) {
  return useQuery({
    queryKey: ['governance', 'inbox', type ?? 'ALL'],
    queryFn: async () => {
      const page = await governanceApi.getInbox({ type })
      return page.items
    },
  })
}

export function useGovernanceActivity() {
  return useQuery({
    queryKey: ['governance', 'activity'],
    queryFn: async () => {
      const page = await governanceApi.getActivity({})
      return page.items
    },
  })
}

export function useGovernanceNotifications() {
  return useQuery({
    queryKey: ['governance', 'notifications'],
    queryFn: () => governanceApi.getNotifications(),
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
    },
  })
}

export function useRebuildSearchIndex() {
  return useMutation({
    mutationFn: () => governanceApi.rebuildSearchIndex(),
  })
}
