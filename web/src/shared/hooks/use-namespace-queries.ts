import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { Namespace, NamespaceMember, ManagedNamespace, CreateNamespaceRequest, NamespaceCandidateUser, NamespaceRole } from '@/api/types'
import { namespaceApi } from '@/api/client'
import { appendNamespaceMember, replaceNamespaceMemberRole } from '@/shared/lib/namespace-member-cache'
import { shouldEnableNamespaceMemberCandidates } from './skill-query-helpers'

async function getMyNamespaces(): Promise<ManagedNamespace[]> {
  return namespaceApi.listMine()
}

async function createNamespace(request: CreateNamespaceRequest): Promise<Namespace> {
  return namespaceApi.create(request)
}

async function getNamespaceDetail(slug: string): Promise<Namespace> {
  return namespaceApi.getDetail(slug)
}

async function getNamespaceMembers(slug: string): Promise<NamespaceMember[]> {
  return namespaceApi.listMembers(slug)
}

async function searchNamespaceMemberCandidates(params: { slug: string; search: string }): Promise<NamespaceCandidateUser[]> {
  return namespaceApi.searchMemberCandidates(params.slug, params.search)
}

async function addNamespaceMember(params: { slug: string; userId: string; role: NamespaceRole }): Promise<NamespaceMember> {
  return namespaceApi.addMember(params.slug, { userId: params.userId, role: params.role })
}

async function updateNamespaceMemberRole(params: { slug: string; userId: string; role: NamespaceRole }): Promise<NamespaceMember> {
  return namespaceApi.updateMemberRole(params.slug, params.userId, params.role)
}

async function removeNamespaceMember(params: { slug: string; userId: string }): Promise<void> {
  return namespaceApi.removeMember(params.slug, params.userId)
}

function invalidateNamespaceQueries(queryClient: ReturnType<typeof useQueryClient>, slug: string) {
  queryClient.invalidateQueries({ queryKey: ['namespaces', 'my'] })
  queryClient.invalidateQueries({ queryKey: ['namespaces', slug] })
  queryClient.invalidateQueries({ queryKey: ['namespaces', slug, 'members'] })
  queryClient.invalidateQueries({ queryKey: ['reviews'] })
}

export function useMyNamespaces() {
  return useQuery({
    queryKey: ['namespaces', 'my'],
    queryFn: getMyNamespaces,
  })
}

export function useCreateNamespace() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: createNamespace,
    onSuccess: (namespace) => {
      queryClient.invalidateQueries({ queryKey: ['namespaces', 'my'] })
      queryClient.invalidateQueries({ queryKey: ['namespaces', namespace.slug] })
      queryClient.invalidateQueries({ queryKey: ['namespaces'] })
    },
  })
}

export function useNamespaceDetail(slug: string) {
  return useQuery({
    queryKey: ['namespaces', slug],
    queryFn: () => getNamespaceDetail(slug),
    enabled: !!slug,
  })
}

export function useNamespaceMembers(slug: string) {
  return useQuery({
    queryKey: ['namespaces', slug, 'members'],
    queryFn: () => getNamespaceMembers(slug),
    enabled: !!slug,
  })
}

export function useNamespaceMemberCandidates(slug: string, search: string, enabled = true) {
  return useQuery({
    queryKey: ['namespaces', slug, 'member-candidates', search],
    queryFn: () => searchNamespaceMemberCandidates({ slug, search }),
    enabled: shouldEnableNamespaceMemberCandidates(slug, search, enabled),
  })
}

export function useAddNamespaceMember() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: addNamespaceMember,
    onSuccess: (member, variables) => {
      queryClient.setQueryData<NamespaceMember[]>(
        ['namespaces', variables.slug, 'members'],
        (currentMembers) => appendNamespaceMember(currentMembers, member),
      )
      invalidateNamespaceQueries(queryClient, variables.slug)
    },
  })
}

export function useUpdateNamespaceMemberRole() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: updateNamespaceMemberRole,
    onSuccess: (member, variables) => {
      queryClient.setQueryData<NamespaceMember[]>(
        ['namespaces', variables.slug, 'members'],
        (currentMembers) => replaceNamespaceMemberRole(currentMembers, variables.userId, member.role),
      )
      invalidateNamespaceQueries(queryClient, variables.slug)
    },
  })
}

export function useRemoveNamespaceMember() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: removeNamespaceMember,
    onSuccess: (_data, variables) => {
      invalidateNamespaceQueries(queryClient, variables.slug)
    },
  })
}

export function useFreezeNamespace() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ slug }: { slug: string }) => namespaceApi.freeze(slug),
    onSuccess: (_data, variables) => {
      invalidateNamespaceQueries(queryClient, variables.slug)
    },
  })
}

export function useUnfreezeNamespace() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ slug }: { slug: string }) => namespaceApi.unfreeze(slug),
    onSuccess: (_data, variables) => {
      invalidateNamespaceQueries(queryClient, variables.slug)
    },
  })
}

export function useArchiveNamespace() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ slug, reason }: { slug: string; reason?: string }) => namespaceApi.archive(slug, reason),
    onSuccess: (_data, variables) => {
      invalidateNamespaceQueries(queryClient, variables.slug)
    },
  })
}

export function useRestoreNamespace() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ slug }: { slug: string }) => namespaceApi.restore(slug),
    onSuccess: (_data, variables) => {
      invalidateNamespaceQueries(queryClient, variables.slug)
    },
  })
}
