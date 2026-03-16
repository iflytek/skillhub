export const MAX_SEARCH_QUERY_LENGTH = 50

export function normalizeSearchQuery(query: string): string {
  return query.trim().slice(0, MAX_SEARCH_QUERY_LENGTH)
}
