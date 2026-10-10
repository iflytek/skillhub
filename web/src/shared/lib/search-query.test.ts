import { describe, expect, it } from 'vitest'
import { MAX_SEARCH_QUERY_LENGTH, normalizeSearchQuery, parseNamespaceSearchInput } from './search-query'

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

describe('parseNamespaceSearchInput', () => {
  it('extracts a leading namespace token and keeps the remaining query', () => {
    expect(parseNamespaceSearchInput('@team-ai release notes')).toEqual({
      namespace: 'team-ai',
      query: 'release notes',
    })
  })

  it('treats a bare namespace token as a namespace-only search', () => {
    expect(parseNamespaceSearchInput('@product')).toEqual({
      namespace: 'product',
      query: '',
    })
  })

  it('extracts a sixty-four character namespace before limiting the keyword', () => {
    const namespace = 'a'.repeat(64)
    const query = 'release-notes '.repeat(8)

    expect(parseNamespaceSearchInput(`@${namespace} ${query}`)).toEqual({
      namespace,
      query: query.trim().slice(0, MAX_SEARCH_QUERY_LENGTH),
    })
  })

  it('leaves ordinary search text unchanged', () => {
    expect(parseNamespaceSearchInput('meeting assistant')).toEqual({
      namespace: '',
      query: 'meeting assistant',
    })
  })
})
