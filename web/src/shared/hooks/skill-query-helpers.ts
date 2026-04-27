import type { SearchParams } from '@/api/types'
import { WEB_API_PREFIX } from '@/api/client'
import { normalizeSearchQuery } from '@/shared/lib/search-query'

export function buildSkillSearchUrl(params: SearchParams) {
  const queryParams = new URLSearchParams()
  const normalizedQuery = normalizeSearchQuery(params.q ?? '')

  if (normalizedQuery) {
    queryParams.append('q', normalizedQuery)
  }

  if (params.namespace) {
    const cleanNamespace = params.namespace.startsWith('@') ? params.namespace.slice(1) : params.namespace
    queryParams.append('namespace', cleanNamespace)
  }

  if (params.label) {
    queryParams.append('label', params.label)
  }

  if (params.sort) {
    queryParams.append('sort', params.sort)
  }

  if (params.page !== undefined) {
    queryParams.append('page', String(params.page))
  }

  if (params.size !== undefined) {
    queryParams.append('size', String(params.size))
  }

  const queryString = queryParams.toString()
  return queryString ? `${WEB_API_PREFIX}/skills?${queryString}` : `${WEB_API_PREFIX}/skills`
}

export function shouldEnableNamespaceMemberCandidates(slug: string, search: string, enabled = true) {
  return enabled && !!slug && search.trim().length >= 2
}
