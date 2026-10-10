import { useMutation, useQueryClient } from '@tanstack/react-query'
import { labelApi } from '@/api/client'
import type { AdminLabelInput } from '@/api/types'
import { useAdminLabelDefinitions } from '@/shared/hooks/use-label-queries'

export { useAdminLabelDefinitions }

export function useCreateAdminLabel() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: AdminLabelInput) => labelApi.createAdminDefinition(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['labels'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}

export function useUpdateAdminLabel() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ slug, request }: { slug: string; request: Omit<AdminLabelInput, 'slug'> }) =>
      labelApi.updateAdminDefinition(slug, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['labels'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}

export function useDeleteAdminLabel() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (slug: string) => labelApi.deleteAdminDefinition(slug),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['labels'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}

export function useUpdateAdminLabelSortOrder() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (items: Array<{ slug: string; sortOrder: number }>) => labelApi.updateAdminSortOrder(items),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['labels'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}
