import { describe, expect, it } from 'vitest'
import { getSkillSquareSearch } from './skill-navigation'

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
