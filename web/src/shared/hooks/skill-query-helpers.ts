import type { SearchParams } from '@/api/types'
import { WEB_API_PREFIX } from '@/api/client'
import { normalizeSearchQuery } from '@/shared/lib/search-query'

function normalizePage(page: number | undefined) {
  if (page === undefined || !Number.isFinite(page)) {
    return undefined
  }
  return Math.min(Math.max(Math.trunc(page), 0), 10000)
}

export function buildSkillSearchUrl(params: SearchParams) {
  const queryParams = new URLSearchParams()
  const normalizedQuery = normalizeSearchQuery(params.q ?? '')

  if (params.q !== undefined) {
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

  const normalizedPage = normalizePage(params.page)
  if (normalizedPage !== undefined) {
    queryParams.append('page', String(normalizedPage))
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
