import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { components } from '@/api/generated/schema'
import { collectionApi } from '@/api/client'

type SkillCollectionCreateBody = components['schemas']['SkillCollectionCreateRequest']
type SkillCollectionUpdateBody = components['schemas']['SkillCollectionUpdateRequest']

function invalidateMineAndDetail(queryClient: ReturnType<typeof useQueryClient>, id?: string | number | null) {
  queryClient.invalidateQueries({ queryKey: ['collections', 'mine'] })
  if (id != null && id !== '') {
    queryClient.invalidateQueries({ queryKey: ['collections', String(id)] })
  }
}

export function useMyCollections(params: { page?: number; size?: number } = {}) {
  const page = params.page ?? 0
  const size = params.size ?? 20
  return useQuery({
    queryKey: ['collections', 'mine', page, size],
    queryFn: () => collectionApi.listMine({ page, size }),
  })
}

export function useCollectionDetail(id: string | undefined) {
  return useQuery({
    queryKey: ['collections', id],
    queryFn: () => collectionApi.getById(id!),
    enabled: !!id,
  })
}

export function useCreateCollection() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: SkillCollectionCreateBody) => collectionApi.create(body),
    onSuccess: (data) => {
      invalidateMineAndDetail(queryClient, data.id)
    },
  })
}

export function useUpdateCollection() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (variables: { id: string; body: SkillCollectionUpdateBody }) => collectionApi.updateMetadata(variables.id, variables.body),
    onSuccess: (_data, variables) => {
      invalidateMineAndDetail(queryClient, variables.id)
    },
  })
}

export function useSetCollectionVisibility() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (variables: { id: string; visibility: 'PUBLIC' | 'PRIVATE' }) =>
      collectionApi.setVisibility(variables.id, variables.visibility),
    onSuccess: (_data, variables) => {
      invalidateMineAndDetail(queryClient, variables.id)
    },
  })
}

export function useDeleteCollection() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => collectionApi.deleteCollection(id),
    onSuccess: (_data, id) => {
      invalidateMineAndDetail(queryClient, id)
    },
  })
}
