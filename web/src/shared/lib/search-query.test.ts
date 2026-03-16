import { describe, expect, it } from 'vitest'
import { MAX_SEARCH_QUERY_LENGTH, normalizeSearchQuery } from './search-query'

describe('normalizeSearchQuery', () => {
  it('trims whitespace around the query', () => {
    expect(normalizeSearchQuery('  hello world  ')).toBe('hello world')
  })

  it('limits the query to fifty characters', () => {
    const query = 'a'.repeat(MAX_SEARCH_QUERY_LENGTH + 12)

    expect(normalizeSearchQuery(query)).toHaveLength(MAX_SEARCH_QUERY_LENGTH)
    expect(normalizeSearchQuery(query)).toBe('a'.repeat(MAX_SEARCH_QUERY_LENGTH))
  })
})
