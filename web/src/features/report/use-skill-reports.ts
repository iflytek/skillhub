import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { reportApi } from '@/api/client'
import type { ReportDisposition } from '@/api/types'

/**
 * Loads reported skills for the requested moderation status.
 */
export function useSkillReports(status: string) {
  return useQuery({
    queryKey: ['skill-reports', status],
    queryFn: async () => {
      const page = await reportApi.listSkillReports({ status })
      return page.items
    },
  })
}

/**
 * Submits a report for the current skill detail page.
 */
export function useSubmitSkillReport(namespace: string, slug: string) {
  return useMutation({
    mutationFn: (request: { reason: string; details?: string }) => reportApi.submitSkillReport(namespace, slug, request),
  })
}

/**
 * Resolves a report and refreshes both report lists and governance widgets that
 * derive unread or pending counts from the same backend sources.
 */
export function useResolveSkillReport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment, disposition }: { id: number; comment?: string; disposition?: ReportDisposition }) =>
      reportApi.resolveSkillReport(id, comment, disposition),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skill-reports'] })
      queryClient.invalidateQueries({ queryKey: ['governance'] })
    },
  })
}

/**
 * Dismisses a report without taking the heavier resolution path.
 */
export function useDismissSkillReport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) => reportApi.dismissSkillReport(id, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skill-reports'] })
      queryClient.invalidateQueries({ queryKey: ['governance'] })
    },
  })
}
