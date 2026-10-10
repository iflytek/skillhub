import { describe, expect, it } from 'vitest'
import { getSkillSquareSearch, getSkillLabelSearch, normalizeSkillDetailReturnTo } from './skill-navigation'

describe('getSkillSquareSearch', () => {
  it('returns the default search params for the skill square', () => {
    expect(getSkillSquareSearch()).toEqual({
      q: '',
      sort: 'relevance',
      page: 0,
      starredOnly: false,
    })
  })
})

describe('getSkillLabelSearch', () => {
  it('returns search params that filter by the given label', () => {
    expect(getSkillLabelSearch('code-generation')).toEqual({
      q: '',
      label: 'code-generation',
      sort: 'newest',
      page: 0,
      starredOnly: false,
    })
  })
})

describe('normalizeSkillDetailReturnTo', () => {
  it('returns the provided dashboard route when coming from my skills', () => {
    expect(normalizeSkillDetailReturnTo('/dashboard/skills')).toBe('/dashboard/skills')
  })

  it('drops invalid return targets', () => {
    expect(normalizeSkillDetailReturnTo('https://example.com/elsewhere')).toBeUndefined()
  })
})
