import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { reportApi } from '@/api/client'

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
    mutationFn: ({ id, comment }: { id: number; comment?: string }) => reportApi.resolveSkillReport(id, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skill-reports'] })
    },
  })
}

export function useDismissSkillReport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) => reportApi.dismissSkillReport(id, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skill-reports'] })
    },
  })
}
