import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { SkillSummary, SkillDetail, SkillVersion, SkillVersionDetail, SkillFile, SearchParams, PagedResponse, PublishResult } from '@/api/types'
import { fetchJson, fetchText, getCsrfHeaders, skillLifecycleApi, WEB_API_PREFIX } from '@/api/client'
import { clearDeletedSkillQueries } from '@/features/skill/skill-delete-flow'
import { getSkillDetailQueryKey } from './query-keys'
import { buildSkillSearchUrl } from './skill-query-helpers'

const PUBLISH_REQUEST_TIMEOUT_MS = 60_000

async function searchSkills(params: SearchParams): Promise<PagedResponse<SkillSummary>> {
  return fetchJson<PagedResponse<SkillSummary>>(buildSkillSearchUrl(params))
}

async function getSkillDetail(namespace: string, slug: string): Promise<SkillDetail> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  return fetchJson<SkillDetail>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}`)
}

async function getSkillVersions(namespace: string, slug: string): Promise<SkillVersion[]> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  const page = await fetchJson<PagedResponse<SkillVersion>>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/versions`)
  return page.items
}

async function getSkillFiles(namespace: string, slug: string, version: string): Promise<SkillFile[]> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  return fetchJson<SkillFile[]>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}/files`)
}

async function getSkillVersionDetail(namespace: string, slug: string, version: string): Promise<SkillVersionDetail> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  return fetchJson<SkillVersionDetail>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}`)
}

async function getSkillDocumentation(namespace: string, slug: string, version: string, path: string): Promise<string> {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
  return fetchText(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}/file?path=${encodeURIComponent(path)}`)
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

export function useSearchSkills(params: SearchParams) {
  return useQuery({
    queryKey: ['skills', 'search', params],
    queryFn: () => searchSkills(params),
    enabled: params.starredOnly !== true && Boolean(params.q || params.label),
  })
}

export function useSkillDetail(namespace: string, slug: string, enabled = true) {
  return useQuery({
    queryKey: getSkillDetailQueryKey(namespace, slug),
    queryFn: () => getSkillDetail(namespace, slug),
    enabled: enabled && !!namespace && !!slug,
    refetchOnMount: 'always',
  })
}

export function useSkillVersions(namespace: string, slug: string, enabled = true) {
  return useQuery({
    queryKey: ['skills', namespace, slug, 'versions'],
    queryFn: () => getSkillVersions(namespace, slug),
    enabled: enabled && !!namespace && !!slug,
  })
}

export function useSkillFiles(namespace: string, slug: string, version?: string, enabled = true) {
  return useQuery({
    queryKey: ['skills', namespace, slug, 'versions', version, 'files'],
    queryFn: () => getSkillFiles(namespace, slug, version!),
    enabled: enabled && !!namespace && !!slug && !!version,
  })
}

export function useSkillReadme(namespace: string, slug: string, version?: string, path?: string | null, enabled = true) {
  return useQuery({
    queryKey: ['skills', namespace, slug, 'versions', version, 'readme', path],
    queryFn: () => getSkillDocumentation(namespace, slug, version!, path!),
    enabled: enabled && !!namespace && !!slug && !!version && !!path,
  })
}

export function useSkillFile(
  namespace: string,
  slug: string,
  version: string | undefined,
  filePath: string | null,
  enabled: boolean = true
) {
  return useQuery({
    queryKey: ['skills', namespace, slug, 'versions', version, 'file', filePath],
    queryFn: () => getSkillDocumentation(namespace, slug, version!, filePath!),
    enabled: enabled && !!namespace && !!slug && !!version && !!filePath,
    staleTime: 5 * 60 * 1000, // Cache for 5 minutes
  })
}

export function useSkillVersionDetail(namespace: string, slug: string, version?: string, enabled = true) {
  return useQuery({
    queryKey: ['skills', namespace, slug, 'versions', version, 'detail'],
    queryFn: () => getSkillVersionDetail(namespace, slug, version!),
    enabled: enabled && !!namespace && !!slug && !!version,
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
      queryClient.invalidateQueries({ queryKey: ['skills'] })
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

export function useDeleteSkill() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ namespace, slug, ownerId }: { namespace: string; slug: string; ownerId?: string }) =>
      skillLifecycleApi.deleteSkill(namespace, slug, ownerId),
    onSuccess: (data, variables) => {
      clearDeletedSkillQueries(queryClient, variables.namespace, variables.slug, data.skillId)
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
