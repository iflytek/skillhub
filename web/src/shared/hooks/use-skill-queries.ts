import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { SkillSummary, SkillDetail, SkillVersion, SkillFile, SearchParams, PagedResponse, PublishResult, Namespace, NamespaceMember } from '@/api/types'
import { fetchJson, fetchText, getCsrfHeaders, meApi, skillLifecycleApi, WEB_API_PREFIX } from '@/api/client'

const PUBLISH_REQUEST_TIMEOUT_MS = 60_000

async function searchSkills(params: SearchParams): Promise<PagedResponse<SkillSummary>> {
  const queryParams = new URLSearchParams()
  if (params.q) queryParams.append('q', params.q)
  if (params.namespace) {
    const cleanNamespace = params.namespace.startsWith('@') ? params.namespace.slice(1) : params.namespace
    queryParams.append('namespace', cleanNamespace)
  }
  if (params.sort) queryParams.append('sort', params.sort)
  if (params.page !== undefined) queryParams.append('page', String(params.page))
  if (params.size !== undefined) queryParams.append('size', String(params.size))

  return fetchJson<PagedResponse<SkillSummary>>(`${WEB_API_PREFIX}/skills?${queryParams.toString()}`)
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

async function getSkillReadme(namespace: string, slug: string, version: string): Promise<string> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  try {
    return await fetchText(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${slug}/versions/${version}/file?path=SKILL.md`)
  } catch {
    return ''
  }
}

async function getMySkills(): Promise<SkillSummary[]> {
  return fetchJson<SkillSummary[]>(`${WEB_API_PREFIX}/me/skills`)
}

async function getMyStars(): Promise<SkillSummary[]> {
  return meApi.getStars()
}

async function getMyNamespaces(): Promise<Namespace[]> {
  const page = await fetchJson<PagedResponse<Namespace>>(`${WEB_API_PREFIX}/namespaces`)
  return page.items
}

async function getNamespaceDetail(slug: string): Promise<Namespace> {
  const cleanSlug = slug.startsWith('@') ? slug.slice(1) : slug
  return fetchJson<Namespace>(`${WEB_API_PREFIX}/namespaces/${cleanSlug}`)
}

async function getNamespaceMembers(slug: string): Promise<NamespaceMember[]> {
  const cleanSlug = slug.startsWith('@') ? slug.slice(1) : slug
  const page = await fetchJson<PagedResponse<NamespaceMember>>(`${WEB_API_PREFIX}/namespaces/${cleanSlug}/members`)
  return page.items
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

// Hooks
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

export function useSkillReadme(namespace: string, slug: string, version?: string) {
  return useQuery({
    queryKey: ['skills', namespace, slug, 'versions', version, 'readme'],
    queryFn: () => getSkillReadme(namespace, slug, version!),
    enabled: !!namespace && !!slug && !!version,
  })
}

export function useMySkills() {
  return useQuery({
    queryKey: ['skills', 'my'],
    queryFn: getMySkills,
  })
}

export function useMyStars(enabled = true) {
  return useQuery({
    queryKey: ['skills', 'stars'],
    queryFn: getMyStars,
    enabled,
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

export function useNamespaceMembers(slug: string) {
  return useQuery({
    queryKey: ['namespaces', slug, 'members'],
    queryFn: () => getNamespaceMembers(slug),
    enabled: !!slug,
  })
}

export function usePublishSkill() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: publishSkill,
    meta: {
      skipGlobalErrorHandler: true,
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skills', 'my'] })
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
