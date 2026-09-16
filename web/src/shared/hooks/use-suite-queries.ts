import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  PagedResponse,
  MySkillSuiteSummary,
  ResourceSearchParams,
  ResourceSummary,
  SkillSuite,
  SkillSuiteDraftInput,
  SkillSuiteMemberCandidate,
  SkillSuiteVersion,
} from '@/api/types'
import { fetchJson, getCsrfHeaders, suiteApi, WEB_API_PREFIX } from '@/api/client'

function normalizeNamespace(namespace: string) {
  return namespace.replace(/^@/, '')
}

function buildResourceSearchUrl(params: ResourceSearchParams) {
  const query = new URLSearchParams()
  if (params.q) query.set('q', params.q)
  if (params.namespace) query.set('namespace', normalizeNamespace(params.namespace))
  if (params.resourceType) query.set('resourceType', params.resourceType)
  if (params.sort) query.set('sort', params.sort)
  query.set('page', String(params.page ?? 0))
  query.set('size', String(params.size ?? 20))
  return `${WEB_API_PREFIX}/resources?${query.toString()}`
}

export function useResourceSearch(params: ResourceSearchParams, enabled = true) {
  return useQuery({
    queryKey: ['resources', 'search', params],
    queryFn: () => fetchJson<PagedResponse<ResourceSummary>>(buildResourceSearchUrl(params)),
    placeholderData: keepPreviousData,
    enabled,
  })
}

export function useMySuites(query = '', page = 0, size = 20) {
  return useQuery({
    queryKey: ['suites', 'mine', query, page, size],
    queryFn: () => {
      const params = new URLSearchParams({ q: query, page: String(page), size: String(size) })
      return fetchJson<PagedResponse<MySkillSuiteSummary>>(`${WEB_API_PREFIX}/me/suites?${params.toString()}`)
    },
    placeholderData: keepPreviousData,
  })
}

export function useSuiteDetail(namespace: string, slug: string, version?: string, enabled = true) {
  return useQuery({
    queryKey: ['suites', namespace, slug, version],
    queryFn: () => {
      const suffix = version ? `?version=${encodeURIComponent(version)}` : ''
      return fetchJson<SkillSuite>(
        `${WEB_API_PREFIX}/suites/${normalizeNamespace(namespace)}/${encodeURIComponent(slug)}${suffix}`,
      )
    },
    enabled: enabled && !!namespace && !!slug,
  })
}

export function useSuiteVersions(namespace: string, slug: string, enabled = true) {
  return useQuery({
    queryKey: ['suites', namespace, slug, 'versions'],
    queryFn: () => fetchJson<SkillSuiteVersion[]>(
      `${WEB_API_PREFIX}/suites/${normalizeNamespace(namespace)}/${encodeURIComponent(slug)}/versions`,
    ),
    enabled: enabled && !!namespace && !!slug,
  })
}

export function useSuiteMemberCandidates(
  namespace: string,
  visibility: string,
  query: string,
  enabled = true,
) {
  return useQuery({
    queryKey: ['suites', 'member-candidates', namespace, visibility, query],
    queryFn: () => {
      const params = new URLSearchParams({
        suiteNamespace: normalizeNamespace(namespace),
        visibility,
        q: query,
        size: '50',
      })
      return fetchJson<SkillSuiteMemberCandidate[]>(
        `${WEB_API_PREFIX}/suites/member-candidates?${params.toString()}`,
      )
    },
    enabled: enabled && !!namespace && !!visibility,
  })
}

export function useCreateSuite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: SkillSuiteDraftInput) => fetchJson<SkillSuite>(`${WEB_API_PREFIX}/suites`, {
      method: 'POST',
      headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(input),
    }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['suites'] }),
  })
}

export function useCreateSuiteVersion(suiteId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: SkillSuiteDraftInput) => suiteApi.createVersion(suiteId, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['suites'] }),
  })
}

export function useUpdateSuiteDraft(suiteId: number, versionId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: SkillSuiteDraftInput) => fetchJson<SkillSuite>(
      `${WEB_API_PREFIX}/suites/${suiteId}/versions/${versionId}`,
      {
        method: 'PUT',
        headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(input),
      },
    ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['suites'] }),
  })
}

export function useSubmitSuite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ suiteId, versionId, privatePublish }: {
      suiteId: number
      versionId: number
      privatePublish: boolean
    }) => fetchJson<void>(
      `${WEB_API_PREFIX}/suites/${suiteId}/versions/${versionId}/${privatePublish ? 'publish' : 'submit'}`,
      { method: 'POST', headers: getCsrfHeaders() },
    ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['suites'] })
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
    },
  })
}

function useSuiteMutation<TInput>(mutationFn: (input: TInput) => Promise<void>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['suites'] })
      queryClient.invalidateQueries({ queryKey: ['resources'] })
    },
  })
}

export function useReopenSuiteVersion() {
  return useSuiteMutation(({ suiteId, versionId }: { suiteId: number; versionId: number }) =>
    suiteApi.reopen(suiteId, versionId))
}

export function useYankSuiteVersion() {
  return useSuiteMutation(({ suiteId, versionId, reason }: {
    suiteId: number
    versionId: number
    reason: string
  }) => suiteApi.yank(suiteId, versionId, reason))
}

export function useSetSuiteHidden() {
  return useSuiteMutation(({ suiteId, hidden }: { suiteId: number; hidden: boolean }) =>
    suiteApi.setHidden(suiteId, hidden))
}

export function useSetSuiteArchived() {
  return useSuiteMutation(({ suiteId, archived }: { suiteId: number; archived: boolean }) =>
    suiteApi.setArchived(suiteId, archived))
}

export function useDeleteSuite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ suiteId }: { suiteId: number }) => suiteApi.delete(suiteId),
    onSuccess: () => {
      // The detail page is still mounted while it redirects. Refetching the deleted Suite here
      // produces expected 404 noise, so only refresh the surfaces that can still show the entry.
      queryClient.invalidateQueries({ queryKey: ['suites', 'mine'] })
      queryClient.invalidateQueries({ queryKey: ['resources'] })
    },
  })
}
