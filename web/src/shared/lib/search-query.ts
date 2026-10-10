export const MAX_SEARCH_QUERY_LENGTH = 50
export const MAX_NAMESPACE_SLUG_LENGTH = 64
export const MAX_SEARCH_INPUT_LENGTH = MAX_NAMESPACE_SLUG_LENGTH + MAX_SEARCH_QUERY_LENGTH + 2

export function normalizeSearchQuery(query: string): string {
  return query.trim().slice(0, MAX_SEARCH_QUERY_LENGTH)
}

export interface NamespaceSearchInput {
  namespace: string
  query: string
}

const LEADING_NAMESPACE_PATTERN = /^@([a-zA-Z0-9][a-zA-Z0-9-]{0,63})(?:\s+|$)(.*)$/

export function parseNamespaceSearchInput(input: string): NamespaceSearchInput {
  const trimmed = input.trim()
  const match = trimmed.match(LEADING_NAMESPACE_PATTERN)
  if (!match) {
    return { namespace: '', query: normalizeSearchQuery(trimmed) }
  }

  return {
    namespace: match[1],
    query: normalizeSearchQuery(match[2] ?? ''),
  }
}

export function formatNamespaceSearchInput(namespace: string, query: string): string {
  const normalizedNamespace = namespace.trim().replace(/^@/, '')
  const normalizedQuery = normalizeSearchQuery(query)
  if (!normalizedNamespace) {
    return normalizedQuery
  }
  return normalizedQuery ? `@${normalizedNamespace} ${normalizedQuery}` : `@${normalizedNamespace}`
}
