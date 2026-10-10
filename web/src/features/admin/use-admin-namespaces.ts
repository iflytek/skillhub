import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/client'
import type {
  AdminNamespaceList,
  BatchMemberResponse,
  NamespaceCandidateUser,
  NamespaceMember,
  NamespaceRole,
  PagedResponse,
} from '@/api/types'

export interface AdminNamespacesParams {
  keyword?: string
  status?: string
  type?: string
  page?: number
  size?: number
}

export function useAdminNamespaces(params: AdminNamespacesParams) {
  return useQuery<AdminNamespaceList>({
    queryKey: ['admin', 'namespaces', params],
    queryFn: () => adminApi.getNamespaces(params),
  })
}

export function useAdminNamespace(slug: string) {
  return useQuery({
    queryKey: ['admin', 'namespaces', slug],
    queryFn: () => adminApi.getNamespace(slug),
    enabled: !!slug,
  })
}

export function useAdminNamespaceMembers(slug: string, page = 0, size = 20) {
  return useQuery<PagedResponse<NamespaceMember>>({
    queryKey: ['admin', 'namespaces', slug, 'members', { page, size }],
    queryFn: () => adminApi.getNamespaceMembers(slug, { page, size }),
    enabled: !!slug,
  })
}

export function useAdminNamespaceMemberCandidates(slug: string, search: string, enabled = true) {
  return useQuery<NamespaceCandidateUser[]>({
    queryKey: ['admin', 'namespaces', slug, 'member-candidates', search],
    queryFn: () => adminApi.searchNamespaceMemberCandidates(slug, search),
    enabled: enabled && !!slug && search.trim().length >= 2,
  })
}

function invalidateAdminNamespaceQueries(queryClient: ReturnType<typeof useQueryClient>, slug?: string) {
  queryClient.invalidateQueries({ queryKey: ['admin', 'namespaces'] })
  queryClient.invalidateQueries({ queryKey: ['namespaces', 'my'] })
  if (slug) {
    queryClient.invalidateQueries({ queryKey: ['admin', 'namespaces', slug] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'namespaces', slug, 'members'] })
    queryClient.invalidateQueries({ queryKey: ['namespaces', slug] })
    queryClient.invalidateQueries({ queryKey: ['namespaces', slug, 'members'] })
  }
}

export function useAddAdminNamespaceMember() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ slug, userId, role }: { slug: string; userId: string; role: NamespaceRole }) =>
      adminApi.addNamespaceMember(slug, { userId, role }),
    onSuccess: (_member, variables) => invalidateAdminNamespaceQueries(queryClient, variables.slug),
  })
}

export function useBatchAddAdminNamespaceMembers() {
  const queryClient = useQueryClient()
  return useMutation<BatchMemberResponse, Error, { slug: string; members: Array<{ userId: string; role: string }> }>({
    mutationFn: ({ slug, members }) => adminApi.batchAddNamespaceMembers(slug, members),
    onSuccess: (_result, variables) => invalidateAdminNamespaceQueries(queryClient, variables.slug),
  })
}

export function useUpdateAdminNamespaceMemberRole() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ slug, userId, role }: { slug: string; userId: string; role: NamespaceRole }) =>
      adminApi.updateNamespaceMemberRole(slug, userId, role),
    onSuccess: (_member, variables) => invalidateAdminNamespaceQueries(queryClient, variables.slug),
  })
}

export function useRemoveAdminNamespaceMember() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ slug, userId }: { slug: string; userId: string }) => adminApi.removeNamespaceMember(slug, userId),
    onSuccess: (_result, variables) => invalidateAdminNamespaceQueries(queryClient, variables.slug),
  })
}

export function useTransferAdminNamespaceOwnership() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ slug, newOwnerUserId }: { slug: string; newOwnerUserId: string }) =>
      adminApi.transferNamespaceOwnership(slug, newOwnerUserId),
    onSuccess: (_result, variables) => invalidateAdminNamespaceQueries(queryClient, variables.slug),
  })
}

export function useAdminNamespaceLifecycleAction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ slug, action, reason }: { slug: string; action: 'freeze' | 'unfreeze' | 'archive' | 'restore'; reason?: string }) => {
      if (action === 'freeze') {
        return adminApi.freezeNamespace(slug, reason)
      }
      if (action === 'unfreeze') {
        return adminApi.unfreezeNamespace(slug)
      }
      if (action === 'archive') {
        return adminApi.archiveNamespace(slug, reason)
      }
      return adminApi.restoreNamespace(slug)
    },
    onSuccess: (_namespace, variables) => invalidateAdminNamespaceQueries(queryClient, variables.slug),
  })
}
