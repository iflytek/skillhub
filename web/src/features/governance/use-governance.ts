import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { governanceApi } from '@/api/client'

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
      queryClient.invalidateQueries({ queryKey: ['governance', 'notifications'] })
    },
  })
}
