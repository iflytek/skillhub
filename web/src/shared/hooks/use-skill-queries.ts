import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { SkillSummary, SkillDetail, SkillVersion, SkillFile, SearchParams, PagedResponse, PublishResult, Namespace, NamespaceMember } from '@/api/types'

// Mock API functions - replace with actual API calls
async function searchSkills(params: SearchParams): Promise<PagedResponse<SkillSummary>> {
  // TODO: Replace with actual API call
  console.log('searchSkills', params)
  return {
    items: [],
    total: 0,
    page: params.page || 1,
    size: params.size || 10,
  }
}

async function getSkillDetail(namespace: string, slug: string): Promise<SkillDetail> {
  // TODO: Replace with actual API call
  console.log('getSkillDetail', namespace, slug)
  throw new Error('Not implemented')
}

async function getSkillVersions(skillId: number): Promise<SkillVersion[]> {
  // TODO: Replace with actual API call
  console.log('getSkillVersions', skillId)
  return []
}

async function getSkillFiles(versionId: number): Promise<SkillFile[]> {
  // TODO: Replace with actual API call
  console.log('getSkillFiles', versionId)
  return []
}

async function getSkillReadme(versionId: number): Promise<string> {
  // TODO: Replace with actual API call
  console.log('getSkillReadme', versionId)
  return '# README\n\nNo content available.'
}

async function getMySkills(): Promise<SkillSummary[]> {
  // TODO: Replace with actual API call
  console.log('getMySkills')
  return []
}

async function getMyNamespaces(): Promise<Namespace[]> {
  // TODO: Replace with actual API call
  console.log('getMyNamespaces')
  return []
}

async function getNamespaceDetail(slug: string): Promise<Namespace> {
  // TODO: Replace with actual API call
  console.log('getNamespaceDetail', slug)
  throw new Error('Not implemented')
}

async function getNamespaceMembers(namespaceId: number): Promise<NamespaceMember[]> {
  // TODO: Replace with actual API call
  console.log('getNamespaceMembers', namespaceId)
  return []
}

async function publishSkill(data: FormData): Promise<PublishResult> {
  // TODO: Replace with actual API call
  console.log('publishSkill', data)
  throw new Error('Not implemented')
}

// Hooks
export function useSearchSkills(params: SearchParams) {
  return useQuery({
    queryKey: ['skills', 'search', params],
    queryFn: () => searchSkills(params),
  })
}

export function useSkillDetail(namespace: string, slug: string) {
  return useQuery({
    queryKey: ['skills', namespace, slug],
    queryFn: () => getSkillDetail(namespace, slug),
    enabled: !!namespace && !!slug,
  })
}

export function useSkillVersions(skillId: number) {
  return useQuery({
    queryKey: ['skills', skillId, 'versions'],
    queryFn: () => getSkillVersions(skillId),
    enabled: !!skillId,
  })
}

export function useSkillFiles(versionId: number) {
  return useQuery({
    queryKey: ['skills', 'versions', versionId, 'files'],
    queryFn: () => getSkillFiles(versionId),
    enabled: !!versionId,
  })
}

export function useSkillReadme(versionId: number) {
  return useQuery({
    queryKey: ['skills', 'versions', versionId, 'readme'],
    queryFn: () => getSkillReadme(versionId),
    enabled: !!versionId,
  })
}

export function useMySkills() {
  return useQuery({
    queryKey: ['skills', 'my'],
    queryFn: getMySkills,
  })
}

export function useMyNamespaces() {
  return useQuery({
    queryKey: ['namespaces', 'my'],
    queryFn: getMyNamespaces,
  })
}

export function useNamespaceDetail(slug: string) {
  return useQuery({
    queryKey: ['namespaces', slug],
    queryFn: () => getNamespaceDetail(slug),
    enabled: !!slug,
  })
}

export function useNamespaceMembers(namespaceId: number) {
  return useQuery({
    queryKey: ['namespaces', namespaceId, 'members'],
    queryFn: () => getNamespaceMembers(namespaceId),
    enabled: !!namespaceId,
  })
}

export function usePublishSkill() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: publishSkill,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skills', 'my'] })
    },
  })
}
