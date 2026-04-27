import { describe, expect, it } from 'vitest'
import { buildSkillSearchUrl, shouldEnableNamespaceMemberCandidates } from './skill-query-helpers'

describe('buildSkillSearchUrl', () => {
  it('normalizes the query and strips the namespace prefix', () => {
    expect(buildSkillSearchUrl({
      q: '  hello world  ',
      namespace: '@team-ai',
      label: 'code-generation',
      sort: 'relevance',
      page: 2,
      size: 12,
    })).toBe('/api/web/skills?q=hello+world&namespace=team-ai&label=code-generation&sort=relevance&page=2&size=12')
  })

  it('returns the base skills endpoint when no search params are provided', () => {
    expect(buildSkillSearchUrl({})).toBe('/api/web/skills')
  })

  it('omits q when the search query is an empty string', () => {
    expect(buildSkillSearchUrl({ q: '' })).toBe('/api/web/skills')
  })

  it('omits q when the normalized query is empty after trimming', () => {
    expect(buildSkillSearchUrl({
      q: '   ',
      sort: 'relevance',
      page: 0,
    })).toBe('/api/web/skills?sort=relevance&page=0')
  })
})

describe('shouldEnableNamespaceMemberCandidates', () => {
  it('enables the query only when slug exists and search text has at least two non-space characters', () => {
    expect(shouldEnableNamespaceMemberCandidates('team-ai', 'ab')).toBe(true)
    expect(shouldEnableNamespaceMemberCandidates('team-ai', ' a ')).toBe(false)
    expect(shouldEnableNamespaceMemberCandidates('', 'admin')).toBe(false)
    expect(shouldEnableNamespaceMemberCandidates('team-ai', 'admin', false)).toBe(false)
  })
})
