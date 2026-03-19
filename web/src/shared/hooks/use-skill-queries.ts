import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { SkillSummary, SkillDetail, SkillVersion, SkillVersionDetail, SkillFile, SearchParams, PagedResponse, PublishResult, Namespace, NamespaceMember, ManagedNamespace, CreateNamespaceRequest, NamespaceCandidateUser, NamespaceRole } from '@/api/types'
import { fetchJson, fetchText, getCsrfHeaders, meApi, namespaceApi, promotionApi, skillLifecycleApi, WEB_API_PREFIX } from '@/api/client'
import { appendNamespaceMember, replaceNamespaceMemberRole } from '@/shared/lib/namespace-member-cache'
import { buildSkillSearchUrl, shouldEnableNamespaceMemberCandidates } from './skill-query-helpers'

/**
 * Shared TanStack Query hooks for skill, namespace, and related dashboard data.
 *
 * This file currently acts as a broad query gateway for several features, centralizing cache keys,
 * backend fetchers, and invalidation rules used throughout the app.
 */
const PUBLISH_REQUEST_TIMEOUT_MS = 60_000

async function searchSkills(params: SearchParams): Promise<PagedResponse<SkillSummary>> {
  return fetchJson<PagedResponse<SkillSummary>>(buildSkillSearchUrl(params))
}

async function getSkillDetail(namespace: string, slug: string): Promise<SkillDetail> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  return fetchJson<SkillDetail>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${slug}`)
}

async function getSkillVersions(namespace: string, slug: string): Promise<SkillVersion[]> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  const page = await fetchJson<PagedResponse<SkillVersion>>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${slug}/versions`)
  return page.items
}

async function getSkillFiles(namespace: string, slug: string, version: string): Promise<SkillFile[]> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  return fetchJson<SkillFile[]>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${slug}/versions/${version}/files`)
}

async function getSkillVersionDetail(namespace: string, slug: string, version: string): Promise<SkillVersionDetail> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  return fetchJson<SkillVersionDetail>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${slug}/versions/${version}`)
}

async function getSkillDocumentation(namespace: string, slug: string, version: string, path: string): Promise<string> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  return fetchText(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${slug}/versions/${version}/file?path=${encodeURIComponent(path)}`)
}

async function getMySkills(params: { page?: number; size?: number } = {}): Promise<PagedResponse<SkillSummary>> {
  return meApi.getSkills(params)
}

async function getMyStars(): Promise<SkillSummary[]> {
  return meApi.getStars()
}

async function getMyStarsPage(params: { page?: number; size?: number } = {}): Promise<PagedResponse<SkillSummary>> {
  return meApi.getStarsPage(params)
}

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

async function submitPromotion(params: { sourceSkillId: number; sourceVersionId: number }): Promise<void> {
  const globalNamespace = await namespaceApi.getDetail('global')
  await promotionApi.submit({
    sourceSkillId: params.sourceSkillId,
    sourceVersionId: params.sourceVersionId,
    targetNamespaceId: globalNamespace.id,
  })
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

async function publishSkill(params: { namespace: string; file: File; visibility: string }): Promise<PublishResult> {
  const cleanNamespace = params.namespace.startsWith('@') ? params.namespace.slice(1) : params.namespace
  const formData = new FormData()
  formData.append('file', params.file)
  formData.append('visibility', params.visibility)

  return fetchJson<PublishResult>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/publish`, {
    method: 'POST',
    headers: getCsrfHeaders(),
    body: formData,
    timeoutMs: PUBLISH_REQUEST_TIMEOUT_MS,
  })
}

// Query hooks stay close to the low-level fetchers so cache keys and invalidation rules remain
// consistent across pages and feature wrappers.
export function useSearchSkills(params: SearchParams) {
  return useQuery({
    queryKey: ['skills', 'search', params],
    queryFn: () => searchSkills(params),
    enabled: params.starredOnly !== true,
  })
}

export function useSkillDetail(namespace: string, slug: string) {
  return useQuery({
    queryKey: ['skills', namespace, slug],
    queryFn: () => getSkillDetail(namespace, slug),
    enabled: !!namespace && !!slug,
  })
}

export function useSkillVersions(namespace: string, slug: string) {
  return useQuery({
    queryKey: ['skills', namespace, slug, 'versions'],
    queryFn: () => getSkillVersions(namespace, slug),
    enabled: !!namespace && !!slug,
  })
}

export function useSkillFiles(namespace: string, slug: string, version?: string) {
  return useQuery({
    queryKey: ['skills', namespace, slug, 'versions', version, 'files'],
    queryFn: () => getSkillFiles(namespace, slug, version!),
    enabled: !!namespace && !!slug && !!version,
  })
}

export function useSkillReadme(namespace: string, slug: string, version?: string, path?: string | null) {
  return useQuery({
    queryKey: ['skills', namespace, slug, 'versions', version, 'readme', path],
    queryFn: () => getSkillDocumentation(namespace, slug, version!, path!),
    enabled: !!namespace && !!slug && !!version && !!path,
  })
}

export function useSkillVersionDetail(namespace: string, slug: string, version?: string) {
  return useQuery({
    queryKey: ['skills', namespace, slug, 'versions', version, 'detail'],
    queryFn: () => getSkillVersionDetail(namespace, slug, version!),
    enabled: !!namespace && !!slug && !!version,
  })
}

export function useMySkills(params: { page?: number; size?: number } = {}) {
  return useQuery({
    queryKey: ['skills', 'my', params],
    queryFn: () => getMySkills(params),
  })
}

export function useMyStars(enabled = true) {
  return useQuery({
    queryKey: ['skills', 'stars'],
    queryFn: getMyStars,
    enabled,
  })
}

export function useMyStarsPage(params: { page?: number; size?: number } = {}, enabled = true) {
  return useQuery({
    queryKey: ['skills', 'stars', 'page', params],
    queryFn: () => getMyStarsPage(params),
    enabled,
  })
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

export function useSubmitPromotion() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: submitPromotion,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
      queryClient.invalidateQueries({ queryKey: ['governance'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}

function invalidateNamespaceQueries(queryClient: ReturnType<typeof useQueryClient>, slug: string) {
  queryClient.invalidateQueries({ queryKey: ['namespaces', 'my'] })
  queryClient.invalidateQueries({ queryKey: ['namespaces', slug] })
  queryClient.invalidateQueries({ queryKey: ['namespaces', slug, 'members'] })
  queryClient.invalidateQueries({ queryKey: ['reviews'] })
}

export function usePublishSkill() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: publishSkill,
    meta: {
      skipGlobalErrorHandler: true,
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
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

export function useArchiveSkill() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ namespace, slug, reason }: { namespace: string; slug: string; reason?: string }) =>
      skillLifecycleApi.archiveSkill(namespace, slug, reason),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['skills', 'my'] })
      queryClient.invalidateQueries({ queryKey: ['skills', variables.namespace, variables.slug] })
      queryClient.invalidateQueries({ queryKey: ['skills', variables.namespace, variables.slug, 'versions'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}

export function useUnarchiveSkill() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ namespace, slug }: { namespace: string; slug: string }) =>
      skillLifecycleApi.unarchiveSkill(namespace, slug),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['skills', 'my'] })
      queryClient.invalidateQueries({ queryKey: ['skills', variables.namespace, variables.slug] })
      queryClient.invalidateQueries({ queryKey: ['skills', variables.namespace, variables.slug, 'versions'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}

export function useDeleteSkillVersion() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ namespace, slug, version }: { namespace: string; slug: string; version: string }) =>
      skillLifecycleApi.deleteVersion(namespace, slug, version),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['skills', 'my'] })
      queryClient.invalidateQueries({ queryKey: ['skills', variables.namespace, variables.slug] })
      queryClient.invalidateQueries({ queryKey: ['skills', variables.namespace, variables.slug, 'versions'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}

export function useWithdrawSkillReview() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ namespace, slug, version }: { namespace: string; slug: string; version: string }) =>
      skillLifecycleApi.withdrawReview(namespace, slug, version),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['skills', 'my'] })
      queryClient.invalidateQueries({ queryKey: ['skills', variables.namespace, variables.slug] })
      queryClient.invalidateQueries({ queryKey: ['skills', variables.namespace, variables.slug, 'versions'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}

export function useRereleaseSkillVersion() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ namespace, slug, version, targetVersion }: { namespace: string; slug: string; version: string; targetVersion: string }) =>
      skillLifecycleApi.rereleaseVersion(namespace, slug, version, targetVersion),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['skills', 'my'] })
      queryClient.invalidateQueries({ queryKey: ['skills', variables.namespace, variables.slug] })
      queryClient.invalidateQueries({ queryKey: ['skills', variables.namespace, variables.slug, 'versions'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
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
