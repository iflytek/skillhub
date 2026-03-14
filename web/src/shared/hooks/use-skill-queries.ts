import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { SkillSummary, SkillDetail, SkillVersion, SkillFile, SearchParams, PagedResponse, PublishResult, Namespace, NamespaceMember } from '@/api/types'
import { fetchJson, fetchText, getCsrfHeaders, meApi } from '@/api/client'

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

  return fetchJson<PagedResponse<SkillSummary>>(`/api/v1/skills?${queryParams.toString()}`)
}

async function getSkillDetail(namespace: string, slug: string): Promise<SkillDetail> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  return fetchJson<SkillDetail>(`/api/v1/skills/${cleanNamespace}/${slug}`)
}

async function getSkillVersions(namespace: string, slug: string): Promise<SkillVersion[]> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  const page = await fetchJson<PagedResponse<SkillVersion>>(`/api/v1/skills/${cleanNamespace}/${slug}/versions`)
  return page.items
}

async function getSkillFiles(namespace: string, slug: string, version: string): Promise<SkillFile[]> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  return fetchJson<SkillFile[]>(`/api/v1/skills/${cleanNamespace}/${slug}/versions/${version}/files`)
}

async function getSkillReadme(namespace: string, slug: string, version: string): Promise<string> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  try {
    return await fetchText(`/api/v1/skills/${cleanNamespace}/${slug}/versions/${version}/file?path=SKILL.md`)
  } catch {
    return ''
  }
}

async function getMySkills(): Promise<SkillSummary[]> {
  return fetchJson<SkillSummary[]>('/api/v1/me/skills')
}

async function getMyStars(): Promise<SkillSummary[]> {
  return meApi.getStars()
}

async function getMyNamespaces(): Promise<Namespace[]> {
  const page = await fetchJson<PagedResponse<Namespace>>('/api/v1/namespaces')
  return page.items
}

async function getNamespaceDetail(slug: string): Promise<Namespace> {
  const cleanSlug = slug.startsWith('@') ? slug.slice(1) : slug
  return fetchJson<Namespace>(`/api/v1/namespaces/${cleanSlug}`)
}

async function getNamespaceMembers(slug: string): Promise<NamespaceMember[]> {
  const cleanSlug = slug.startsWith('@') ? slug.slice(1) : slug
  const page = await fetchJson<PagedResponse<NamespaceMember>>(`/api/v1/namespaces/${cleanSlug}/members`)
  return page.items
}

async function publishSkill(params: { namespace: string; file: File; visibility: string }): Promise<PublishResult> {
  const cleanNamespace = params.namespace.startsWith('@') ? params.namespace.slice(1) : params.namespace
  const formData = new FormData()
  formData.append('file', params.file)
  formData.append('visibility', params.visibility)

  return fetchJson<PublishResult>(`/api/v1/skills/${cleanNamespace}/publish`, {
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

export function useMyStars() {
  return useQuery({
    queryKey: ['skills', 'stars'],
    queryFn: getMyStars,
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skills', 'my'] })
    },
  })
}
