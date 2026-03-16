import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { reportApi } from '@/api/client'
import type { ReportDisposition } from '@/api/types'

export function useSkillReports(status: string) {
  return useQuery({
    queryKey: ['skill-reports', status],
    queryFn: async () => {
      const page = await reportApi.listSkillReports({ status })
      return page.items
    },
  })
}

export function useSubmitSkillReport(namespace: string, slug: string) {
  return useMutation({
    mutationFn: (request: { reason: string; details?: string }) => reportApi.submitSkillReport(namespace, slug, request),
  })
}

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
